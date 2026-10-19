package com.aloute.controller.ws;

import com.aloute.config.StompAuthChannelInterceptor;
import com.aloute.config.StompAuthChannelInterceptor.StompPrincipalAuthenticationToken;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.entity.ConversationMember;
import com.aloute.entity.User;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.CustomUserDetailsService;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.DefaultUserDestinationResolver;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatWebSocketControllerAuthorizationTest {

    private ConversationService conversationService;
    private ConversationMemberRepository conversationMemberRepository;
    private SimpMessagingTemplate messagingTemplate;
    private ChatWebSocketController controller;

    private final Long conversationId = 1L;
    private final Long memberAliceId = 100L;
    private final Long memberBobId = 200L;
    private final Long outsiderEveId = 300L;

    private UserPrincipal alicePrincipal;
    private UserPrincipal bobPrincipal;
    private UserPrincipal evePrincipal;

    private Principal aliceStompAuth;
    private Principal eveStompAuth;
    private Principal aliceStandardAuthWithEmailName;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        conversationMemberRepository = mock(ConversationMemberRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        controller = new ChatWebSocketController(conversationService, conversationMemberRepository, messagingTemplate);

        alicePrincipal = createUserPrincipal(memberAliceId, "alice@example.com", true);
        bobPrincipal = createUserPrincipal(memberBobId, "bob@example.com", true);
        evePrincipal = createUserPrincipal(outsiderEveId, "eve@example.com", true);

        // Standard Spring Security token: getName() returns email address
        aliceStandardAuthWithEmailName = new UsernamePasswordAuthenticationToken(
                alicePrincipal, null, alicePrincipal.getAuthorities()
        );
        assertThat(aliceStandardAuthWithEmailName.getName()).isEqualTo("alice@example.com");

        // StompPrincipalAuthenticationToken: getName() returns numeric ID string
        aliceStompAuth = new StompPrincipalAuthenticationToken(alicePrincipal, alicePrincipal.getAuthorities());
        assertThat(aliceStompAuth.getName()).isEqualTo(String.valueOf(memberAliceId));

        eveStompAuth = new StompPrincipalAuthenticationToken(evePrincipal, evePrincipal.getAuthorities());
        assertThat(eveStompAuth.getName()).isEqualTo(String.valueOf(outsiderEveId));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal createUserPrincipal(Long id, String email, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(active);
        user.setRole(Role.USER);
        return UserPrincipal.from(user);
    }

    private ConversationMember createMember(Long userId, String email) {
        ConversationMember member = new ConversationMember();
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        member.setUser(user);
        return member;
    }

    // -------------------------------------------------------------------------
    // 1. Interceptor & Token Contract Tests
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("STOMP Interceptor & Token Identity Contract Tests")
    class StompInterceptorTests {

        private JwtTokenProvider jwtTokenProvider;
        private CustomUserDetailsService userDetailsService;
        private StompAuthChannelInterceptor interceptor;

        @BeforeEach
        void initInterceptor() {
            jwtTokenProvider = mock(JwtTokenProvider.class);
            userDetailsService = mock(CustomUserDetailsService.class);
            interceptor = new StompAuthChannelInterceptor(jwtTokenProvider, userDetailsService);
        }

        @Test
        @DisplayName("StompPrincipalAuthenticationToken preserves UserPrincipal, authorities, HTTP email identity, numeric getName, and equals/hashCode symmetry")
        void stompPrincipalAuthenticationToken_contract() {
            StompPrincipalAuthenticationToken token =
                    new StompPrincipalAuthenticationToken(alicePrincipal, alicePrincipal.getAuthorities());

            assertThat(token.getName()).isEqualTo("100");
            assertThat(token.getPrincipal()).isSameAs(alicePrincipal);
            assertThat(((UserPrincipal) token.getPrincipal()).getUsername()).isEqualTo("alice@example.com");
            assertThat(token.getAuthorities()).isEqualTo(alicePrincipal.getAuthorities());
            assertThat(token.isAuthenticated()).isTrue();

            // Symmetry with parent UsernamePasswordAuthenticationToken
            UsernamePasswordAuthenticationToken httpToken =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null, alicePrincipal.getAuthorities());
            assertThat(httpToken.equals(token)).isTrue();
            assertThat(token.equals(httpToken)).isTrue();
            assertThat(token.hashCode()).isEqualTo(httpToken.hashCode());
        }

        @Test
        @DisplayName("CONNECT with valid Authorization: Bearer token authenticates session with numeric name")
        void preSend_connectWithValidBearerHeader_succeeds() {
            when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(memberAliceId);
            when(userDetailsService.loadById(memberAliceId)).thenReturn(alicePrincipal);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer valid-token");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertThat(result).isNotNull();

            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Principal user = resultAccessor.getUser();
            assertThat(user).isInstanceOf(StompPrincipalAuthenticationToken.class);
            assertThat(user.getName()).isEqualTo("100");
            assertThat(((Authentication) user).getPrincipal()).isEqualTo(alicePrincipal);
        }

        @Test
        @DisplayName("CONNECT with valid aloute_access_token native header authenticates session with numeric name")
        void preSend_connectWithCookieNativeHeader_succeeds() {
            when(jwtTokenProvider.parseUserId("cookie-token")).thenReturn(memberAliceId);
            when(userDetailsService.loadById(memberAliceId)).thenReturn(alicePrincipal);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("aloute_access_token", "cookie-token");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Principal user = resultAccessor.getUser();
            assertThat(user).isInstanceOf(StompPrincipalAuthenticationToken.class);
            assertThat(user.getName()).isEqualTo("100");
        }

        @Test
        @DisplayName("CONNECT with handshake session attribute aloute_access_token authenticates session with numeric name")
        void preSend_connectWithSessionAttributeCookie_succeeds() {
            when(jwtTokenProvider.parseUserId("session-token")).thenReturn(memberAliceId);
            when(userDetailsService.loadById(memberAliceId)).thenReturn(alicePrincipal);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setSessionAttributes(Map.of("aloute_access_token", "session-token"));
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Principal user = resultAccessor.getUser();
            assertThat(user).isInstanceOf(StompPrincipalAuthenticationToken.class);
            assertThat(user.getName()).isEqualTo("100");
        }

        @Test
        @DisplayName("CONNECT with client-supplied native spoof headers (user-id, roles) ignores spoof and enforces token identity")
        void preSend_connectWithSpoofedNativeHeaders_ignoresSpoof() {
            when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(memberAliceId);
            when(userDetailsService.loadById(memberAliceId)).thenReturn(alicePrincipal);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer valid-token");
            accessor.setNativeHeader("user-id", "999");
            accessor.setNativeHeader("roles", "ROLE_ADMIN");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Principal user = resultAccessor.getUser();
            assertThat(user.getName()).isEqualTo("100"); // Verified: token userId 100, not spoofed 999
        }

        @Test
        @DisplayName("CONNECT with invalid token fails closed: clears user and throws IllegalArgumentException")
        void preSend_connectWithInvalidToken_failsClosed() {
            when(jwtTokenProvider.parseUserId("bad-token")).thenThrow(new RuntimeException("Token expired"));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer bad-token");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid STOMP credentials");
        }

        @Test
        @DisplayName("CONNECT with disabled user fails closed: clears user and throws IllegalArgumentException")
        void preSend_connectWithDisabledUser_failsClosed() {
            UserPrincipal disabledUser = createUserPrincipal(memberAliceId, "alice@example.com", false);
            when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(memberAliceId);
            when(userDetailsService.loadById(memberAliceId)).thenReturn(disabledUser);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer valid-token");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("CONNECT with already authenticated HTTP handshake principal maps cleanly to numeric name token")
        void preSend_connectWithHandshakePrincipal_mapsToNumericName() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setLeaveMutable(true);
            // Handshake user standard auth has email as name
            accessor.setUser(aliceStandardAuthWithEmailName);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Principal mappedUser = resultAccessor.getUser();
            assertThat(mappedUser).isInstanceOf(StompPrincipalAuthenticationToken.class);
            assertThat(mappedUser.getName()).isEqualTo("100");
        }

        @Test
        @DisplayName("CONNECT with disabled HTTP handshake principal fails closed")
        void preSend_connectWithDisabledHandshakePrincipal_failsClosed() {
            UserPrincipal disabledUser = createUserPrincipal(memberAliceId, "alice@example.com", false);
            Authentication disabledAuth = new UsernamePasswordAuthenticationToken(disabledUser, null, disabledUser.getAuthorities());

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setUser(disabledAuth);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("CONNECT unauthenticated without token or handshake principal fails closed")
        void preSend_connectWithoutTokenOrUser_failsClosed() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unauthenticated");
        }

        @Test
        @DisplayName("CONNECT with non-UserPrincipal handshake principal fails closed")
        void preSend_connectWithNonUserDetailsHandshakePrincipal_failsClosed() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setUser(() -> "malicious-user");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unauthenticated");
        }

        @Test
        @DisplayName("CONNECT with unauthenticated HTTP handshake principal (isAuthenticated=false) fails closed")
        void preSend_connectWithUnauthenticatedHandshakePrincipal_failsClosed() {
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null);
            assertThat(unauth.isAuthenticated()).isFalse();

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setUser(unauth);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unauthenticated");
        }

        // --- SUBSCRIBE Destination Authorization Tests ---

        @Test
        @DisplayName("SUBSCRIBE with empty or blank destination is rejected")
        void preSend_subscribe_emptyDestination_isRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setUser(aliceStompAuth);
            accessor.setDestination("   ");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Subscription destination cannot be empty");
        }

        @Test
        @DisplayName("SUBSCRIBE to all four real queues by authenticated user succeeds")
        void preSend_subscribe_realQueues_authenticated_succeeds() {
            List<String> realDestinations = List.of(
                    "/user/queue/messages",
                    "/user/queue/typing",
                    "/user/queue/read-receipts",
                    "/user/queue/notifications"
            );

            for (String dest : realDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(aliceStompAuth);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
                assertThat(result).as("Expected successful subscription to %s", dest).isNotNull();
            }
        }

        @Test
        @DisplayName("SUBSCRIBE to real queues by unauthenticated user fails closed")
        void preSend_subscribe_realQueues_unauthenticated_failsClosed() {
            List<String> realDestinations = List.of(
                    "/user/queue/messages",
                    "/user/queue/typing",
                    "/user/queue/read-receipts",
                    "/user/queue/notifications"
            );

            for (String dest : realDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setDestination(dest);
                // No user set (unauthenticated)
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Unauthenticated subscription to %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthenticated");
            }
        }

        @Test
        @DisplayName("SUBSCRIBE to real queue with unauthenticated numeric-name lambda Principal fails closed")
        void preSend_subscribe_numericNameSpoofPrincipal_failsClosed() {
            Principal numericSpoof = () -> "100";
            List<String> realDestinations = List.of(
                    "/user/queue/messages",
                    "/user/queue/typing",
                    "/user/queue/read-receipts",
                    "/user/queue/notifications"
            );

            for (String dest : realDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(numericSpoof);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Numeric name spoof subscription to %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthenticated");
            }
        }

        @Test
        @DisplayName("SUBSCRIBE with unauthenticated Authentication token (isAuthenticated=false) fails closed")
        void preSend_subscribe_unauthenticatedToken_failsClosed() {
            UsernamePasswordAuthenticationToken unauthToken =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null);
            assertThat(unauthToken.isAuthenticated()).isFalse();

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setUser(unauthToken);
            accessor.setDestination("/user/queue/messages");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unauthenticated");
        }

        @Test
        @DisplayName("SUBSCRIBE to unknown topic destinations fails closed")
        void preSend_subscribe_unknownTopicDestinations_failClosed() {
            List<String> topicDestinations = List.of(
                    "/topic/public",
                    "/topic/announcements",
                    "/topic/feed",
                    "/topic/system-status",
                    "/topic/chat"
            );

            for (String dest : topicDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(aliceStompAuth);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Subscription to unsupported topic %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthorized STOMP subscription destination");
            }
        }

        @Test
        @DisplayName("SUBSCRIBE to direct queue destinations fails closed")
        void preSend_subscribe_directQueueDestinations_failClosed() {
            List<String> directQueueDestinations = List.of(
                    "/queue/messages",
                    "/queue/typing",
                    "/queue/read-receipts",
                    "/queue/notifications",
                    "/queue/1"
            );

            for (String dest : directQueueDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(aliceStompAuth);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Subscription to direct queue %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthorized STOMP subscription destination");
            }
        }

        @Test
        @DisplayName("SUBSCRIBE to conversation destinations fails closed")
        void preSend_subscribe_conversationDestinations_failClosed() {
            List<String> conversationDestinations = List.of(
                    "/topic/conversation/1",
                    "/topic/conversations/1",
                    "/queue/conversation/1",
                    "/user/queue/conversation/1",
                    "/topic/conversation/not-a-number",
                    "/topic/conversation/*"
            );

            for (String dest : conversationDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(aliceStompAuth);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Subscription to conversation destination %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthorized STOMP subscription destination");
            }
        }

        @Test
        @DisplayName("SUBSCRIBE to explicit target user destinations fails closed")
        void preSend_subscribe_explicitTargetUserDestinations_failClosed() {
            List<String> explicitTargetDestinations = List.of(
                    "/user/100/queue/messages",
                    "/user/200/queue/messages",
                    "/user/alice@example.com/queue/messages",
                    "/user/other/queue/notifications"
            );

            for (String dest : explicitTargetDestinations) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                accessor.setUser(aliceStompAuth);
                accessor.setDestination(dest);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                        .as("Subscription to explicit target destination %s must be rejected", dest)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unauthorized STOMP subscription destination");
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Controller Authorization & Membership Tests
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("ChatWebSocketController Inbound Authorization & Membership Tests")
    class ControllerAuthorizationTests {

        @Test
        @DisplayName("chat.send: Member succeeds when principal is authenticated UserPrincipal (StompPrincipalAuthenticationToken)")
        void handleChatMessage_whenMember_succeeds() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Hello Bob!");

            controller.handleChatMessage(conversationId, request, aliceStompAuth);

            verify(conversationService).sendMessage(eq(conversationId), eq(memberAliceId), eq(request));
        }

        @Test
        @DisplayName("chat.send: Also supports standard auth where principal is UserPrincipal (backward compatibility)")
        void handleChatMessage_whenStandardAuthWithUserPrincipal_succeeds() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Hello from standard auth!");

            controller.handleChatMessage(conversationId, request, aliceStandardAuthWithEmailName);

            verify(conversationService).sendMessage(eq(conversationId), eq(memberAliceId), eq(request));
        }

        @Test
        @DisplayName("chat.send: Outsider is rejected and message is never sent")
        void handleChatMessage_whenOutsider_rejectsMessage() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, outsiderEveId)).thenReturn(false);

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Sneaky outsider message");

            controller.handleChatMessage(conversationId, request, eveStompAuth);

            verify(conversationService, never()).sendMessage(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("chat.send: Null principal is rejected")
        void handleChatMessage_whenPrincipalIsNull_rejectsMessage() {
            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Unauth message");

            controller.handleChatMessage(conversationId, request, null);

            verify(conversationService, never()).sendMessage(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("chat.send: Unauthenticated arbitrary numeric name spoof (() -> '100') is rejected")
        void handleChatMessage_whenNumericNameSpoofWithoutUserPrincipal_rejectsMessage() {
            Principal numericSpoof = () -> "100";
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, 100L)).thenReturn(true);

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Spoofed message");

            controller.handleChatMessage(conversationId, request, numericSpoof);

            verify(conversationService, never()).sendMessage(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("chat.send: Stale ambient SecurityContextHolder is ignored when incoming principal is null or invalid")
        void handleChatMessage_whenStaleSecurityContextPresent_ignoresAmbientContext() {
            // Set stale ambient context on the thread
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null, alicePrincipal.getAuthorities())
            );

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Exploit attempt using ambient thread context");

            controller.handleChatMessage(conversationId, request, null);

            verify(conversationService, never()).sendMessage(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("chat.send: Service exceptions are propagated and not swallowed")
        void handleChatMessage_whenServiceThrowsException_propagatesException() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);
            doThrow(new IllegalStateException("Database connection failed"))
                    .when(conversationService).sendMessage(eq(conversationId), eq(memberAliceId), any());

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Test message");

            assertThatThrownBy(() -> controller.handleChatMessage(conversationId, request, aliceStompAuth))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Database connection failed");
        }

        @Test
        @DisplayName("chat.typing: Member broadcasts to other conversation members only")
        void handleTypingIndicator_whenMember_broadcastsToOtherMembers() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);
            when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of(
                    createMember(memberAliceId, "alice@example.com"),
                    createMember(memberBobId, "bob@example.com")
            ));

            controller.handleTypingIndicator(conversationId, aliceStompAuth);

            // Verified: broadcasts to Bob (200), not Alice (100)
            verify(messagingTemplate).convertAndSendToUser(
                    eq(String.valueOf(memberBobId)),
                    eq("/queue/typing"),
                    eq(Map.of("conversationId", conversationId, "userId", memberAliceId, "isTyping", true))
            );
            verify(messagingTemplate, never()).convertAndSendToUser(
                    eq(String.valueOf(memberAliceId)),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("chat.typing: Outsider is rejected and never broadcasts")
        void handleTypingIndicator_whenOutsider_rejectsBroadcast() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, outsiderEveId)).thenReturn(false);

            controller.handleTypingIndicator(conversationId, eveStompAuth);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("chat.typing: Unauthenticated or null principal is rejected")
        void handleTypingIndicator_whenUnauthenticated_rejectsBroadcast() {
            controller.handleTypingIndicator(conversationId, null);
            controller.handleTypingIndicator(conversationId, () -> "100");

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("chat.read: Member broadcasts read receipt to other conversation members only")
        void handleReadReceipt_whenMember_broadcastsToOtherMembers() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);
            when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of(
                    createMember(memberAliceId, "alice@example.com"),
                    createMember(memberBobId, "bob@example.com")
            ));

            controller.handleReadReceipt(conversationId, aliceStompAuth);

            // Verified: broadcasts to Bob (200), not Alice (100)
            verify(messagingTemplate).convertAndSendToUser(
                    eq(String.valueOf(memberBobId)),
                    eq("/queue/read-receipts"),
                    eq(Map.of("conversationId", conversationId, "readByUserId", memberAliceId))
            );
            verify(messagingTemplate, never()).convertAndSendToUser(
                    eq(String.valueOf(memberAliceId)),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("chat.read: Outsider is rejected and never broadcasts")
        void handleReadReceipt_whenOutsider_rejectsBroadcast() {
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, outsiderEveId)).thenReturn(false);

            controller.handleReadReceipt(conversationId, eveStompAuth);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("chat.read: Unauthenticated or null principal is rejected")
        void handleReadReceipt_whenUnauthenticated_rejectsBroadcast() {
            controller.handleReadReceipt(conversationId, null);
            controller.handleReadReceipt(conversationId, () -> "100");

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("chat.send: Unauthenticated Authentication token (isAuthenticated=false) is rejected even if member")
        void handleChatMessage_whenUnauthenticatedToken_rejectsMessage() {
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null);
            assertThat(unauth.isAuthenticated()).isFalse();
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);

            SendMessageRequest request = new SendMessageRequest();
            request.setContent("Unauthenticated message attempt");

            controller.handleChatMessage(conversationId, request, unauth);

            verify(conversationService, never()).sendMessage(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("chat.typing: Unauthenticated Authentication token (isAuthenticated=false) is rejected even if member")
        void handleTypingIndicator_whenUnauthenticatedToken_rejectsBroadcast() {
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null);
            assertThat(unauth.isAuthenticated()).isFalse();
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);

            controller.handleTypingIndicator(conversationId, unauth);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("chat.read: Unauthenticated Authentication token (isAuthenticated=false) is rejected even if member")
        void handleReadReceipt_whenUnauthenticatedToken_rejectsBroadcast() {
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken(alicePrincipal, null);
            assertThat(unauth.isAuthenticated()).isFalse();
            when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, memberAliceId)).thenReturn(true);

            controller.handleReadReceipt(conversationId, unauth);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }
    }

    // -------------------------------------------------------------------------
    // 3. Real Spring Registry & Broker UserDestination Resolution Integration
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Real Spring SimpUserRegistry & UserDestinationResolver Integration Tests")
    class SpringUserDestinationResolutionTests {

        private DefaultSimpUserRegistry userRegistry;
        private DefaultUserDestinationResolver resolver;
        private ExecutorSubscribableChannel clientInboundChannel;
        private ExecutorSubscribableChannel brokerChannel;
        private UserDestinationMessageHandler messageHandler;

        @BeforeEach
        void setUpSpringComponents() {
            userRegistry = new DefaultSimpUserRegistry();
            resolver = new DefaultUserDestinationResolver(userRegistry);

            clientInboundChannel = new ExecutorSubscribableChannel();
            brokerChannel = new ExecutorSubscribableChannel();
            messageHandler = new UserDestinationMessageHandler(clientInboundChannel, brokerChannel, resolver);
            messageHandler.start();
        }

        private void simulateConnectAndSubscribe(String sessionId, Principal userAuth, String subscriptionId, String destination) {
            // 1. SessionConnectedEvent
            StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
            connectAccessor.setSessionId(sessionId);
            connectAccessor.setUser(userAuth);
            connectAccessor.setLeaveMutable(true);
            Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
            userRegistry.onApplicationEvent(new SessionConnectedEvent(this, connectMsg, userAuth));

            // 2. SessionSubscribeEvent
            StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            subAccessor.setSessionId(sessionId);
            subAccessor.setSubscriptionId(subscriptionId);
            subAccessor.setDestination(destination);
            subAccessor.setUser(userAuth);
            subAccessor.setLeaveMutable(true);
            Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
            userRegistry.onApplicationEvent(new SessionSubscribeEvent(this, subMsg, userAuth));
        }

        @Test
        @DisplayName("Resolver delivers to intended recipient session ONLY when registered with numeric name (StompPrincipalAuthenticationToken)")
        void userDestinationResolver_deliversToNumericUser_andIsolatesFromOtherUsers() {
            // Connect User 100 on session-100 with StompPrincipalAuthenticationToken (name = "100")
            simulateConnectAndSubscribe("sess-100", aliceStompAuth, "sub-alice", "/user/queue/messages");

            // Connect User 200 on session-200 with StompPrincipalAuthenticationToken (name = "200")
            simulateConnectAndSubscribe("sess-200", new StompPrincipalAuthenticationToken(bobPrincipal, bobPrincipal.getAuthorities()),
                    "sub-bob", "/user/queue/messages");

            // Verify both users are present in the registry by numeric ID
            assertThat(userRegistry.getUser("100")).isNotNull();
            assertThat(userRegistry.getUser("200")).isNotNull();

            // Create fanout message sent to numeric user 100: /user/100/queue/messages
            StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
            sendAccessor.setDestination("/user/100/queue/messages");
            Message<byte[]> messageToUser100 = MessageBuilder.createMessage("Hello 100".getBytes(), sendAccessor.getMessageHeaders());

            // Resolve destination via DefaultUserDestinationResolver
            UserDestinationResult result = resolver.resolveDestination(messageToUser100);

            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo("100");
            assertThat(result.getTargetDestinations()).isNotEmpty();

            // Destination contains sess-100 and DOES NOT contain sess-200 (isolated delivery)
            assertThat(result.getTargetDestinations()).anyMatch(dest -> dest.contains("sess-100"));
            assertThat(result.getTargetDestinations()).noneMatch(dest -> dest.contains("sess-200"));
        }

        @Test
        @DisplayName("Reproduction of F-12 bug: Email-named session causes 0 deliveries when fanout sends to numeric userId")
        void userDestinationResolver_failsDelivery_whenRegisteredWithStandardEmailAuth() {
            // Reproduce bug: connect User 100 with standard UsernamePasswordAuthenticationToken (name = "alice@example.com")
            simulateConnectAndSubscribe("sess-email", aliceStandardAuthWithEmailName, "sub-email", "/user/queue/messages");

            // Registry indexed session under email, NOT numeric ID
            assertThat(userRegistry.getUser("alice@example.com")).isNotNull();
            assertThat(userRegistry.getUser("100")).isNull();

            // Fanout callers send to numeric ID: /user/100/queue/messages
            StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
            sendAccessor.setDestination("/user/100/queue/messages");
            Message<byte[]> messageToNumericId = MessageBuilder.createMessage("Hello".getBytes(), sendAccessor.getMessageHeaders());

            // Resolver cannot find user "100" -> 0 deliveries!
            UserDestinationResult result = resolver.resolveDestination(messageToNumericId);
            assertThat(result.getTargetDestinations()).isEmpty();
        }

        @Test
        @DisplayName("UserDestinationMessageHandler sends translated message to brokerChannel only for target recipient")
        void messageHandler_translatesAndDeliversToBrokerChannel() {
            simulateConnectAndSubscribe("sess-alice", aliceStompAuth, "sub-1", "/user/queue/messages");
            simulateConnectAndSubscribe("sess-eve", eveStompAuth, "sub-2", "/user/queue/messages");

            List<Message<?>> brokerDeliveredMessages = new ArrayList<>();
            brokerChannel.subscribe(brokerDeliveredMessages::add);

            // Send message to numeric ID 100 via /user/100/queue/messages
            StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
            sendAccessor.setDestination("/user/100/queue/messages");
            Message<byte[]> message = MessageBuilder.createMessage("Payload".getBytes(), sendAccessor.getMessageHeaders());

            messageHandler.handleMessage(message);

            // Message delivered to brokerChannel translated for sess-alice ONLY
            assertThat(brokerDeliveredMessages).hasSize(1);
            String targetDest = StompHeaderAccessor.wrap(brokerDeliveredMessages.get(0)).getDestination();
            assertThat(targetDest).contains("sess-alice");
            assertThat(targetDest).doesNotContain("sess-eve");
        }
    }
}
