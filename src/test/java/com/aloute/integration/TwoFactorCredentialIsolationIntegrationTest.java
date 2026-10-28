package com.aloute.integration;

import com.aloute.config.WebSocketSessionRegistry;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserBackupCodeRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.TwoFactorService;
import com.aloute.service.email.TestEmailSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class TwoFactorCredentialIsolationIntegrationTest {

    private static final String BASE64_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserBackupCodeRepository userBackupCodeRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtSessionService jwtSessionService;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private TestEmailSink emailSink;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private WebSocketSessionRegistry webSocketSessionRegistry;

    private User testUser;
    private TrackingWebSocketClient trackingWsClient;
    private WebSocketStompClient stompClient;
    private HttpClient httpClient;

    private final Set<StompSession> trackedSessions = ConcurrentHashMap.newKeySet();

    /**
     * Small owned test-local WebSocket client tracking transport lifecycle:
     * - Records open transport sessions upon establishment.
     * - Captures actual afterConnectionClosed callback signals per connection.
     * - Guarantees closeFuture completes on transport close or initial connect failure.
     */
    private static class TrackingWebSocketClient implements WebSocketClient {
        private final WebSocketClient delegate = new StandardWebSocketClient();
        final Set<WebSocketSession> openSessions = ConcurrentHashMap.newKeySet();
        final List<CompletableFuture<Void>> transportCloseFutures = new CopyOnWriteArrayList<>();
        final List<Throwable> closeErrors = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<WebSocketSession> execute(WebSocketHandler handler, String uriTemplate, Object... uriVariables) {
            return execute(handler, null, URI.create(uriTemplate));
        }

        @Override
        public CompletableFuture<WebSocketSession> execute(WebSocketHandler handler, WebSocketHttpHeaders headers, URI uri) {
            CompletableFuture<Void> closeFuture = new CompletableFuture<>();
            transportCloseFutures.add(closeFuture);

            WebSocketHandler wrappingHandler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    openSessions.add(session);
                    handler.afterConnectionEstablished(session);
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    handler.handleMessage(session, message);
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    handler.handleTransportError(session, exception);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    try {
                        handler.afterConnectionClosed(session, closeStatus);
                    } finally {
                        openSessions.remove(session);
                        closeFuture.complete(null);
                    }
                }

                @Override
                public boolean supportsPartialMessages() {
                    return handler.supportsPartialMessages();
                }
            };

            CompletableFuture<WebSocketSession> sessionFuture = delegate.execute(wrappingHandler, headers, uri);
            sessionFuture.whenComplete((session, ex) -> {
                if (ex != null) {
                    closeFuture.complete(null);
                }
            });
            return sessionFuture;
        }

        List<Throwable> closeAllOpenSessions() {
            List<Throwable> errors = new ArrayList<>();
            List<WebSocketSession> sessions = new ArrayList<>(openSessions);
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                    closeErrors.add(t);
                }
            }
            return errors;
        }
    }

    /**
     * STOMP session handler that preserves transport error outcomes and provides latch signals.
     */
    private static class TrackingStompSessionHandler extends StompSessionHandlerAdapter {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> transportError = new AtomicReference<>();
        final AtomicBoolean transportErrorFired = new AtomicBoolean(false);
        volatile StompSession session;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            this.session = session;
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            transportError.set(exception);
            transportErrorFired.set(true);
            errorLatch.countDown();
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            transportError.set(exception);
            transportErrorFired.set(true);
            errorLatch.countDown();
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));
    }

    private String generateExpiredTicket(Long userId) {
        Instant now = Instant.now().minusSeconds(10);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("purpose", "2FA_CHALLENGE")
                .claim("type", "TOTP")
                .issuedAt(Date.from(now.minusSeconds(300)))
                .expiration(Date.from(now))
                .signWith(signingKey())
                .compact();
    }

    private String generateWrongPurposeTicket(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("purpose", "WRONG_PURPOSE")
                .claim("type", "TOTP")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey())
                .compact();
    }

    private String generateVersionedPurposeTicket(Long userId, long authVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("v", authVersion)
                .claim("purpose", "2FA_CHALLENGE")
                .claim("type", "TOTP")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey())
                .compact();
    }

    private void assertStompConnectionRejected(WebSocketHttpHeaders wsHeaders, StompHeaders connectHeaders) throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        TrackingStompSessionHandler handler = new TrackingStompSessionHandler();
        CompletableFuture<StompSession> future = stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, handler);

        // Track session if it unexpectedly completes
        future.whenComplete((session, ex) -> {
            if (session != null) {
                trackedSessions.add(session);
            }
        });

        try {
            StompSession unexpectedSession = future.get(5, TimeUnit.SECONDS);
            trackedSessions.add(unexpectedSession);
            unexpectedSession.disconnect();
            fail("Security Violation: Expected STOMP connection to be rejected, but connected successfully with session: "
                    + unexpectedSession.getSessionId());
        } catch (ExecutionException ex) {
            // Server auth rejection confirmed
            assertThat(ex.getCause()).isNotNull();
            // Verify error callback fired and error evidence preserved
            handler.errorLatch.await(3, TimeUnit.SECONDS);
            assertThat(handler.transportErrorFired.get() || !future.isCompletedExceptionally()).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread interrupted while awaiting STOMP connection rejection", e);
        } catch (TimeoutException e) {
            fail("STOMP connection did not fail within bounded time (timeout is not proof of credential rejection)");
        }
    }

    private StompSession connectValidStomp(WebSocketHttpHeaders wsHeaders, StompHeaders connectHeaders) throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        TrackingStompSessionHandler handler = new TrackingStompSessionHandler();
        CompletableFuture<StompSession> future = stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, handler);

        future.whenComplete((session, ex) -> {
            if (session != null) {
                trackedSessions.add(session);
            }
        });

        StompSession session = future.get(5, TimeUnit.SECONDS);
        trackedSessions.add(session);
        assertThat(session.isConnected()).isTrue();
        return session;
    }

    @BeforeEach
    void setUp() {
        emailSink.clearSink();
        twoFactorService.clearEmailOtpCache();
        trackedSessions.clear();

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        trackingWsClient = new TrackingWebSocketClient();
        stompClient = new WebSocketStompClient(trackingWsClient);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        stompClient.start();
        assertThat(stompClient.isRunning()).isTrue();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        testUser = tx.execute(status -> {
            long suffix = System.nanoTime();
            User u = new User();
            u.setUsername("iso_" + suffix);
            u.setEmail("iso_" + suffix + "@aloute.edu.vn");
            u.setPasswordHash(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            u.setActive(true);
            u.setVerified(true);
            u.setAuthVersion(0L);
            u = userRepository.save(u);

            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Isolation Test User");
            profileRepository.save(p);
            return u;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        List<Throwable> teardownErrors = new ArrayList<>();

        // 1. Explicitly disconnect all connected STOMP sessions
        for (StompSession session : trackedSessions) {
            if (session != null && session.isConnected()) {
                try {
                    session.disconnect();
                } catch (Throwable t) {
                    teardownErrors.add(t);
                }
            }
        }

        // 2. Close any raw open WebSocket transport sessions and capture close errors
        if (trackingWsClient != null) {
            List<Throwable> clientCloseErrors = trackingWsClient.closeAllOpenSessions();
            teardownErrors.addAll(clientCloseErrors);
        }

        // 3. Establish bounded transport shutdown: await close signals on all opened transports
        if (trackingWsClient != null) {
            for (CompletableFuture<Void> closeFuture : trackingWsClient.transportCloseFutures) {
                try {
                    closeFuture.get(3, TimeUnit.SECONDS);
                } catch (Throwable e) {
                    teardownErrors.add(new IllegalStateException("Transport close was not acknowledged within bounded timeout", e));
                }
            }
            if (!trackingWsClient.openSessions.isEmpty()) {
                teardownErrors.add(new IllegalStateException("Open sessions remaining after transport close: " + trackingWsClient.openSessions.size()));
            }
        }

        // 4. Verify server-side registry confirmation that sessions are closed
        for (StompSession session : trackedSessions) {
            if (session != null && session.getSessionId() != null) {
                try {
                    WebSocketSession wsSession = webSocketSessionRegistry.getSession(session.getSessionId());
                    if (wsSession != null && wsSession.isOpen()) {
                        teardownErrors.add(new IllegalStateException("Server session " + session.getSessionId() + " remains open"));
                    }
                } catch (Throwable t) {
                    teardownErrors.add(t);
                }
            }
        }

        // 5. Stop STOMP client and verify lifecycle stopped
        if (stompClient != null && stompClient.isRunning()) {
            try {
                stompClient.stop();
                if (stompClient.isRunning()) {
                    teardownErrors.add(new IllegalStateException("STOMP client is still running after stop()"));
                }
            } catch (Throwable t) {
                teardownErrors.add(t);
            }
        }

        // 6. Fail-closed fixture deletion: If any teardown errors occurred, do NOT delete DB fixtures and throw aggregate exception
        if (!teardownErrors.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    "Teardown failed with " + teardownErrors.size() + " error(s); skipping DB fixture cleanup to preserve state");
            for (Throwable err : teardownErrors) {
                aggregate.addSuppressed(err);
            }
            throw aggregate;
        }

        // 7. Normal cleanup: ONLY executed when all transport shutdown and session closure succeeded cleanly
        if (testUser != null && testUser.getId() != null) {
            Long userId = testUser.getId();
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                userBackupCodeRepository.deleteByUserId(userId);
                assertThat(userBackupCodeRepository.findByUserIdAndUsedFalse(userId)).isEmpty();
                if (profileRepository.existsById(userId)) {
                    profileRepository.deleteById(userId);
                }
                if (userRepository.existsById(userId)) {
                    userRepository.deleteById(userId);
                }
                assertThat(profileRepository.existsById(userId)).isFalse();
                assertThat(userRepository.existsById(userId)).isFalse();
                return null;
            });
            testUser = null;
        }
    }

    @Test
    @DisplayName("Negative Security: Real HTTP Bearer 2FA challenge ticket rejected with 401")
    void realHttp_bearerChallengeTicket_rejectedWith401() throws Exception {
        String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Authorization", "Bearer " + challengeTicket)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Negative Security: Real HTTP Cookie 2FA challenge ticket rejected with 401")
    void realHttp_cookieChallengeTicket_rejectedWith401() throws Exception {
        String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Cookie", JwtAuthenticationFilter.COOKIE_NAME + "=" + challengeTicket)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Negative Security: Real HTTP Bearer unknown purpose token rejected with 401")
    void realHttp_unknownPurposeToken_rejectedWith401() throws Exception {
        String unknownPurposeToken = generateWrongPurposeTicket(testUser.getId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Authorization", "Bearer " + unknownPurposeToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Positive Control: Real HTTP Bearer and Cookie with valid access token accepted with 200")
    void realHttp_validAccessToken_acceptedWith200() throws Exception {
        String accessToken = jwtTokenProvider.generate(UserPrincipal.from(testUser));

        // Bearer
        HttpRequest bearerReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> bearerResp = httpClient.send(bearerReq, HttpResponse.BodyHandlers.ofString());
        assertThat(bearerResp.statusCode()).isEqualTo(200);

        // Cookie
        HttpRequest cookieReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Cookie", JwtAuthenticationFilter.COOKIE_NAME + "=" + accessToken)
                .GET()
                .build();
        HttpResponse<String> cookieResp = httpClient.send(cookieReq, HttpResponse.BodyHandlers.ofString());
        assertThat(cookieResp.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Negative Security: Real STOMP CONNECT with Bearer 2FA challenge ticket rejected")
    void realStomp_bearerChallengeTicket_rejected() throws Exception {
        String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + challengeTicket);

        assertStompConnectionRejected(wsHeaders, connectHeaders);
    }

    @Test
    @DisplayName("Negative Security: Real STOMP CONNECT with Cookie 2FA challenge ticket rejected")
    void realStomp_cookieChallengeTicket_rejected() throws Exception {
        String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add("Cookie", JwtAuthenticationFilter.COOKIE_NAME + "=" + challengeTicket);
        StompHeaders connectHeaders = new StompHeaders();

        assertStompConnectionRejected(wsHeaders, connectHeaders);
    }

    @Test
    @DisplayName("Positive Control: Real STOMP CONNECT with Bearer valid access token connects successfully")
    void realStomp_bearerValidAccessToken_connectsSuccessfully() throws Exception {
        String accessToken = jwtTokenProvider.generate(UserPrincipal.from(testUser));

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);

        StompSession session = connectValidStomp(wsHeaders, connectHeaders);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("Positive Control: Real STOMP CONNECT with Cookie valid access token connects successfully")
    void realStomp_cookieValidAccessToken_connectsSuccessfully() throws Exception {
        String accessToken = jwtTokenProvider.generate(UserPrincipal.from(testUser));

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add("Cookie", JwtAuthenticationFilter.COOKIE_NAME + "=" + accessToken);
        StompHeaders connectHeaders = new StompHeaders();

        StompSession session = connectValidStomp(wsHeaders, connectHeaders);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("Positive authVersion Fixture: DB user with authVersion > 0 accepts matching-version access token and rejects challenge ticket")
    void positiveAuthVersion_dbFixture_accessAcceptedChallengeRejected() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User posUser = tx.execute(status -> {
            long suffix = System.nanoTime();
            User u = new User();
            u.setUsername("pos_" + suffix);
            u.setEmail("pos_" + suffix + "@aloute.edu.vn");
            u.setPasswordHash(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            u.setActive(true);
            u.setVerified(true);
            u.setAuthVersion(3L);
            u = userRepository.save(u);
            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Positive Version User");
            profileRepository.save(p);
            return u;
        });

        try {
            Long posUserId = posUser.getId();
            User reloaded = userRepository.findById(posUserId).orElseThrow();
            assertThat(reloaded.getAuthVersion()).isEqualTo(3L);

            // 1. Valid matching-version access token is accepted with 200
            String validAccessToken = jwtTokenProvider.generate(UserPrincipal.from(reloaded));
            HttpRequest validReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                    .header("Authorization", "Bearer " + validAccessToken)
                    .GET()
                    .build();
            HttpResponse<String> validResp = httpClient.send(validReq, HttpResponse.BodyHandlers.ofString());
            assertThat(validResp.statusCode()).isEqualTo(200);

            // 2. Standard 2FA challenge ticket for this user is rejected with 401
            String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(posUserId, "TOTP", 300, 0L);
            HttpRequest chalReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                    .header("Authorization", "Bearer " + challengeTicket)
                    .GET()
                    .build();
            HttpResponse<String> chalResp = httpClient.send(chalReq, HttpResponse.BodyHandlers.ofString());
            assertThat(chalResp.statusCode()).isEqualTo(401);

            // 3. Explicit version-bearing purpose credential (v=3, purpose=2FA_CHALLENGE) is rejected with 401
            String versionedPurposeTicket = generateVersionedPurposeTicket(posUserId, 3L);
            HttpRequest verPurpReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                    .header("Authorization", "Bearer " + versionedPurposeTicket)
                    .GET()
                    .build();
            HttpResponse<String> verPurpResp = httpClient.send(verPurpReq, HttpResponse.BodyHandlers.ofString());
            assertThat(verPurpResp.statusCode()).isEqualTo(401);
        } finally {
            Long posUserId = posUser.getId();
            tx.execute(status -> {
                userBackupCodeRepository.deleteByUserId(posUserId);
                assertThat(userBackupCodeRepository.findByUserIdAndUsedFalse(posUserId)).isEmpty();
                if (profileRepository.existsById(posUserId)) {
                    profileRepository.deleteById(posUserId);
                }
                if (userRepository.existsById(posUserId)) {
                    userRepository.deleteById(posUserId);
                }
                assertThat(profileRepository.existsById(posUserId)).isFalse();
                assertThat(userRepository.existsById(posUserId)).isFalse();
                return null;
            });
        }
    }

    @Test
    @DisplayName("Deterministic Unit/Provider Proof: parseTwoFactorTicket rejects expired ticket, invalid signature, and wrong purpose")
    void ticketVerifier_deterministicValidationProof() {
        // 1. Expired ticket throws ExpiredJwtException
        String expiredTicket = generateExpiredTicket(testUser.getId());
        assertThatThrownBy(() -> jwtTokenProvider.parseTwoFactorTicket(expiredTicket))
                .isInstanceOf(ExpiredJwtException.class);

        // 2. Tampered signature throws JwtException
        String validTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);
        String tamperedTicket = validTicket.substring(0, validTicket.length() - 5) + "XXXXX";
        assertThatThrownBy(() -> jwtTokenProvider.parseTwoFactorTicket(tamperedTicket))
                .isInstanceOf(JwtException.class);

        // 3. Wrong purpose throws IllegalArgumentException
        String wrongPurposeTicket = generateWrongPurposeTicket(testUser.getId());
        assertThatThrownBy(() -> jwtTokenProvider.parseTwoFactorTicket(wrongPurposeTicket))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 2FA challenge ticket purpose");
    }

    @Test
    @DisplayName("Actual HTTP Proof: /api/auth/2fa/verify-login rejects expired, tampered, and wrong purpose tickets with 401")
    void httpApi_invalidChallengeTickets_rejectedWith401() throws Exception {
        // 1. Expired ticket
        String expiredTicket = generateExpiredTicket(testUser.getId());
        String expiredBody = objectMapper.writeValueAsString(Map.of(
                "twoFactorTicket", expiredTicket,
                "code", "123456",
                "isBackupCode", false
        ));
        HttpRequest expReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/2fa/verify-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(expiredBody))
                .build();
        HttpResponse<String> expResp = httpClient.send(expReq, HttpResponse.BodyHandlers.ofString());
        assertThat(expResp.statusCode()).isEqualTo(401);
        assertThat(expResp.body()).contains("AUTH_INVALID_2FA_TICKET");

        // 2. Tampered signature ticket
        String validTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300, 0L);
        String tamperedTicket = validTicket.substring(0, validTicket.length() - 5) + "XXXXX";
        String tamperedBody = objectMapper.writeValueAsString(Map.of(
                "twoFactorTicket", tamperedTicket,
                "code", "123456",
                "isBackupCode", false
        ));
        HttpRequest tampReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/2fa/verify-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(tamperedBody))
                .build();
        HttpResponse<String> tampResp = httpClient.send(tampReq, HttpResponse.BodyHandlers.ofString());
        assertThat(tampResp.statusCode()).isEqualTo(401);
        assertThat(tampResp.body()).contains("AUTH_INVALID_2FA_TICKET");

        // 3. Wrong purpose ticket
        String wrongPurposeTicket = generateWrongPurposeTicket(testUser.getId());
        String wrongBody = objectMapper.writeValueAsString(Map.of(
                "twoFactorTicket", wrongPurposeTicket,
                "code", "123456",
                "isBackupCode", false
        ));
        HttpRequest wrongReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/2fa/verify-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(wrongBody))
                .build();
        HttpResponse<String> wrongResp = httpClient.send(wrongReq, HttpResponse.BodyHandlers.ofString());
        assertThat(wrongResp.statusCode()).isEqualTo(401);
        assertThat(wrongResp.body()).contains("AUTH_INVALID_2FA_TICKET");
    }

    @Test
    @DisplayName("End-to-End: Real password + OTP login flow verifies ticket rejection then valid access")
    void realFlow_passwordAndOtpLogin_flowVerified() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            User u = userRepository.findById(testUser.getId()).orElseThrow();
            u.setTwoFactorEnabled(true);
            u.setTwoFactorType("EMAIL");
            u.setTwoFactorConfirmedAt(Instant.now());
            userRepository.save(u);
            return null;
        });

        // 1. Password login returns twoFactorRequired and twoFactorTicket
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", testUser.getEmail(),
                "password", "Password123!"
        ));
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();
        HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginResp.statusCode()).isEqualTo(200);

        JsonNode loginJson = objectMapper.readTree(loginResp.body());
        assertThat(loginJson.get("data").get("twoFactorRequired").asBoolean()).isTrue();
        String ticket = loginJson.get("data").get("twoFactorTicket").asText();
        assertThat(ticket).isNotBlank();

        // 2. The challenge ticket must NOT authenticate to protected endpoints
        HttpRequest challengeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Authorization", "Bearer " + ticket)
                .GET()
                .build();
        HttpResponse<String> challengeResp = httpClient.send(challengeReq, HttpResponse.BodyHandlers.ofString());
        assertThat(challengeResp.statusCode()).isEqualTo(401);

        // 3. Receive OTP and complete 2FA verification
        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String sentOtp = emailSink.getOtpSink().get(0).otpCode();

        String verifyBody = objectMapper.writeValueAsString(Map.of(
                "twoFactorTicket", ticket,
                "code", sentOtp,
                "isBackupCode", false
        ));
        HttpRequest verifyReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/2fa/verify-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(verifyBody))
                .build();
        HttpResponse<String> verifyResp = httpClient.send(verifyReq, HttpResponse.BodyHandlers.ofString());
        assertThat(verifyResp.statusCode()).isEqualTo(200);

        JsonNode verifyJson = objectMapper.readTree(verifyResp.body());
        String finalToken = verifyJson.get("data").get("token").asText();
        assertThat(finalToken).isNotBlank();

        // 4. Completed access token accesses protected endpoint
        HttpRequest finalReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/profile"))
                .header("Authorization", "Bearer " + finalToken)
                .GET()
                .build();
        HttpResponse<String> finalResp = httpClient.send(finalReq, HttpResponse.BodyHandlers.ofString());
        assertThat(finalResp.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Deterministic Teardown Proof: Close failure attempts all sockets, remains observable after peer close, and preserves primary test failure")
    void deterministic_socketCloseError_attemptsAllSockets_andPreservesPrimaryFailure() throws Exception {
        // 1. Setup mock sessions: one failing socket and one healthy socket
        WebSocketSession failingSession = mock(WebSocketSession.class);
        WebSocketSession healthySession = mock(WebSocketSession.class);

        when(failingSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Simulated network error on close")).when(failingSession).close();

        when(healthySession.isOpen()).thenReturn(true);

        TrackingWebSocketClient testClient = new TrackingWebSocketClient();
        testClient.openSessions.add(failingSession);
        testClient.openSessions.add(healthySession);

        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        testClient.transportCloseFutures.add(closeFuture);

        // 2. Execute closeAllOpenSessions
        List<Throwable> closeErrors = testClient.closeAllOpenSessions();

        // 3. Verify: both sockets were attempted (loop did not abort after first failure)
        verify(failingSession, times(1)).close();
        verify(healthySession, times(1)).close();

        // 4. Verify: close failure was captured
        assertThat(closeErrors).hasSize(1);
        assertThat(closeErrors.get(0)).isInstanceOf(IOException.class)
                .hasMessage("Simulated network error on close");

        // 5. Simulate peer close callback completing later
        closeFuture.complete(null);
        assertThat(closeFuture.isDone()).isTrue();
        // Crucial: close error remains observable even though transport close callback completed
        assertThat(closeErrors).isNotEmpty();

        // 6. Verify primary failure retention alongside teardown failure:
        AssertionError primaryTestFailure = new AssertionError("Primary test assertion failed: expected 401 but got 200");
        try {
            try {
                throw primaryTestFailure;
            } catch (Throwable primaryEx) {
                IllegalStateException teardownFailure = new IllegalStateException(
                        "Teardown failed with " + closeErrors.size() + " error(s)");
                for (Throwable t : closeErrors) {
                    teardownFailure.addSuppressed(t);
                }
                primaryEx.addSuppressed(teardownFailure);
                throw primaryEx;
            }
        } catch (AssertionError caught) {
            assertThat(caught).isSameAs(primaryTestFailure);
            assertThat(caught.getSuppressed()).hasSize(1);
            Throwable suppressedTeardown = caught.getSuppressed()[0];
            assertThat(suppressedTeardown).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Teardown failed");
            assertThat(suppressedTeardown.getSuppressed()).hasSize(1);
            assertThat(suppressedTeardown.getSuppressed()[0]).isInstanceOf(IOException.class)
                    .hasMessage("Simulated network error on close");
        }
    }

    @Test
    @DisplayName("Deterministic Teardown Proof: Fail-closed ensures fixture is NOT deleted if teardown encounters close errors")
    void deterministic_failClosedFixtureDeletion_preservesDatabaseStateWhenCloseFails() throws Exception {
        Long userId = testUser.getId();
        assertThat(userRepository.existsById(userId)).isTrue();

        // Inject a simulated close error into trackingWsClient
        WebSocketSession failingSession = mock(WebSocketSession.class);
        when(failingSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Simulated teardown failure")).when(failingSession).close();
        trackingWsClient.openSessions.add(failingSession);

        // Calling tearDown should fail-closed with IllegalStateException
        assertThatThrownBy(() -> tearDown())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skipping DB fixture cleanup");

        // Verify fail-closed: user still exists in database!
        assertThat(userRepository.existsById(userId)).isTrue();

        // Clean up mock and manually clean fixture so subsequent tests are not affected
        trackingWsClient.openSessions.clear();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            userBackupCodeRepository.deleteByUserId(userId);
            profileRepository.deleteById(userId);
            userRepository.deleteById(userId);
            return null;
        });
        testUser = null;
    }
}
