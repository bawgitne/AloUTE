package com.aloute.config;

import com.aloute.repository.ConversationMemberRepository;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.CustomUserDetailsService;
import com.aloute.security.principal.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_DESTINATION_PATTERN =
            Pattern.compile("^/(?:topic|queue|user/queue)/(?:conversation|conversations|chat)/([0-9]+)(?:/.*)?$");

    private static final Pattern TARGET_USER_DESTINATION_PATTERN =
            Pattern.compile("^/user/([0-9]+)(?:/.*)?$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ConversationMemberRepository conversationMemberRepository;

    @Autowired
    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                       CustomUserDetailsService userDetailsService,
                                       ConversationMemberRepository conversationMemberRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                       CustomUserDetailsService userDetailsService) {
        this(jwtTokenProvider, userDetailsService, null);
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

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !accessor.isMutable()) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;

            // 1. Try Authorization header: Bearer <token>
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String header = authHeaders.get(0);
                if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = header.substring(7).trim();
                }
            }

            // 2. Fallback to aloute_access_token native header if present
            if (token == null) {
                List<String> cookieHeaders = accessor.getNativeHeader("aloute_access_token");
                if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                    token = cookieHeaders.get(0).trim();
                }
            }

            // 3. Fallback to session attributes captured during HTTP handshake (HttpOnly cookie)
            if (token == null && accessor.getSessionAttributes() != null) {
                Object sessionToken = accessor.getSessionAttributes().get("aloute_access_token");
                if (sessionToken != null) {
                    token = sessionToken.toString().trim();
                }
            }

            if (token != null && !token.isBlank()) {
                try {
                    Long userId = jwtTokenProvider.parseUserId(token);
                    UserPrincipal principal = userDetailsService.loadById(userId);
                    if (principal != null && principal.isEnabled()) {
                        StompPrincipalAuthenticationToken auth =
                                new StompPrincipalAuthenticationToken(principal, principal.getAuthorities());
                        accessor.setUser(null);
                        accessor.setUser(auth);
                        log.info("STOMP session connected for userId: {}", userId);
                    } else {
                        accessor.setUser(null);
                        throw new IllegalArgumentException("User account is disabled or not found");
                    }
                } catch (Exception ex) {
                    log.warn("STOMP connect failed token validation: {}", ex.getMessage());
                    accessor.setUser(null);
                    throw new IllegalArgumentException("Invalid STOMP credentials: " + ex.getMessage(), ex);
                }
            } else {
                // If explicit CONNECT token absent, check already-authenticated HTTP handshake principal
                Principal existingUser = accessor.getUser();
                if (existingUser instanceof Authentication auth && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
                    if (userPrincipal.isEnabled()) {
                        StompPrincipalAuthenticationToken stompAuth =
                                new StompPrincipalAuthenticationToken(userPrincipal, userPrincipal.getAuthorities());
                        accessor.setUser(null);
                        accessor.setUser(stompAuth);
                        log.info("STOMP session connected via handshake principal for userId: {}", userPrincipal.getId());
                    } else {
                        accessor.setUser(null);
                        throw new IllegalArgumentException("User account is disabled");
                    }
                } else {
                    // Unauthenticated CONNECT or non-UserPrincipal handshake
                    accessor.setUser(null);
                    throw new IllegalArgumentException("Unauthenticated STOMP connection rejected");
                }
            }
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            Long userId = extractUserId(user);
            authorizeSubscription(destination, userId);
            return message;
        }

        return message;
    }

    private void authorizeSubscription(String destination, Long userId) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Subscription destination cannot be empty");
        }

        // 1. Conversation-specific destinations require authenticated membership
        Matcher convMatcher = CONVERSATION_DESTINATION_PATTERN.matcher(destination);
        if (convMatcher.matches()) {
            if (userId == null) {
                log.warn("Unauthenticated STOMP subscription rejected for conversation destination: {}", destination);
                throw new IllegalArgumentException("Unauthenticated STOMP subscription rejected for conversation destination: " + destination);
            }
            Long conversationId = Long.parseLong(convMatcher.group(1));
            if (conversationMemberRepository == null || !conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
                log.warn("User {} is not a member of conversation {} - subscription rejected for destination: {}", userId, conversationId, destination);
                throw new IllegalArgumentException("User " + userId + " is not a member of conversation " + conversationId);
            }
            log.info("Authorized subscription to conversation {} for userId: {}", conversationId, userId);
            return;
        }

        // 2. Reject malformed / wildcard conversation destinations
        if (destination.startsWith("/topic/conversation") || destination.startsWith("/topic/conversations") || destination.startsWith("/topic/chat/")) {
            log.warn("Invalid conversation subscription destination rejected: {}", destination);
            throw new IllegalArgumentException("Invalid conversation destination: " + destination);
        }

        // 3. User queue destinations: /user/** or direct /queue/**
        if (destination.startsWith("/user/") || destination.equals("/user") || destination.startsWith("/queue/") || destination.equals("/queue")) {
            if (userId == null) {
                log.warn("Unauthenticated STOMP subscription rejected for protected destination: {}", destination);
                throw new IllegalArgumentException("Unauthenticated STOMP subscription rejected for destination: " + destination);
            }

            Matcher targetUserMatcher = TARGET_USER_DESTINATION_PATTERN.matcher(destination);
            if (targetUserMatcher.matches()) {
                Long targetUserId = Long.parseLong(targetUserMatcher.group(1));
                if (!targetUserId.equals(userId)) {
                    log.warn("User {} attempted to subscribe to another user's queue {} - rejected", userId, targetUserId);
                    throw new IllegalArgumentException("Cannot subscribe to another user's queue: " + destination);
                }
            }
            log.debug("Authorized subscription to user queue {} for userId: {}", destination, userId);
            return;
        }

        // 4. Public destinations (e.g. /topic/public, /topic/announcements, etc.)
        log.debug("Permitted subscription to public destination: {}", destination);
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getId();
            }
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal != null && principal.getName() != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
