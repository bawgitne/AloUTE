package com.aloute.config;

import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.CustomUserDetailsService;
import com.aloute.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;

            // 1. Try Authorization header: Bearer <token>
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String header = authHeaders.get(0);
                if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = header.substring(7).trim();
                }
            }

            // 2. Fallback to aloute_access_token header or cookie if present
            if (token == null) {
                List<String> cookieHeaders = accessor.getNativeHeader("aloute_access_token");
                if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                    token = cookieHeaders.get(0).trim();
                }
            }

            if (token != null && !token.isBlank()) {
                try {
                    Long userId = jwtTokenProvider.parseUserId(token);
                    UserPrincipal principal = userDetailsService.loadById(userId);
                    if (principal.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        accessor.setUser(auth);
                        log.info("STOMP session connected for userId: {}", userId);
                    }
                } catch (Exception ex) {
                    log.warn("STOMP connect failed token validation: {}", ex.getMessage());
                }
            }
        }
        return message;
    }
}
