package com.aloute.integration;

import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.ConversationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-02: client SEND frames must be restricted to the canonical application
 * destinations (/app/** per system architecture §21.1). Before the fix the
 * interceptor only verified the session token, letting an authenticated user
 * SEND to broker/user destinations — including /user/{victim}/queue/**, which
 * Spring's user-destination resolution can turn into a direct delivery into
 * another user's private queue.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class StompSendDestinationAuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private ConversationService conversationService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private User alice;
    private User bob;
    private Long conversationId;

    @BeforeEach
    void setUp() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        long suffix = System.currentTimeMillis();
        alice = createUser("sendauth_alice_" + suffix);
        bob = createUser("sendauth_bob_" + suffix);

        CreateConversationRequest req = new CreateConversationRequest();
        req.setTargetUserId(bob.getId());
        ConversationResponse conv = conversationService.createConversation(alice.getId(), req);
        conversationId = conv.getId();
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private User createUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPasswordHash(passwordEncoder.encode("password123"));
        u.setActive(true);
        u.setRole(Role.USER);
        u.setAuthVersion(0L);
        return userRepository.saveAndFlush(u);
    }

    private StompSession connect(User user) throws Exception {
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders =
                new org.springframework.web.socket.WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtTokenProvider.generate(UserPrincipal.from(user)));
        String wsUrl = "ws://localhost:" + port + "/ws";
        return stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {}
        }).get(5, TimeUnit.SECONDS);
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> payloadType)
            throws InterruptedException {
        BlockingQueue<T> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payloadType.cast(payload));
            }
        });
        Thread.sleep(300);
        return received;
    }

    @Test
    @DisplayName("BL-02: SEND to /user/{victimId}/queue/notifications must not reach the victim's queue")
    void sendToExplicitUserDestinationIsRejected() throws Exception {
        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);
        assertThat(aliceSession.isConnected()).isTrue();
        assertThat(bobSession.isConnected()).isTrue();

        BlockingQueue<Map> bobNotifications =
                subscribe(bobSession, "/user/queue/notifications", Map.class);

        aliceSession.send("/user/" + bob.getId() + "/queue/notifications",
                Map.of("type", "SPOOFED_NOTIFICATION", "text", "attacker payload"));

        // Negative assertion needs a bounded window; a spoofed delivery would arrive quickly.
        Object leaked = bobNotifications.poll(2, TimeUnit.SECONDS);
        assertThat(leaked).as("Victim must not receive attacker-crafted frames on their private queue").isNull();
    }

    @Test
    @DisplayName("BL-02: SEND to broker destination /topic/** is rejected (no broadcast spoofing)")
    void sendToTopicDestinationIsRejected() throws Exception {
        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);

        // SUBSCRIBE allowlist already rejects topic subscriptions; the SEND gate must hold
        // independently of subscription state. Bob has no topic subscription, so the
        // observable outcome is that nothing is delivered anywhere.
        BlockingQueue<Map> bobQueue = subscribe(bobSession, "/user/queue/notifications", Map.class);

        aliceSession.send("/topic/announcements", Map.of("text", "fake global announcement"));

        Object leaked = bobQueue.poll(2, TimeUnit.SECONDS);
        assertThat(leaked).as("No broadcast from a client SEND to a broker destination").isNull();
    }

    @Test
    @DisplayName("Legitimate /app/chat.send delivery still works end-to-end after the SEND gate")
    void legitimateChatSendStillDelivered() throws Exception {
        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);

        BlockingQueue<MessageResponse> bobMessages =
                subscribe(bobSession, "/user/queue/messages", MessageResponse.class);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Legitimate realtime message via /app");
        aliceSession.send("/app/chat.send/" + conversationId, request);

        MessageResponse delivered = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).as("Persisted chat message must still be delivered to members").isNotNull();
        assertThat(delivered.getContent()).isEqualTo("Legitimate realtime message via /app");
    }

    @Test
    @DisplayName("SEND to /app destination by a non-member conversation outsider is ignored by handler")
    void nonMemberAppSendDoesNotDeliver() throws Exception {
        User eve = createUser("sendauth_eve_" + System.currentTimeMillis());
        StompSession eveSession = connect(eve);
        StompSession bobSession = connect(bob);

        BlockingQueue<MessageResponse> bobMessages =
                subscribe(bobSession, "/user/queue/messages", MessageResponse.class);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Eve should not be able to inject into this conversation");
        eveSession.send("/app/chat.send/" + conversationId, request);

        MessageResponse delivered = bobMessages.poll(2, TimeUnit.SECONDS);
        assertThat(delivered).as("Non-members must not inject messages through /app destinations").isNull();
    }
}
