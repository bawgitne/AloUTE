package com.aloute.integration;

import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.entity.PasswordResetToken;
import com.aloute.security.authorization.Role;
import com.aloute.entity.User;
import com.aloute.common.exception.AppException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.repository.PasswordResetTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.PasswordResetService;
import com.aloute.service.email.TestEmailSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.reset-request-cooldown-seconds=5",
        "aloute.auth.reset-max-requests-per-window=5",
        "aloute.app.base-url=http://localhost:8080"
})
class PasswordResetAndSessionRevocationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private TestEmailSink emailSink;
    @Autowired
    @Qualifier("emailExecutor")
    private Executor emailExecutor;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        emailSink.clearSink();
        emailSink.setDeliveryHoldLatch(null);
        emailSink.setDeliveryStartedLatch(null);
    }

    @Test
    void fullPasswordResetAndAllSessionRevocationLifecycle() throws Exception {
        long suffix = System.currentTimeMillis();
        String username = "usr_" + suffix;
        String email = "usr_" + suffix + "@example.com";
        String initialPassword = "oldPassword123";
        String newPassword = "newSecurePassword456";

        // 1. Register a test user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.aloute.dto.request.RegisterRequest(
                                username, email, initialPassword))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getAuthVersion()).isEqualTo(0L);

        // 2. Login to get Session 1 Token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.aloute.dto.request.LoginRequest(
                                email, initialPassword))))
                .andExpect(status().isOk())
                .andReturn();

        String session1Token = extractToken(loginResult);
        assertThat(session1Token).isNotBlank();

        // Verify Session 1 Token can access /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie("aloute_access_token", session1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // 3. Security gate: POST /api/auth/forgot-password without CSRF MUST fail with 403 Forbidden
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))))
                .andExpect(status().isForbidden());

        // 4. POST /api/auth/forgot-password with valid CSRF succeeds (200 OK)
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Cooldown / Rate Limiting: Immediate second request fails with 429 Too Many Requests
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))))
                .andExpect(status().isTooManyRequests());

        // 6. Non-existent email request returns generic 200 without leaking account non-existence
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("nonexistent_user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 7. Verify email sink received the reset email with trusted fragment URL
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
        List<TestEmailSink.SentEmail> sentEmails = emailSink.getEmailSink();
        assertThat(sentEmails).hasSize(1);
        TestEmailSink.SentEmail sent = sentEmails.get(0);
        assertThat(sent.toEmail()).isEqualTo(email);
        assertThat(sent.resetUrl()).startsWith("http://localhost:8080/reset-password#token=");

        // Extract raw token from URL fragment
        String rawToken = sent.resetUrl().substring(sent.resetUrl().indexOf("#token=") + 7);
        assertThat(rawToken).hasSize(64);

        // 8. Security gate: POST /api/auth/reset-password without CSRF MUST fail with 403 Forbidden
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                rawToken, newPassword, newPassword))))
                .andExpect(status().isForbidden());

        // 9. Validation: Mismatched password and confirmPassword fails with 422
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                rawToken, newPassword, "differentConfirmPw123"))))
                .andExpect(status().isUnprocessableEntity());

        // 10. Invalid token fails with 400 Bad Request
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                "0000000000000000000000000000000000000000000000000000000000000000",
                                newPassword, newPassword))))
                .andExpect(status().isBadRequest());

        // 11. Execute successful password reset with valid token and CSRF
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                rawToken, newPassword, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 12. Token is single-use: Reusing the same token fails with 400 Bad Request
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                rawToken, "anotherPassword789", "anotherPassword789"))))
                .andExpect(status().isBadRequest());

        // 13. Durable authVersion increment verified
        User updatedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(updatedUser.getAuthVersion()).isEqualTo(1L);

        // 14. CRITICAL SECURITY ASSERTION: Session 1 Token (minted before reset) is immediately REVOKED
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie("aloute_access_token", session1Token)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + session1Token))
                .andExpect(status().isUnauthorized());

        // 15. Old password no longer works
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.aloute.dto.request.LoginRequest(
                                email, initialPassword))))
                .andExpect(status().isUnauthorized());

        // 16. New password works and issues Session 2 Token with new authVersion
        MvcResult login2Result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.aloute.dto.request.LoginRequest(
                                email, newPassword))))
                .andExpect(status().isOk())
                .andReturn();

        String session2Token = extractToken(login2Result);
        assertThat(session2Token).isNotBlank();

        // Session 2 token successfully authenticates
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie("aloute_access_token", session2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // 17. Verify SSR page endpoints
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("forgot-password-form")));

        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reset-password-form")));
    }

    @Test
    void deterministicExpiryBoundaryCheck() {
        PasswordResetToken token = new PasswordResetToken();
        Instant boundary = Instant.parse("2026-10-19T20:00:00Z");
        token.setExpiresAt(boundary);

        // Before expiry boundary -> NOT expired
        assertThat(token.isExpired(boundary.minusMillis(1))).isFalse();

        // Exactly at boundary -> EXPIRED (now >= expiresAt)
        assertThat(token.isExpired(boundary)).isTrue();

        // After boundary -> EXPIRED
        assertThat(token.isExpired(boundary.plusMillis(1))).isTrue();
    }

    @Test
    void abuseLimitsApplyEquallyToUnknownAndInactiveEmails() throws Exception {
        String unknownEmail = "unknown_abuse_" + System.currentTimeMillis() + "@example.com";

        // 1. First request succeeds silently
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(unknownEmail))))
                .andExpect(status().isOk());

        // 2. Immediate second request hits cooldown -> 429
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(unknownEmail))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void inactiveUserPasswordResetRejected() {
        long suffix = System.currentTimeMillis();
        User inactiveUser = new User();
        inactiveUser.setUsername("inactive_" + suffix);
        inactiveUser.setEmail("inactive_" + suffix + "@example.com");
        inactiveUser.setPasswordHash(passwordEncoder.encode("password123"));
        inactiveUser.setActive(false);
        inactiveUser.setRole(Role.USER);
        inactiveUser = userRepository.saveAndFlush(inactiveUser);

        // Initiate forgot password for inactive user -> generic silent success, no email sent
        emailSink.clearSink();
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(inactiveUser.getEmail()));
        assertThat(emailSink.getEmailSink()).isEmpty();

        // Even if a token somehow existed for inactive user, resetPassword MUST reject
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(inactiveUser);
        token.setTokenHash("testhash123456789012345678901234567890123456789012345678901234");
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        resetTokenRepository.saveAndFlush(token);

        ResetPasswordRequest resetReq = new ResetPasswordRequest("testhash123456789012345678901234567890123456789012345678901234", "newPassword123", "newPassword123");
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetReq))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    void concurrentConsumptionAllowsExactlyOneSuccess() throws Exception {
        long suffix = System.currentTimeMillis();
        User user = new User();
        user.setUsername("concurrent_" + suffix);
        user.setEmail("concurrent_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        user.setActive(true);
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);

        // Initiate reset
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(user.getEmail()));
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
        List<TestEmailSink.SentEmail> sent = emailSink.getEmailSink();
        assertThat(sent).isNotEmpty();
        String rawToken = sent.get(sent.size() - 1).resetUrl().split("#token=")[1];

        // Concurrently attempt consumption from 2 threads
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken, "concurrentNewPw" + idx, "concurrentNewPw" + idx));
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }

    @Test
    void issuanceVersusReplacementRejectsStaleToken() {
        long suffix = System.currentTimeMillis();
        User user = new User();
        user.setUsername("replace_" + suffix);
        user.setEmail("replace_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        user.setActive(true);
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);

        // Issue Token 1
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(user.getEmail()));
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
        String token1 = emailSink.getEmailSink().get(0).resetUrl().split("#token=")[1];

        // Wait beyond cooldown to issue Token 2
        try {
            Thread.sleep(5100);
        } catch (InterruptedException ignored) {}

        // Issue Token 2 (replaces Token 1)
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(user.getEmail()));
        assertThat(emailSink.awaitEmailCount(2, 2, TimeUnit.SECONDS)).isTrue();
        String token2 = emailSink.getEmailSink().get(1).resetUrl().split("#token=")[1];
        assertThat(token1).isNotEqualTo(token2);

        // Attempting to consume stale Token 1 MUST fail and NOT delete Token 2
        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new ResetPasswordRequest(token1, "newPassword123", "newPassword123")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        // Token 2 must still exist and be valid
        assertThat(resetTokenRepository.findByUser(user)).isPresent();

        // Consuming newer Token 2 MUST succeed
        passwordResetService.resetPassword(
                new ResetPasswordRequest(token2, "newPassword123", "newPassword123"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getAuthVersion()).isEqualTo(1L);
        assertThat(resetTokenRepository.findByUser(user)).isEmpty();
    }

    @Test
    void concurrentObsoleteTokenVsReplacementTokenRejectsObsoleteAndPreservesReplacement() throws Exception {
        long suffix = System.currentTimeMillis();
        User user = new User();
        user.setUsername("coord_obs_" + suffix);
        user.setEmail("coord_obs_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        user.setActive(true);
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);
        final String userEmail = user.getEmail();
        final Long testUserId = user.getId();

        // Issue token A
        emailSink.clearSink();
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(userEmail));
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
        String tokenA = emailSink.getEmailSink().get(0).resetUrl().split("#token=")[1];

        // Wait beyond cooldown to allow token B issuance
        Thread.sleep(5100);

        // Coordinate Thread 1 (reset with obsolete token A) and Thread 2 (issuance of replacement token B)
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger resetSuccess = new AtomicInteger(0);
        AtomicInteger resetFailed = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> tokenBRef = new java.util.concurrent.atomic.AtomicReference<>();

        // Thread 2 issues replacement token B under barrier
        Future<?> fIssuance = executor.submit(() -> {
            try {
                barrier.await();
                emailSink.clearSink();
                passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(userEmail));
                assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
                String tokenB = emailSink.getEmailSink().get(0).resetUrl().split("#token=")[1];
                tokenBRef.set(tokenB);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Thread 1 attempts reset with obsolete token A
        Future<?> fReset = executor.submit(() -> {
            try {
                barrier.await();
                fIssuance.get(5, TimeUnit.SECONDS);
                passwordResetService.resetPassword(new ResetPasswordRequest(tokenA, "newPassword123", "newPassword123"));
                resetSuccess.incrementAndGet();
            } catch (Exception e) {
                resetFailed.incrementAndGet();
            }
        });

        fReset.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Obsolete token A reset MUST fail
        assertThat(resetFailed.get()).isEqualTo(1);
        assertThat(resetSuccess.get()).isEqualTo(0);

        // Replacement token B MUST still be present in repository
        assertThat(resetTokenRepository.findByUser(user)).isPresent();

        // Resetting with replacement token B MUST succeed
        String tokenB = tokenBRef.get();
        assertThat(tokenB).isNotBlank();
        passwordResetService.resetPassword(new ResetPasswordRequest(tokenB, "brandNewPass456", "brandNewPass456"));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getAuthVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches("brandNewPass456", updatedUser.getPasswordHash())).isTrue();
    }

    @Test
    void issuanceWaitingBehindResetBindsTokenToCurrentEpoch() throws Exception {
        long suffix = System.currentTimeMillis();
        User user = new User();
        user.setUsername("epoch_bind_" + suffix);
        user.setEmail("epoch_bind_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("initialPass123"));
        user.setActive(true);
        user.setRole(Role.USER);
        user.setAuthVersion(0L);
        user = userRepository.saveAndFlush(user);
        final String userEmail = user.getEmail();
        final Long testUserId = user.getId();

        // Issue a reset token at authVersion = 0
        emailSink.clearSink();
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(userEmail));
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
        String rawToken = emailSink.getEmailSink().get(0).resetUrl().split("#token=")[1];

        // Wait beyond cooldown so Thread 2 can issue a new token
        Thread.sleep(5100);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: performs reset (holds user lock, increments authVersion to 1L)
        Future<?> fReset = executor.submit(() -> {
            try {
                barrier.await();
                passwordResetService.resetPassword(new ResetPasswordRequest(
                        rawToken, "resetPassword123", "resetPassword123"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Thread 2: attempts issuance concurrently (waits behind reset user lock)
        Future<?> fIssuance = executor.submit(() -> {
            try {
                barrier.await();
                Thread.sleep(50);
                emailSink.clearSink();
                passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(userEmail));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        fReset.get(10, TimeUnit.SECONDS);
        fIssuance.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Await completion of asynchronous delivery before reading or ending test
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();

        // The newly issued token must be bound to authVersion = 1L (the updated epoch, NOT 0L)
        User freshUser = userRepository.findById(testUserId).orElseThrow();
        PasswordResetToken newlyIssued = resetTokenRepository.findByUser(freshUser).orElseThrow();
        assertThat(newlyIssued.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void clearedLimiterParityBetweenKnownAndUnknownEmails() throws Exception {
        long suffix = System.currentTimeMillis();
        User knownUser = new User();
        knownUser.setUsername("known_" + suffix);
        knownUser.setEmail("known_" + suffix + "@example.com");
        knownUser.setPasswordHash(passwordEncoder.encode("password123"));
        knownUser.setActive(true);
        knownUser.setRole(Role.USER);
        userRepository.saveAndFlush(knownUser);

        // Clear in-process limiter state using reflection (simulating clean server restart)
        Object historyMap = org.springframework.test.util.ReflectionTestUtils.getField(passwordResetService, "emailRequestHistory");
        if (historyMap instanceof java.util.Map map) {
            map.clear();
        }

        String unknownEmail = "unknown_" + suffix + "@example.com";
        String knownEmail = knownUser.getEmail();

        // 1. First request after restart: BOTH known and unknown return 200 OK (no enumeration discrepancy)
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(unknownEmail))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(knownEmail))))
                .andExpect(status().isOk());

        // 2. Immediate second request within cooldown: BOTH known and unknown return 429 Too Many Requests
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(unknownEmail))))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(knownEmail))))
                .andExpect(status().isTooManyRequests());

        // Await completion of asynchronous delivery from the first known-email request before ending test
        assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rateLimiterHardCapPreventsUnboundedMemoryGrowth() {
        Object historyMapObj = org.springframework.test.util.ReflectionTestUtils.getField(passwordResetService, "emailRequestHistory");
        if (historyMapObj instanceof java.util.Map map) {
            map.clear();
            for (int i = 0; i < 10000; i++) {
                java.util.Deque<Instant> dq = new java.util.ArrayDeque<>();
                dq.add(Instant.now());
                map.put("filler" + i + "@example.com", dq);
            }
        }

        assertThatThrownBy(() -> passwordResetService.initiateForgotPassword(
                new ForgotPasswordRequest("brand_new_at_capacity@example.com")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

        if (historyMapObj instanceof java.util.Map map) {
            map.clear();
        }
    }

    @Test
    void barrierConcurrentDistinctEmailsAtNearCapacityEnforcesHardCapAndReclaimsStale() throws Exception {
        Object historyMapObj = org.springframework.test.util.ReflectionTestUtils.getField(passwordResetService, "emailRequestHistory");
        assertThat(historyMapObj).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, java.util.Deque<Instant>> map = (java.util.Map<String, java.util.Deque<Instant>>) historyMapObj;

        map.clear();
        // Fill to near-capacity: 9,990 active entries, leaving exactly 10 slots
        for (int i = 0; i < 9990; i++) {
            java.util.Deque<Instant> dq = new java.util.ArrayDeque<>();
            dq.add(Instant.now());
            map.put("active_filler_" + i + "@example.com", dq);
        }

        int threadCount = 30; // 30 concurrent threads requesting distinct new emails
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threadCount);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.atomic.AtomicInteger admittedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejectedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<String> admittedEmails = new java.util.concurrent.CopyOnWriteArrayList<>();

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String distinctEmail = "concurrent_new_" + i + "@example.com";
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(distinctEmail));
                    admittedCount.incrementAndGet();
                    admittedEmails.add(distinctEmail);
                } catch (AppException ae) {
                    if (ae.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED) {
                        rejectedCount.incrementAndGet();
                    } else {
                        throw new RuntimeException("Unexpected error code", ae);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (java.util.concurrent.Future<?> f : futures) {
            f.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        executor.shutdown();

        // 1. Assert exactly 10 admitted and 20 rejected
        assertThat(admittedCount.get()).isEqualTo(10);
        assertThat(rejectedCount.get()).isEqualTo(20);

        // 2. Assert hard bound: map size is EXACTLY 10,000, never exceeded
        assertThat(map.size()).isEqualTo(10000);

        // 3. Assert no lost per-key limits for admitted emails: immediate second request hits cooldown 429
        String oneAdmitted = admittedEmails.get(0);
        assertThatThrownBy(() -> passwordResetService.initiateForgotPassword(new ForgotPasswordRequest(oneAdmitted)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED)
                .hasMessageContaining("Vui lòng chờ");

        // 4. Assert exact-capacity stale entries are reclaimed:
        // Make 5 existing entries stale (older than 15 minutes)
        for (int i = 0; i < 5; i++) {
            java.util.Deque<Instant> staleDq = new java.util.ArrayDeque<>();
            staleDq.add(Instant.now().minus(java.time.Duration.ofMinutes(20)));
            map.put("active_filler_" + i + "@example.com", staleDq);
        }
        // At exact capacity (10,000) with 5 stale entries
        assertThat(map.size()).isEqualTo(10000);

        // A new distinct email request should trigger stale eviction, admit the new key, and succeed
        passwordResetService.initiateForgotPassword(new ForgotPasswordRequest("stale_reclaimed_key@example.com"));

        // After evicting 5 stale entries and adding 1 new email, size must be 9,996
        assertThat(map.size()).isEqualTo(9996);
        assertThat(map.containsKey("stale_reclaimed_key@example.com")).isTrue();

        map.clear();
    }

    @Test
    void heldSmtpDeliveryReturnsWithoutWaitingAndIsIndistinguishableForKnownVsUnknown() throws Exception {
        long suffix = System.currentTimeMillis();
        User knownUser = new User();
        knownUser.setUsername("smtp_hold_" + suffix);
        knownUser.setEmail("smtp_hold_" + suffix + "@example.com");
        knownUser.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        knownUser.setActive(true);
        knownUser.setRole(Role.USER);
        userRepository.saveAndFlush(knownUser);

        CountDownLatch holdLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(1);
        emailSink.setDeliveryHoldLatch(holdLatch);
        emailSink.setDeliveryStartedLatch(startedLatch);
        emailSink.setDeliveryCompletedLatch(completedLatch);

        try {
            long t0 = System.currentTimeMillis();
            MvcResult knownResult = mockMvc.perform(post("/api/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(knownUser.getEmail()))))
                    .andExpect(status().isOk())
                    .andReturn();
            long knownDuration = System.currentTimeMillis() - t0;

            // Delivery is held on background executor, so HTTP call must return immediately (<1000ms)
            assertThat(knownDuration).isLessThan(1000);

            // Await delivery started latch AFTER HTTP response returns and BEFORE releasing the hold:
            // This proves delivery was actively blocked in the background during the HTTP call.
            assertThat(startedLatch.await(2, TimeUnit.SECONDS)).isTrue();

            long t1 = System.currentTimeMillis();
            MvcResult unknownResult = mockMvc.perform(post("/api/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("unknown_" + suffix + "@example.com"))))
                    .andExpect(status().isOk())
                    .andReturn();
            long unknownDuration = System.currentTimeMillis() - t1;

            assertThat(unknownDuration).isLessThan(1000);

            // Responses must be completely indistinguishable in status, content type, and body
            assertThat(knownResult.getResponse().getStatus()).isEqualTo(unknownResult.getResponse().getStatus());
            assertThat(knownResult.getResponse().getContentType()).isEqualTo(unknownResult.getResponse().getContentType());
            assertThat(knownResult.getResponse().getContentAsString()).isEqualTo(unknownResult.getResponse().getContentAsString());

            // Assert unknown address caused no email-sink invocation
            assertThat(emailSink.getInvocationCount()).isEqualTo(1);

            // Release hold latch and verify only the known user received an email
            holdLatch.countDown();
            assertThat(completedLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(emailSink.awaitEmailCount(1, 2, TimeUnit.SECONDS)).isTrue();
            List<TestEmailSink.SentEmail> sent = emailSink.getEmailSink();
            assertThat(sent).hasSize(1);
            assertThat(sent.get(0).toEmail()).isEqualTo(knownUser.getEmail());
            assertThat(emailSink.getInvocationCount()).isEqualTo(1);
        } finally {
            holdLatch.countDown();
            try {
                completedLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            emailSink.clearSink();
        }
    }

    @Test
    void emailSinkFailurePreservesGenericAntiEnumerationResponse() throws Exception {
        long suffix = System.currentTimeMillis();
        User knownUser = new User();
        knownUser.setUsername("smtp_fail_" + suffix);
        knownUser.setEmail("smtp_fail_" + suffix + "@example.com");
        knownUser.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        knownUser.setActive(true);
        knownUser.setRole(Role.USER);
        userRepository.saveAndFlush(knownUser);

        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(1);
        emailSink.setDeliveryStartedLatch(startedLatch);
        emailSink.setDeliveryCompletedLatch(completedLatch);
        emailSink.setDeliveryFailureException(new RuntimeException("Simulated SMTP transmission error"));

        try {
            long t0 = System.currentTimeMillis();
            MvcResult knownResult = mockMvc.perform(post("/api/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(knownUser.getEmail()))))
                    .andExpect(status().isOk())
                    .andReturn();
            long knownDuration = System.currentTimeMillis() - t0;

            // Delivery fails asynchronously on executor, so HTTP call must return immediately (<1000ms)
            assertThat(knownDuration).isLessThan(1000);

            // Wait for background delivery attempt to run, throw, and complete
            assertThat(startedLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(completedLatch.await(2, TimeUnit.SECONDS)).isTrue();

            long t1 = System.currentTimeMillis();
            MvcResult unknownResult = mockMvc.perform(post("/api/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("unknown_" + suffix + "@example.com"))))
                    .andExpect(status().isOk())
                    .andReturn();
            long unknownDuration = System.currentTimeMillis() - t1;

            assertThat(unknownDuration).isLessThan(1000);

            // Responses must remain identical generic 200 without waiting
            assertThat(knownResult.getResponse().getStatus()).isEqualTo(unknownResult.getResponse().getStatus());
            assertThat(knownResult.getResponse().getContentType()).isEqualTo(unknownResult.getResponse().getContentType());
            assertThat(knownResult.getResponse().getContentAsString()).isEqualTo(unknownResult.getResponse().getContentAsString());

            // Unknown address triggers no send; invocation count is exactly 1 (for known user)
            assertThat(emailSink.getInvocationCount()).isEqualTo(1);
            assertThat(emailSink.getEmailSink()).isEmpty();
        } finally {
            try {
                completedLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            emailSink.clearSink();
        }
    }

    @Test
    void executorRejectionPreservesGenericAntiEnumerationResponse() throws Exception {
        long suffix = System.currentTimeMillis();
        User knownUser = new User();
        knownUser.setUsername("smtp_reject_" + suffix);
        knownUser.setEmail("smtp_reject_" + suffix + "@example.com");
        knownUser.setPasswordHash(passwordEncoder.encode("oldPassword123"));
        knownUser.setActive(true);
        knownUser.setRole(Role.USER);
        userRepository.saveAndFlush(knownUser);

        CountDownLatch blockerLatch = new CountDownLatch(1);
        List<Future<?>> blockers = new ArrayList<>();
        try {
            // Fill core (2) + max (2 additional) + queue (100) = 104 tasks to saturate executor
            for (int i = 0; i < 104; i++) {
                blockers.add(CompletableFuture.runAsync(() -> {
                    try {
                        blockerLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}
                }, emailExecutor));
            }

            // Saturated executor triggers AbortPolicy (RejectedExecutionException).
            // Service must catch rejection and preserve generic 200 anti-enumeration response.
            mockMvc.perform(post("/api/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(knownUser.getEmail()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            blockerLatch.countDown();
            for (Future<?> f : blockers) {
                try {
                    f.get(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
        }
    }

    private String extractToken(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow(() -> new IllegalStateException("Missing aloute_access_token cookie"));
    }
}
