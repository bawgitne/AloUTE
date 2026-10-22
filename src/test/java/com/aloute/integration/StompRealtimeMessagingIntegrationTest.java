package com.aloute.integration;

import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.security.authorization.Role;
import com.aloute.entity.User;
import com.aloute.repository.ConversationRepository;
import com.aloute.repository.MessageRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.reset-request-cooldown-seconds=5"
})
class StompRealtimeMessagingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ConversationService conversationService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtSessionService jwtSessionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private com.aloute.repository.PasswordResetTokenRepository resetTokenRepository;
    @Autowired private com.aloute.service.email.TestEmailSink testEmailSink;
    @Autowired(required = false) private com.aloute.service.email.DefaultEmailService defaultEmailService;
    @Autowired private UserDestinationResolver userDestinationResolver;
    @Autowired private SimpleBrokerMessageHandler simpleBrokerMessageHandler;

    private User alice;
    private User bob;
    private User eve;
    private Long conversationId;

    private String fetchCsrfToken() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .GET()
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        return resp.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst()
                .orElse("");
    }


    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        long suffix = System.currentTimeMillis();
        alice = createUser("alice_" + suffix, "alice_" + suffix + "@example.com");
        bob = createUser("bob_" + suffix, "bob_" + suffix + "@example.com");
        eve = createUser("eve_" + suffix, "eve_" + suffix + "@example.com");

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

    @Autowired private com.aloute.config.WebSocketSessionRegistry webSocketSessionRegistry;

    private void waitForMessageSubscription(Long userId) throws InterruptedException {
        var userMessage = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, "/user/" + userId + "/queue/messages")
                .build();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var resolved = userDestinationResolver.resolveDestination(userMessage);
            if (resolved != null) {
                for (String destination : resolved.getTargetDestinations()) {
                    var brokerMessage = MessageBuilder.fromMessage(userMessage)
                            .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, destination)
                            .build();
                    if (!simpleBrokerMessageHandler.getSubscriptionRegistry()
                            .findSubscriptions(brokerMessage).isEmpty()) {
                        return;
                    }
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for broker message subscription: user=" + userId);
    }

    private User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("password123"));
        u.setActive(true);
        u.setRole(Role.USER);
        u.setAuthVersion(0L);
        return userRepository.saveAndFlush(u);
    }

    /**
     * Genuine pure HttpOnly cookie STOMP client:
     * Sends ONLY Cookie: aloute_access_token=... on WebSocket handshake headers.
     * Absolutely NO HTTP Authorization header and NO STOMP Authorization/native headers.
     */
    private StompSession connectCookieOnlyStompClient(String token, StompSessionHandler handler) throws Exception {
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        if (token != null) {
            wsHeaders.add("Cookie", "aloute_access_token=" + token);
        }
        StompHeaders connectHeaders = new StompHeaders();

        String wsUrl = "ws://localhost:" + port + "/ws";
        StompSessionHandler defaultHandler = new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {}
        };
        return stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, handler != null ? handler : defaultHandler)
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * Explicit Bearer token STOMP client:
     * Sends Authorization: Bearer <token> on STOMP CONNECT frame headers.
     */
    private StompSession connectBearerStompClient(String token, StompSessionHandler handler) throws Exception {
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        String wsUrl = "ws://localhost:" + port + "/ws";
        StompSessionHandler defaultHandler = new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {}
        };
        return stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, handler != null ? handler : defaultHandler)
                .get(5, TimeUnit.SECONDS);
    }

    private StompSession connectStompClient(String token) throws Exception {
        return connectCookieOnlyStompClient(token, null);
    }

    @Test
    void twoClientRealtimeMessageDeliveryAndRestHistoryAfterCommit() throws Exception {
        String aliceToken = jwtTokenProvider.generate(UserPrincipal.from(alice));
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));
        String eveToken = jwtTokenProvider.generate(UserPrincipal.from(eve));

        // 1. Authenticated CONNECT for Alice, Bob, and Eve
        StompSession aliceSession = connectStompClient(aliceToken);
        StompSession bobSession = connectStompClient(bobToken);
        StompSession eveSession = connectStompClient(eveToken);

        assertThat(aliceSession.isConnected()).isTrue();
        assertThat(bobSession.isConnected()).isTrue();
        assertThat(eveSession.isConnected()).isTrue();

        // 2. Bob subscribes to his private queue: /user/queue/messages
        BlockingQueue<MessageResponse> bobReceivedMessages = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                bobReceivedMessages.add((MessageResponse) payload);
            }
        });

        // Eve (outsider) also subscribes to her private queue
        BlockingQueue<MessageResponse> eveReceivedMessages = new LinkedBlockingQueue<>();
        eveSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                eveReceivedMessages.add((MessageResponse) payload);
            }
        });

        // Alice subscribes to her private queue
        BlockingQueue<MessageResponse> aliceReceivedMessages = new LinkedBlockingQueue<>();
        aliceSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                aliceReceivedMessages.add((MessageResponse) payload);
            }
        });

        // Ensure async SUBSCRIBE frames reach broker registry before sending message
        waitForMessageSubscription(bob.getId());
        waitForMessageSubscription(eve.getId());
        waitForMessageSubscription(alice.getId());

        // 3. Alice sends message via conversationService (simulating REST/mutation path)
        SendMessageRequest sendReq = new SendMessageRequest();
        sendReq.setContent("Hello Bob, this is a real-time message!");
        MessageResponse sentResponse = conversationService.sendMessage(conversationId, alice.getId(), sendReq);
        assertThat(sentResponse.getId()).isNotNull();

        // 4. Bob receives the message over network via STOMP
        MessageResponse bobReceived = bobReceivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobReceived).isNotNull();
        assertThat(bobReceived.getId()).isEqualTo(sentResponse.getId());
        assertThat(bobReceived.getContent()).isEqualTo("Hello Bob, this is a real-time message!");
        assertThat(bobReceived.getSender().id()).isEqualTo(alice.getId());

        // 5. Outsider Eve MUST NOT receive Alice's message to Bob
        MessageResponse eveReceived = eveReceivedMessages.poll(1, TimeUnit.SECONDS);
        assertThat(eveReceived).isNull();

        // 6. REST history verification: Bob retrieves messages via REST and finds the message
        Page<MessageResponse> restHistory = conversationService.getMessages(
                conversationId, bob.getId(), PageRequest.of(0, 10));
        assertThat(restHistory.getContent()).anyMatch(m -> m.getId().equals(sentResponse.getId()));

        // 7. Bob replies to Alice
        SendMessageRequest replyReq = new SendMessageRequest();
        replyReq.setContent("Hi Alice, received loud and clear!");
        MessageResponse replyResponse = conversationService.sendMessage(conversationId, bob.getId(), replyReq);

        MessageResponse aliceReceived = aliceReceivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(aliceReceived).isNotNull();
        assertThat(aliceReceived.getId()).isEqualTo(replyResponse.getId());
        assertThat(aliceReceived.getContent()).isEqualTo("Hi Alice, received loud and clear!");
        assertThat(aliceReceived.getSender().id()).isEqualTo(bob.getId());

        // Disconnect sessions
        aliceSession.disconnect();
        bobSession.disconnect();
        eveSession.disconnect();
    }

    @Test
    void rollbackSendsNothingOverStomp() throws Exception {
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));
        StompSession bobSession = connectStompClient(bobToken);

        BlockingQueue<MessageResponse> bobReceivedMessages = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                bobReceivedMessages.add((MessageResponse) payload);
            }
        });

        // Execute in transaction that rolls back
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                SendMessageRequest req = new SendMessageRequest();
                req.setContent("This message will be rolled back!");
                conversationService.sendMessage(conversationId, alice.getId(), req);
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {}

        // Verify Bob receives NOTHING because transaction was rolled back (AFTER_COMMIT guarantee)
        MessageResponse received = bobReceivedMessages.poll(1500, TimeUnit.MILLISECONDS);
        assertThat(received).isNull();

        bobSession.disconnect();
    }

    @Test
    void passwordResetRevokesExistingStompSessionAndOutboundDelivery() throws Exception {
        // Mint two distinct signed JWT tokens for Alice (distinct iat timestamps)
        String aliceToken1 = jwtTokenProvider.generate(UserPrincipal.from(alice));
        Thread.sleep(1100);
        String aliceToken2 = jwtTokenProvider.generate(UserPrincipal.from(alice));
        assertThat(aliceToken1).isNotEqualTo(aliceToken2);

        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));

        // Alice connects via pure Cookie, Bob connects via Bearer
        StompSession aliceSession = connectCookieOnlyStompClient(aliceToken1, null);
        StompSession bobSession = connectBearerStompClient(bobToken, null);

        BlockingQueue<MessageResponse> aliceReceivedMessages = new LinkedBlockingQueue<>();
        aliceSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                aliceReceivedMessages.add((MessageResponse) payload);
            }
        });

        // 1. Fetch CSRF token via HTTP
        String csrfToken = fetchCsrfToken();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

        // 1a. Positive authentication of BOTH old tokens across Bearer and Cookie BEFORE reset
        java.net.http.HttpRequest preB1 = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + aliceToken1)
                .GET().build();
        assertThat(httpClient.send(preB1, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        java.net.http.HttpRequest preC1 = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Cookie", "aloute_access_token=" + aliceToken1)
                .GET().build();
        assertThat(httpClient.send(preC1, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        java.net.http.HttpRequest preB2 = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + aliceToken2)
                .GET().build();
        assertThat(httpClient.send(preB2, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        java.net.http.HttpRequest preC2 = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Cookie", "aloute_access_token=" + aliceToken2)
                .GET().build();
        assertThat(httpClient.send(preC2, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        java.net.URL keystoreUrl = getClass().getResource("/test-smtp-keystore.p12");
        assertThat(keystoreUrl).isNotNull();
        java.io.File keystoreFile = new java.io.File(keystoreUrl.toURI());

        String origTrustStore = System.getProperty("javax.net.ssl.trustStore");
        String origTrustStorePass = System.getProperty("javax.net.ssl.trustStorePassword");
        String origTrustStoreType = System.getProperty("javax.net.ssl.trustStoreType");

        System.setProperty("javax.net.ssl.trustStore", keystoreFile.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.InputStream in = new java.io.FileInputStream(keystoreFile)) {
            trustStore.load(in, "changeit".toCharArray());
        }
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        javax.net.ssl.SSLContext origSslContext = javax.net.ssl.SSLContext.getDefault();
        javax.net.ssl.SSLContext.setDefault(sslContext);

        try (com.aloute.service.email.LocalSmtpServer smtpServer = new com.aloute.service.email.LocalSmtpServer()) {
            if (defaultEmailService != null) {
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "smtpHost", "localhost");
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "smtpPort", smtpServer.getPort());
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "starttlsEnable", true);
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "sslCheckServerIdentity", true);
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "smtpUsername", "testuser");
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "smtpPassword", "testpass");
            }

            // 2. Request forgot password via real HTTP endpoint
            testEmailSink.clearSink();
            String forgotJson = objectMapper.writeValueAsString(new ForgotPasswordRequest(alice.getEmail()));
            java.net.http.HttpRequest forgotReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(forgotJson))
                    .build();
            java.net.http.HttpResponse<String> forgotResp = httpClient.send(forgotReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(forgotResp.statusCode()).isEqualTo(200);

            // Assert real loopback SMTP server received email over STARTTLS network transport
            boolean smtpReceived = smtpServer.awaitMessage(5, TimeUnit.SECONDS);
            assertThat(smtpReceived).isTrue();
            assertThat(smtpServer.getReceivedMessages()).hasSize(1);
            assertThat(smtpServer.getReceivedMessages().get(0)).contains("To: " + alice.getEmail());

            // Unknown email also returns identical 200 generic response via HTTP
            String unknownForgotJson = objectMapper.writeValueAsString(new ForgotPasswordRequest("unknown_user_" + System.currentTimeMillis() + "@example.com"));
            java.net.http.HttpRequest unknownForgotReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(unknownForgotJson))
                    .build();
            java.net.http.HttpResponse<String> unknownForgotResp = httpClient.send(unknownForgotReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(unknownForgotResp.statusCode()).isEqualTo(200);

            // Extract reset token directly from loopback SMTP STARTTLS network payload
            assertThat(smtpServer.getReceivedMessages()).isNotEmpty();
            String resetToken = extractTokenFromRawSmtp(smtpServer.getReceivedMessages().get(0));
            assertThat(resetToken).isNotBlank();

        // 3. Test expired token rejection via actual HTTP (fixture setup: set token expires_at in DB)
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            var tok = resetTokenRepository.findByUser(alice).orElseThrow();
            tok.setExpiresAt(java.time.Instant.now().minusSeconds(10));
            resetTokenRepository.saveAndFlush(tok);
            return null;
        });

        String expiredResetJson = objectMapper.writeValueAsString(new ResetPasswordRequest(resetToken, "brandNewPassword999", "brandNewPassword999"));
        java.net.http.HttpRequest expiredResetReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(expiredResetJson))
                .build();
        java.net.http.HttpResponse<String> expiredResetResp = httpClient.send(expiredResetReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(expiredResetResp.statusCode()).isEqualTo(400);

        // Re-issue fresh token via HTTP after cooldown
        testEmailSink.clearSink();
        Thread.sleep(5100);
        httpClient.send(forgotReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        long deadline = System.currentTimeMillis() + 5000;
        while (smtpServer.getReceivedMessages().size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(smtpServer.getReceivedMessages().size()).isGreaterThanOrEqualTo(2);
        String freshToken = extractTokenFromRawSmtp(smtpServer.getReceivedMessages().get(1));
        assertThat(freshToken).isNotBlank();

        // 4. Complete reset via actual HTTP API
        String validResetJson = objectMapper.writeValueAsString(new ResetPasswordRequest(freshToken, "brandNewPassword999", "brandNewPassword999"));
        java.net.http.HttpRequest validResetReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(validResetJson))
                .build();
        java.net.http.HttpResponse<String> validResetResp = httpClient.send(validResetReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(validResetResp.statusCode()).isEqualTo(200);

        // Token reuse rejected via HTTP
        java.net.http.HttpResponse<String> reuseResp = httpClient.send(validResetReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(reuseResp.statusCode()).isEqualTo(400);

        // 5. Old password login returns 401 via HTTP
        String oldLoginJson = objectMapper.writeValueAsString(new com.aloute.dto.request.LoginRequest(alice.getEmail(), "password123"));
        java.net.http.HttpRequest oldLoginReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(oldLoginJson))
                .build();
        java.net.http.HttpResponse<String> oldLoginResp = httpClient.send(oldLoginReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(oldLoginResp.statusCode()).isEqualTo(401);

        // 6. BOTH old JWTs return 401 across Cookie and Bearer
        java.net.http.HttpRequest b1Req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + aliceToken1)
                .GET().build();
        assertThat(httpClient.send(b1Req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        java.net.http.HttpRequest c1Req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Cookie", "aloute_access_token=" + aliceToken1)
                .GET().build();
        assertThat(httpClient.send(c1Req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        java.net.http.HttpRequest b2Req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + aliceToken2)
                .GET().build();
        assertThat(httpClient.send(b2Req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        java.net.http.HttpRequest c2Req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Cookie", "aloute_access_token=" + aliceToken2)
                .GET().build();
        assertThat(httpClient.send(c2Req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        // 7. Fresh new password login succeeds via HTTP
        String newLoginJson = objectMapper.writeValueAsString(new com.aloute.dto.request.LoginRequest(alice.getEmail(), "brandNewPassword999"));
        java.net.http.HttpRequest newLoginReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(newLoginJson))
                .build();
        java.net.http.HttpResponse<String> newLoginResp = httpClient.send(newLoginReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(newLoginResp.statusCode()).isEqualTo(200);

        String freshAliceToken = newLoginResp.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .map(c -> {
                    int end = c.indexOf(';');
                    return end >= 0 ? c.substring("aloute_access_token=".length(), end) : c.substring("aloute_access_token=".length());
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("aloute_access_token cookie not found in login response"));
        assertThat(freshAliceToken).isNotBlank();

        // 7b. Positively authenticate fresh auth cookie via HTTP /api/auth/me (200) before new STOMP connect
        java.net.http.HttpRequest freshMeReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Cookie", "aloute_access_token=" + freshAliceToken)
                .GET().build();
        java.net.http.HttpResponse<String> freshMeResp = httpClient.send(freshMeReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(freshMeResp.statusCode()).isEqualTo(200);

        // 8a. Alice's old connected session attempts to send message via STOMP after password reset
        String markerContent = "ATTEMPT_SEND_AFTER_RESET_" + System.currentTimeMillis();
        SendMessageRequest unauthSendReq = new SendMessageRequest();
        unauthSendReq.setContent(markerContent);
        aliceSession.send("/app/chat.send/" + conversationId, unauthSendReq);

        // Give server time to process and reject the SEND frame
        Thread.sleep(1000);

        // Assert zero DB persistence: no message with the marker content exists in database
        assertThat(messageRepository.findAll().stream().anyMatch(m -> markerContent.equals(m.getContent()))).isFalse();

        // 8b. Bob sends message to Alice: outbound delivery to Alice's old connection is SUPPRESSED
        SendMessageRequest msgReq = new SendMessageRequest();
        msgReq.setContent("Hey Alice, can you see this after HTTP password reset?");
        conversationService.sendMessage(conversationId, bob.getId(), msgReq);

        MessageResponse receivedOnRevokedSession = aliceReceivedMessages.poll(2, TimeUnit.SECONDS);
        assertThat(receivedOnRevokedSession).isNull();

        // 9. Old tokens cannot connect again (both cookie and bearer fail)
        assertThatThrownBy(() -> connectCookieOnlyStompClient(aliceToken1, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connectBearerStompClient(aliceToken2, null))
                .isInstanceOf(Exception.class);

        // 10. Alice connects new STOMP session with fresh token minted after password reset
        StompSession aliceNewSession = connectCookieOnlyStompClient(freshAliceToken, null);
        assertThat(aliceNewSession.isConnected()).isTrue();

        BlockingQueue<MessageResponse> aliceNewReceivedMessages = new LinkedBlockingQueue<>();
        aliceNewSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                aliceNewReceivedMessages.add((MessageResponse) payload);
            }
        });

        // 11. Bob sends another message: fresh Alice session receives it over STOMP
        SendMessageRequest msg2Req = new SendMessageRequest();
        msg2Req.setContent("Message to re-authenticated Alice!");
        MessageResponse m2 = conversationService.sendMessage(conversationId, bob.getId(), msg2Req);

        MessageResponse receivedOnNewSession = aliceNewReceivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(receivedOnNewSession).isNotNull();
        assertThat(receivedOnNewSession.getId()).isEqualTo(m2.getId());

        try {
            aliceSession.disconnect();
        } catch (Exception ignored) {}
        aliceNewSession.disconnect();
        bobSession.disconnect();
        } finally {
            if (defaultEmailService != null) {
                org.springframework.test.util.ReflectionTestUtils.setField(defaultEmailService, "smtpHost", "");
            javax.net.ssl.SSLContext.setDefault(origSslContext);
            }
            if (origTrustStore != null) {
                System.setProperty("javax.net.ssl.trustStore", origTrustStore);
            } else {
                System.clearProperty("javax.net.ssl.trustStore");
            }
            if (origTrustStorePass != null) {
                System.setProperty("javax.net.ssl.trustStorePassword", origTrustStorePass);
            } else {
                System.clearProperty("javax.net.ssl.trustStorePassword");
            }
            if (origTrustStoreType != null) {
                System.setProperty("javax.net.ssl.trustStoreType", origTrustStoreType);
            } else {
                System.clearProperty("javax.net.ssl.trustStoreType");
            }
        }
    }


    @Test
    void expiredNewConnectDenied() {
        String expiredToken = jwtTokenProvider.generate(UserPrincipal.from(alice), -10);

        // Denied on both pure cookie and bearer modes
        assertThatThrownBy(() -> connectCookieOnlyStompClient(expiredToken, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connectBearerStompClient(expiredToken, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void logoutRevokesTargetStompSessionWhileLeavingOtherSessionActive() throws Exception {
        String aliceToken1 = jwtTokenProvider.generate(UserPrincipal.from(alice));
        String aliceToken2 = jwtTokenProvider.generate(UserPrincipal.from(alice));
        assertThat(aliceToken1).isNotEqualTo(aliceToken2);

        String aliceToken1Alias = aliceToken1 + "!";
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));

        StompSession aliceSession1 = connectCookieOnlyStompClient(aliceToken1, null);
        StompSession aliceAliasSession = connectBearerStompClient(aliceToken1Alias, null);
        StompSession aliceSession2 = connectBearerStompClient(aliceToken2, null);
        StompSession bobSession = connectBearerStompClient(bobToken, null);

        BlockingQueue<MessageResponse> session1Messages = new LinkedBlockingQueue<>();
        aliceSession1.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return MessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { session1Messages.add((MessageResponse) payload); }
        });

        BlockingQueue<MessageResponse> session2Messages = new LinkedBlockingQueue<>();
        aliceSession2.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return MessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { session2Messages.add((MessageResponse) payload); }
        });

        BlockingQueue<Object> bobTypingQueue = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { bobTypingQueue.add(payload); }
        });

        BlockingQueue<Object> bobReadQueue = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/read-receipts", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { bobReadQueue.add(payload); }
        });

        SendMessageRequest preMsg = new SendMessageRequest();
        preMsg.setContent("Preflight test message");
        conversationService.sendMessage(conversationId, bob.getId(), preMsg);
        MessageResponse m1 = session1Messages.poll(3, TimeUnit.SECONDS);
        MessageResponse m2 = session2Messages.poll(3, TimeUnit.SECONDS);
        assertThat(m1).isNotNull();
        assertThat(m2).isNotNull();

        long messageCountBefore = messageRepository.count();

        String csrfToken = fetchCsrfToken();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest logoutReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/logout"))
                .header("Authorization", "Bearer " + aliceToken1)
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();
        java.net.http.HttpResponse<String> logoutResp = httpClient.send(logoutReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(logoutResp.statusCode()).isEqualTo(204);

        // Explicitly observe transport disconnect on both canonical and alias sessions
        long deadline = System.currentTimeMillis() + 3000;
        while ((aliceSession1.isConnected() || aliceAliasSession.isConnected()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(aliceSession1.isConnected()).as("Target canonical session transport must be closed").isFalse();
        assertThat(aliceAliasSession.isConnected()).as("Target alias session transport must be closed").isFalse();

        // 1. Post-logout SEND is rejected on closed transport and produces zero message persistence
        SendMessageRequest rejectedSendReq = new SendMessageRequest();
        rejectedSendReq.setContent("Illegal post-logout attempt");
        assertThatThrownBy(() -> aliceSession1.send("/app/chat.send/" + conversationId, rejectedSendReq))
                .isInstanceOf(Exception.class);
        assertThat(messageRepository.count())
                .as("Rejected post-logout SEND must not create any new message row")
                .isEqualTo(messageCountBefore);
        assertThat(messageRepository.findAll().stream().noneMatch(m -> "Illegal post-logout attempt".equals(m.getContent())))
                .as("Rejected post-logout SEND payload must not be persisted")
                .isTrue();

        // 2. Post-logout typing and read attempts are rejected
        assertThatThrownBy(() -> aliceSession1.send("/app/chat.typing/" + conversationId, Map.of("isTyping", true)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> aliceSession1.send("/app/chat.read/" + conversationId, Map.of()))
                .isInstanceOf(Exception.class);

        // 3. Post-logout SUBSCRIBE attempts are rejected (old SUBSCRIBE denied)
        assertThatThrownBy(() -> aliceSession1.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return MessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {}
        })).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> aliceSession1.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {}
        })).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> aliceSession1.subscribe("/user/queue/read-receipts", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {}
        })).isInstanceOf(Exception.class);

        // Also confirm rejected operations on alias session
        assertThatThrownBy(() -> aliceAliasSession.send("/app/chat.send/" + conversationId, rejectedSendReq))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> aliceAliasSession.send("/app/chat.typing/" + conversationId, Map.of("isTyping", true)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> aliceAliasSession.send("/app/chat.read/" + conversationId, Map.of()))
                .isInstanceOf(Exception.class);

        // 4. Bob sends message to conversation: outbound delivery to revoked session is suppressed
        SendMessageRequest postMsg = new SendMessageRequest();
        postMsg.setContent("Post-logout message");
        conversationService.sendMessage(conversationId, bob.getId(), postMsg);

        MessageResponse session1Post = session1Messages.poll(2, TimeUnit.SECONDS);
        assertThat(session1Post).as("Revoked session 1 must receive no message delivery").isNull();

        MessageResponse session2Post = session2Messages.poll(3, TimeUnit.SECONDS);
        assertThat(session2Post).as("Active session 2 must receive message normally").isNotNull();
        assertThat(session2Post.getContent()).isEqualTo("Post-logout message");

        // Bob receives no private typing or read deliveries from revoked session
        assertThat(bobTypingQueue.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(bobReadQueue.poll(1, TimeUnit.SECONDS)).isNull();

        // 5. Old tokens and alias tokens cannot establish new STOMP connections (new CONNECT denied)
        assertThatThrownBy(() -> connectBearerStompClient(aliceToken1, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connectCookieOnlyStompClient(aliceToken1, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connectBearerStompClient(aliceToken1Alias, null))
                .isInstanceOf(Exception.class);

        // 6. Separate token remains valid and usable for new STOMP session
        StompSession aliceSession3 = connectBearerStompClient(aliceToken2, null);
        assertThat(aliceSession3.isConnected()).isTrue();
        aliceSession3.disconnect();
        try { aliceSession1.disconnect(); } catch (Exception ignored) {}
        try { aliceAliasSession.disconnect(); } catch (Exception ignored) {}
        aliceSession2.disconnect();
        bobSession.disconnect();
    }

    @Test
    void tokenExpiresAfterConnect_sendRejected_connectionClosed_noPersistedMessage() throws Exception {
        // Mint a short-lived token valid for 3 seconds
        String shortLivedToken = jwtTokenProvider.generate(UserPrincipal.from(alice), 3);

        // 1. Healthy peer control: Bob connects with valid long-lived token and subscribes
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));
        StompSession bobSession = connectCookieOnlyStompClient(bobToken, null);
        assertThat(bobSession.isConnected()).isTrue();

        BlockingQueue<MessageResponse> bobMessages = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                bobMessages.add((MessageResponse) payload);
            }
        });

        // 2. Alice connects via pure HttpOnly cookie with short-lived token
        CountDownLatch errorLatch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean transportErrorFired = new java.util.concurrent.atomic.AtomicBoolean(false);

        StompSessionHandler aliceHandler = new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                transportErrorFired.set(true);
                errorLatch.countDown();
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                transportErrorFired.set(true);
                errorLatch.countDown();
            }
        };

        StompSession aliceSession = connectCookieOnlyStompClient(shortLivedToken, aliceHandler);
        assertThat(aliceSession.isConnected()).isTrue();
        String aliceSessionId = aliceSession.getSessionId();

        // 3. Wait until Alice's token has expired (3.5 seconds)
        Thread.sleep(3500);

        // 4. Attempt to SEND a chat message over the existing STOMP session
        String marker = "EXPIRED_STOMP_REJECTED_" + System.currentTimeMillis();
        SendMessageRequest req = new SendMessageRequest();
        req.setContent(marker);

        try {
            aliceSession.send("/app/chat.send/" + conversationId, req);
        } catch (Exception ignored) {
            // Transport may fail immediately
        }

        // Wait for error callback / socket closure
        errorLatch.await(3, TimeUnit.SECONDS);

        // 5. Assert actual transport closure and session registry cleanup
        boolean disconnected = !aliceSession.isConnected() || transportErrorFired.get();
        assertThat(disconnected).isTrue();

        // Check WebSocketSessionRegistry: transport session is closed or removed
        org.springframework.web.socket.WebSocketSession wsSession = webSocketSessionRegistry.getSession(aliceSessionId);
        if (wsSession != null) {
            assertThat(wsSession.isOpen()).isFalse();
        }

        // 6. Assert NO message with marker was persisted in the database
        boolean messagePersisted = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, 100)
        ).stream().anyMatch(m -> marker.equals(m.getContent()));
        assertThat(messagePersisted).isFalse();

        // 7. Canonical validator rejects the expired token
        assertThatThrownBy(() -> jwtSessionService.authenticate(shortLivedToken))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);

        // 8. Assert Bob never received the expired marker message
        MessageResponse bobMarker = bobMessages.poll(500, TimeUnit.MILLISECONDS);
        assertThat(bobMarker).isNull();

        // 9. Positive healthy-peer control: Send a message to Bob, proving broker and Bob's subscription are healthy
        SendMessageRequest toBobReq = new SendMessageRequest();
        toBobReq.setContent("Healthy broker check to Bob");
        MessageResponse bobSent = conversationService.sendMessage(conversationId, alice.getId(), toBobReq);
        assertThat(bobSent).isNotNull();

        MessageResponse bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobReceived).isNotNull();
        assertThat(bobReceived.getContent()).isEqualTo("Healthy broker check to Bob");

        bobSession.disconnect();
        try {
            aliceSession.disconnect();
        } catch (Exception ignored) {}
    }

    @Test
    void tokenExpiresAfterConnect_explicitBearer_sendRejected_connectionClosed_noPersistedMessage() throws Exception {
        // Mint a short-lived token valid for 3 seconds
        String shortLivedToken = jwtTokenProvider.generate(UserPrincipal.from(alice), 3);

        // 1. Healthy peer control: Bob connects with valid long-lived token and subscribes
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));
        StompSession bobSession = connectCookieOnlyStompClient(bobToken, null);
        assertThat(bobSession.isConnected()).isTrue();

        BlockingQueue<MessageResponse> bobMessages = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                bobMessages.add((MessageResponse) payload);
            }
        });

        // 2. Alice connects via explicit Bearer token with short-lived token
        CountDownLatch errorLatch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean transportErrorFired = new java.util.concurrent.atomic.AtomicBoolean(false);

        StompSessionHandler aliceHandler = new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                transportErrorFired.set(true);
                errorLatch.countDown();
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                transportErrorFired.set(true);
                errorLatch.countDown();
            }
        };

        StompSession aliceSession = connectBearerStompClient(shortLivedToken, aliceHandler);
        assertThat(aliceSession.isConnected()).isTrue();
        String aliceSessionId = aliceSession.getSessionId();

        // 3. Wait until Alice's token has expired (3.5 seconds)
        Thread.sleep(3500);

        // 4. Attempt to SEND a chat message over the existing STOMP session
        String marker = "EXPIRED_BEARER_STOMP_REJECTED_" + System.currentTimeMillis();
        SendMessageRequest req = new SendMessageRequest();
        req.setContent(marker);

        try {
            aliceSession.send("/app/chat.send/" + conversationId, req);
        } catch (Exception ignored) {}

        // Wait for error callback / socket closure
        errorLatch.await(3, TimeUnit.SECONDS);

        // 5. Assert actual transport closure and session registry cleanup
        boolean disconnected = !aliceSession.isConnected() || transportErrorFired.get();
        assertThat(disconnected).isTrue();

        org.springframework.web.socket.WebSocketSession wsSession = webSocketSessionRegistry.getSession(aliceSessionId);
        if (wsSession != null) {
            assertThat(wsSession.isOpen()).isFalse();
        }

        // 6. Assert NO message with marker was persisted in the database
        boolean messagePersisted = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, 100)
        ).stream().anyMatch(m -> marker.equals(m.getContent()));
        assertThat(messagePersisted).isFalse();

        // 7. Assert Bob NEVER received the expired marker message
        MessageResponse bobMarker = bobMessages.poll(500, TimeUnit.MILLISECONDS);
        assertThat(bobMarker).isNull();

        // 8. Positive healthy-peer control: Send a message to Bob, proving broker is healthy
        SendMessageRequest toBobReq = new SendMessageRequest();
        toBobReq.setContent("Healthy broker check to Bob from Bearer test");
        MessageResponse bobSent = conversationService.sendMessage(conversationId, alice.getId(), toBobReq);
        assertThat(bobSent).isNotNull();

        MessageResponse bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobReceived).isNotNull();
        assertThat(bobReceived.getContent()).isEqualTo("Healthy broker check to Bob from Bearer test");

        bobSession.disconnect();
        try {
            aliceSession.disconnect();
        } catch (Exception ignored) {}
    }

    @Test
    void restSendFallbackWhenWsBlockedPersistsExactlyOneMessageAndHistoryCatchesUp() throws Exception {
        // 1. Healthy peer Bob connects to STOMP and subscribes
        String bobToken = jwtTokenProvider.generate(UserPrincipal.from(bob));
        StompSession bobSession = connectCookieOnlyStompClient(bobToken, null);
        assertThat(bobSession.isConnected()).isTrue();

        BlockingQueue<MessageResponse> bobMessages = new LinkedBlockingQueue<>();
        bobSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                bobMessages.add((MessageResponse) payload);
            }
        });

        // 2. Alice has NO STOMP connection (WS is offline/blocked for Alice)
        // Alice sends message via REST API
        String aliceToken = jwtTokenProvider.generate(UserPrincipal.from(alice));
        String csrfToken = fetchCsrfToken();

        String uniqueContent = "REST_FALLBACK_MSG_" + System.currentTimeMillis();
        SendMessageRequest sendReq = new SendMessageRequest();
        sendReq.setContent(uniqueContent);
        String sendJson = objectMapper.writeValueAsString(sendReq);

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest restSendReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                .header("Content-Type", "application/json")
                .header("Cookie", "aloute_access_token=" + aliceToken + "; XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(sendJson))
                .build();

        java.net.http.HttpResponse<String> sendResp = httpClient.send(restSendReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(sendResp.statusCode()).isEqualTo(201);

        // 3. Bob receives the message immediately over STOMP
        MessageResponse bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobReceived).isNotNull();
        assertThat(bobReceived.getContent()).isEqualTo(uniqueContent);

        // 4. Assert database contains EXACTLY ONE persisted message with this content
        long count = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, 100)
        ).stream().filter(m -> uniqueContent.equals(m.getContent())).count();
        assertThat(count).isEqualTo(1L);

        // 5. REST history fetch by Alice returns the message
        java.net.http.HttpRequest historyReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/conversations/" + conversationId + "/messages?page=0&size=30"))
                .header("Cookie", "aloute_access_token=" + aliceToken)
                .GET().build();
        java.net.http.HttpResponse<String> historyResp = httpClient.send(historyReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(historyResp.statusCode()).isEqualTo(200);
        assertThat(historyResp.body()).contains(uniqueContent);

        // 6. Alice connects STOMP session (resubscribe + catchup)
        StompSession aliceSession = connectCookieOnlyStompClient(aliceToken, null);
        assertThat(aliceSession.isConnected()).isTrue();

        aliceSession.disconnect();
        bobSession.disconnect();
    }

    private String extractTokenFromRawSmtp(String rawSmtp) {
        String normalized = rawSmtp.replaceAll("=\r?\n", "").replace("=3D", "=");
        assertThat(normalized).contains("#token=");
        int start = normalized.indexOf("#token=") + "#token=".length();
        return normalized.substring(start).trim().split("[\\s\\r\\n\"'<>]+")[0];
    }
}
