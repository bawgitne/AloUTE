package com.aloute.config;

import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtSessionService jwtSessionService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory simple broker for broadcasting to /topic and private messaging via /queue
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix for client-to-server mapped message handlers (@MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific queues (e.g. /user/queue/messages, /user/queue/notifications)
        config.setUserDestinationPrefix("/user");
    }

    private DefaultHandshakeHandler createHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String token = null;
                Object tokenObj = attributes.get(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE);
                if (tokenObj != null) {
                    token = tokenObj.toString();
                }
                if (token == null) {
                    List<String> authHeaders = request.getHeaders().get("Authorization");
                    if (authHeaders != null && !authHeaders.isEmpty()) {
                        String header = authHeaders.get(0);
                        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                            token = header.substring(7).trim();
                        }
                    }
                }
                if (token != null) {
                    try {
                        UserPrincipal principal = jwtSessionService.authenticate(token);
                        attributes.put("token", token);
                        attributes.put(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE, token);
                        attributes.put("authVersion", principal.getAuthVersion());
                        return new StompAuthChannelInterceptor.StompPrincipalAuthenticationToken(principal, principal.getAuthorities());
                    } catch (Exception ex) {
                        return null;
                    }
                }
                return null;
            }
        };
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = createHandshakeHandler();

        // Standard STOMP over WebSocket with SockJS fallback (uses Spring default same-origin protection)
        registry.addEndpoint("/ws")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();

        // Raw WebSocket endpoint without SockJS (uses Spring default same-origin protection)
        registry.addEndpoint("/ws")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                webSocketSessionRegistry.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                webSocketSessionRegistry.unregister(session);
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
