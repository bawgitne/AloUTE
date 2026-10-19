package com.aloute.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.CustomUserDetailsService;
import com.aloute.security.principal.UserPrincipal;
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

@RequiredArgsConstructor
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

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
        return message;
    }
}
