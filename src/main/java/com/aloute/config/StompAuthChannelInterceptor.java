package com.aloute.config;

import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.principal.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> ALLOWED_SUBSCRIBE_DESTINATIONS = Set.of(
            "/user/queue/messages",
            "/user/queue/typing",
            "/user/queue/read-receipts",
            "/user/queue/notifications"
    );

    private final JwtSessionService jwtSessionService;
    private final WebSocketSessionRegistry sessionRegistry;

    private final ConcurrentHashMap<String, String> sessionTokenMap = new ConcurrentHashMap<>();

    @Autowired
    public StompAuthChannelInterceptor(JwtSessionService jwtSessionService,
                                       @Autowired(required = false) WebSocketSessionRegistry sessionRegistry) {
        this.jwtSessionService = Objects.requireNonNull(jwtSessionService, "jwtSessionService must not be null");
        this.sessionRegistry = sessionRegistry;
    }

    public StompAuthChannelInterceptor(JwtSessionService jwtSessionService) {
        this(jwtSessionService, null);
    }

    public static class StompPrincipalAuthenticationToken extends UsernamePasswordAuthenticationToken {
        private final String name;

        public StompPrincipalAuthenticationToken(UserPrincipal principal,
                                                Collection<? extends GrantedAuthority> authorities) {
            super(principal, null, authorities);
            this.name = String.valueOf(principal.getId());
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    public void closeSession(String sessionId) {
        if (sessionId == null) return;
        sessionTokenMap.remove(sessionId);
        if (sessionRegistry != null) {
            sessionRegistry.closeSession(sessionId);
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !accessor.isMutable()) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        // 1. Inbound CONNECT frame authentication
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;

            // 1a. Authorization header: Bearer <token>
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String header = authHeaders.get(0);
                if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = header.substring(7).trim();
                }
            }

            // 1b. Fallback to aloute_access_token native header if present
            if (token == null) {
                List<String> cookieHeaders = accessor.getNativeHeader("aloute_access_token");
                if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                    token = cookieHeaders.get(0).trim();
                }
            }

            // 1c. Fallback to session attributes captured during HTTP handshake (HttpOnly cookie)
            if (token == null && accessor.getSessionAttributes() != null) {
                Object sessionToken = accessor.getSessionAttributes().get("aloute_access_token");
                if (sessionToken == null) {
                    sessionToken = accessor.getSessionAttributes().get("token");
                }
                if (sessionToken != null) {
                    token = sessionToken.toString().trim();
                }
            }

            if (token == null || token.isBlank()) {
                log.warn("Unauthenticated STOMP connection rejected: missing token");
                accessor.setUser(null);
                closeSession(accessor.getSessionId());
                throw new IllegalArgumentException("Unauthenticated STOMP connection: missing token");
            }

            try {
                UserPrincipal principal = jwtSessionService.authenticate(token);
                StompPrincipalAuthenticationToken auth =
                        new StompPrincipalAuthenticationToken(principal, principal.getAuthorities());
                accessor.setUser(null);
                accessor.setUser(auth);
                if (accessor.getSessionId() != null) {
                    sessionTokenMap.put(accessor.getSessionId(), token);
                }
                if (accessor.getSessionAttributes() != null) {
                    try {
                        accessor.getSessionAttributes().put("token", token);
                        accessor.getSessionAttributes().put("aloute_access_token", token);
                        accessor.getSessionAttributes().put("authVersion", principal.getAuthVersion());
                    } catch (UnsupportedOperationException ex) {
                        Map<String, Object> mutableMap = new HashMap<>(accessor.getSessionAttributes());
                        mutableMap.put("token", token);
                        mutableMap.put("aloute_access_token", token);
                        mutableMap.put("authVersion", principal.getAuthVersion());
                        accessor.setSessionAttributes(mutableMap);
                    }
                }
                log.info("STOMP session connected for userId: {}", principal.getId());
            } catch (Exception ex) {
                log.warn("STOMP connect failed token validation: {}", ex.getMessage());
                accessor.setUser(null);
                closeSession(accessor.getSessionId());
                throw new IllegalArgumentException("Invalid STOMP credentials: " + ex.getMessage(), ex);
            }
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }

        // Inbound DISCONNECT frame cleanup
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            if (accessor.getSessionId() != null) {
                sessionTokenMap.remove(accessor.getSessionId());
            }
            return message;
        }

        // 2. Inbound SUBSCRIBE frame validation
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            Long userId = extractUserId(user);
            authorizeSubscription(destination, userId);

            // Re-verify session validity on every privileged frame
            verifyActiveSession(accessor);
            return message;
        }

        // 3. Inbound SEND frame validation
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            verifyActiveSession(accessor);
            return message;
        }

        // 4. Outbound private message delivery check
        if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
            String sessionId = accessor.getSessionId();
            String dest = accessor.getDestination();

            if (sessionId == null && dest != null) {
                int userIdx = dest.lastIndexOf("-user");
                if (userIdx >= 0) {
                    sessionId = dest.substring(userIdx + 5);
                }
            }

            boolean isPrivateDelivery = (sessionId != null) || (dest != null && (dest.startsWith("/user/") || dest.contains("-user")));
            if (isPrivateDelivery) {
                String token = null;
                if (sessionId != null) {
                    token = sessionTokenMap.get(sessionId);
                }
                if (token == null && accessor.getSessionAttributes() != null) {
                    Object tokenObj = accessor.getSessionAttributes().get("token");
                    if (tokenObj == null) {
                        tokenObj = accessor.getSessionAttributes().get("aloute_access_token");
                    }
                    if (tokenObj != null) {
                        token = tokenObj.toString();
                        if (sessionId != null) {
                            sessionTokenMap.put(sessionId, token);
                        }
                    }
                }

                if (token == null || token.isBlank()) {
                    log.warn("Outbound private delivery suppressed: no credentials bound for session {}", sessionId);
                    closeSession(sessionId);
                    return null; // Fail-closed: suppress delivery
                }

                try {
                    jwtSessionService.authenticate(token);
                } catch (Exception ex) {
                    log.warn("Outbound private delivery suppressed and session closed for revoked/expired token: {}", ex.getMessage());
                    closeSession(sessionId);
                    return null; // Suppresses delivery to this revoked client connection
                }
            }
        }

        return message;
    }

    private void verifyActiveSession(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String token = null;
        if (sessionId != null) {
            token = sessionTokenMap.get(sessionId);
        }
        if (token == null && accessor.getSessionAttributes() != null) {
            Object tokenObj = accessor.getSessionAttributes().get("token");
            if (tokenObj == null) {
                tokenObj = accessor.getSessionAttributes().get("aloute_access_token");
            }
            if (tokenObj != null) {
                token = tokenObj.toString();
                if (sessionId != null) {
                    sessionTokenMap.put(sessionId, token);
                }
            }
        }

        if (token == null || token.isBlank()) {
            log.warn("STOMP frame rejected for unauthenticated session: {}", sessionId);
            accessor.setUser(null);
            closeSession(sessionId);
            throw new IllegalArgumentException("Session not authenticated: missing token");
        }

        try {
            UserPrincipal principal = jwtSessionService.authenticate(token);
            StompPrincipalAuthenticationToken auth =
                    new StompPrincipalAuthenticationToken(principal, principal.getAuthorities());
            accessor.setUser(auth);
        } catch (Exception ex) {
            log.warn("STOMP frame rejected and session closed for revoked or expired token: {}", ex.getMessage());
            accessor.setUser(null);
            closeSession(sessionId);
            throw new IllegalArgumentException("Session revoked or expired: " + ex.getMessage(), ex);
        }
    }

    private void authorizeSubscription(String destination, Long userId) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Subscription destination cannot be empty");
        }

        if (userId == null) {
            log.warn("Unauthenticated STOMP subscription rejected for destination: {}", destination);
            throw new IllegalArgumentException("Unauthenticated STOMP subscription rejected for destination: " + destination);
        }

        if (!ALLOWED_SUBSCRIBE_DESTINATIONS.contains(destination)) {
            log.warn("Unauthorized STOMP subscription destination rejected for user {}: {}", userId, destination);
            throw new IllegalArgumentException("Unauthorized STOMP subscription destination: " + destination);
        }

        log.debug("Authorized STOMP subscription to {} for userId: {}", destination, userId);
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof UserPrincipal userPrincipal && userPrincipal.getId() != null) {
                return userPrincipal.getId();
            }
        }
        return null;
    }
}
