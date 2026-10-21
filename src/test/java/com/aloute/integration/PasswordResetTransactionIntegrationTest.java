package com.aloute.integration;

import com.aloute.common.exception.AppException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.entity.PasswordResetToken;
import com.aloute.entity.User;
import com.aloute.repository.PasswordResetTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.PasswordResetService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.reset-request-cooldown-seconds=5",
        "aloute.auth.reset-max-requests-per-window=5",
        "aloute.app.base-url=http://localhost:8080",
        "spring.datasource.url=jdbc:h2:mem:reset_transaction_regression;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=1000"
})
class PasswordResetTransactionIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordResetService passwordResetService;
    @SpyBean private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private HikariDataSource dataSource;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final List<Long> ownedUserIds = new ArrayList<>();
    private final List<ExecutorService> ownedExecutors = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixtures() {
        assertAll("owned test resources", () -> {
            for (ExecutorService executor : ownedExecutors) {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }, () -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (Long userId : ownedUserIds) {
                    userRepository.findById(userId).ifPresent(user -> {
                        resetTokenRepository.deleteByUser(user);
                        userRepository.delete(user);
                    });
                }
            });
            assertThat(userRepository.findAllById(ownedUserIds)).isEmpty();
        });
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private User createTestUser(String prefix, long authVersion) {
        long suffix = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(10000);
        User user = new User();
        user.setUsername(prefix + "_" + suffix);
        user.setEmail(prefix + "_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("InitialPassword123!"));
        user.setActive(true);
        user.setRole(Role.USER);
        user.setAuthVersion(authVersion);
        User saved = userRepository.saveAndFlush(user);
        ownedUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("1. Valid token resets password, increments authVersion, and consumes token")
    void validTokenResetsPasswordAndConsumesToken() {
        User user = createTestUser("valid_user", 0L);
        String rawToken = "1111111111111111111111111111111111111111111111111111111111111111";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token = resetTokenRepository.saveAndFlush(token);
        Long tokenId = token.getId();

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewValidPass123!", "NewValidPass123!");
        passwordResetService.resetPassword(req);

        // Verify password updated and authVersion incremented
        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewValidPass123!", refreshedUser.getPasswordHash())).isTrue();
        assertThat(refreshedUser.getAuthVersion()).isEqualTo(1L);

        // Verify token consumed/deleted from database
        assertThat(resetTokenRepository.findById(tokenId)).isEmpty();
    }

    @Test
    @DisplayName("2. Expired token throws BAD_REQUEST after committing only token cleanup")
    void expiredTokenThrowsBadRequestAndPurgesToken() {
        User user = createTestUser("expired_user", 0L);
        String rawToken = "2222222222222222222222222222222222222222222222222222222222222222";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().minus(Duration.ofMinutes(5)));
        token = resetTokenRepository.saveAndFlush(token);
        Long tokenId = token.getId();

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewPass1234!", "NewPass1234!");

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        assertThat(resetTokenRepository.findById(tokenId))
                .as("Expired token cleanup must commit despite the rejection response")
                .isEmpty();

        // User password must remain untouched
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getAuthVersion()).isEqualTo(0L);
        assertThat(passwordEncoder.matches("InitialPassword123!", unchangedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("3. Reused token (already consumed/deleted) is rejected with BAD_REQUEST")
    void reusedTokenIsRejected() {
        User user = createTestUser("reused_user", 0L);
        String rawToken = "3333333333333333333333333333333333333333333333333333333333333333";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        resetTokenRepository.saveAndFlush(token);

        // First use succeeds
        ResetPasswordRequest req1 = new ResetPasswordRequest(rawToken, "PasswordOne123!", "PasswordOne123!");
        passwordResetService.resetPassword(req1);

        // Second use (reused token) fails
        ResetPasswordRequest req2 = new ResetPasswordRequest(rawToken, "PasswordTwo123!", "PasswordTwo123!");
        assertThatThrownBy(() -> passwordResetService.resetPassword(req2))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("4. Replaced token is rejected with BAD_REQUEST while newest active token is preserved")
    void replacedTokenIsRejectedAndNewestPreserved() {
        User user = createTestUser("replaced_user", 0L);
        String oldRawToken = "4444444444444444444444444444444444444444444444444444444444444444";
        String oldTokenHash = sha256Hex(oldRawToken);

        String newRawToken = "5555555555555555555555555555555555555555555555555555555555555555";
        String newTokenHash = sha256Hex(newRawToken);

        // Simulate user requested a new forgot-password link which replaced the old hash in DB
        PasswordResetToken activeToken = new PasswordResetToken();
        activeToken.setUser(user);
        activeToken.setTokenHash(newTokenHash);
        activeToken.setAuthVersion(0L);
        activeToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(20)));
        activeToken = resetTokenRepository.saveAndFlush(activeToken);
        Long activeTokenId = activeToken.getId();

        // Trying to use old token that is not in DB at all fails fast
        ResetPasswordRequest oldReq = new ResetPasswordRequest(oldRawToken, "SomeNewPass123!", "SomeNewPass123!");
        assertThatThrownBy(() -> passwordResetService.resetPassword(oldReq))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        // The active newest token MUST still exist in database
        assertThat(resetTokenRepository.findById(activeTokenId)).isPresent();

        // Using the active newest token succeeds
        ResetPasswordRequest newReq = new ResetPasswordRequest(newRawToken, "SomeNewPass123!", "SomeNewPass123!");
        passwordResetService.resetPassword(newReq);
        assertThat(resetTokenRepository.findById(activeTokenId)).isEmpty();
    }

    @Test
    @DisplayName("5. AuthVersion-obsolete token throws BAD_REQUEST after committing only token cleanup")
    void authVersionObsoleteTokenThrowsBadRequestAndPurgesToken() {
        // User is at authVersion = 2L (e.g., logged out all sessions or reset earlier)
        User user = createTestUser("obsolete_user", 2L);
        String rawToken = "6666666666666666666666666666666666666666666666666666666666666666";
        String tokenHash = sha256Hex(rawToken);

        // Token was minted when authVersion was 0L (stale/obsolete token)
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L); // Mismatched with user's 2L
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token = resetTokenRepository.saveAndFlush(token);
        Long tokenId = token.getId();

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewPass1234!", "NewPass1234!");

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        assertThat(resetTokenRepository.findById(tokenId))
                .as("Obsolete token cleanup must commit despite the rejection response")
                .isEmpty();

        // User authVersion and password must remain untouched
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getAuthVersion()).isEqualTo(2L);
        assertThat(passwordEncoder.matches("InitialPassword123!", unchangedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("6. Rollback safety: validation failure preserves token and leaves user password unchanged")
    void rollbackSafetyOnValidationFailure() {
        User user = createTestUser("rollback_user", 0L);
        String rawToken = "7777777777777777777777777777777777777777777777777777777777777777";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token = resetTokenRepository.saveAndFlush(token);
        Long tokenId = token.getId();

        // Mismatched confirm password
        ResetPasswordRequest reqMismatch = new ResetPasswordRequest(rawToken, "NewPass1234!", "DifferentPass1234!");
        assertThatThrownBy(() -> passwordResetService.resetPassword(reqMismatch))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        // Token must still exist (retryable)
        assertThat(resetTokenRepository.findById(tokenId)).isPresent();

        // Too short password (< 8 chars)
        ResetPasswordRequest reqShort = new ResetPasswordRequest(rawToken, "short", "short");
        assertThatThrownBy(() -> passwordResetService.resetPassword(reqShort))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        assertThat(resetTokenRepository.findById(tokenId)).isPresent();

        // Password remains unchanged
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("InitialPassword123!", unchangedUser.getPasswordHash())).isTrue();
        assertThat(unchangedUser.getAuthVersion()).isEqualTo(0L);
    }

    @Test
    void failureAfterFlushingMutationsRollsBackPasswordEpochAndConsumption() {
        User user = createTestUser("flush_rollback", 0L);
        Long userId = user.getId();
        String originalHash = user.getPasswordHash();
        String rawToken = UUID.randomUUID().toString().replace("-", "").repeat(2);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plusSeconds(300));
        Long tokenId = resetTokenRepository.saveAndFlush(token).getId();
        String newPassword = "RollbackAfterFlush123!";
        AtomicBoolean flushedMutations = new AtomicBoolean();

        doAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    entityManager.flush();
                    assertThat(jdbcTemplate.queryForObject("select auth_version from users where id = ?",
                            Long.class, userId)).isEqualTo(1L);
                    String changedHash = jdbcTemplate.queryForObject(
                            "select password_hash from users where id = ?", String.class, userId);
                    assertThat(passwordEncoder.matches(newPassword, changedHash)).isTrue();
                    assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from password_reset_tokens where id = ?", Long.class, tokenId))
                            .isZero();
                    flushedMutations.set(true);
                    throw new AppException(ErrorCode.BAD_REQUEST, "simulated failure after flush");
                }
            });
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(newPassword);

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new ResetPasswordRequest(rawToken, newPassword, newPassword)))
                .isInstanceOf(AppException.class).hasMessage("simulated failure after flush");
        assertThat(flushedMutations).isTrue();
        User unchanged = userRepository.findById(userId).orElseThrow();
        assertThat(unchanged.getPasswordHash()).isEqualTo(originalHash);
        assertThat(unchanged.getAuthVersion()).isZero();
        assertThat(resetTokenRepository.findById(tokenId)).isPresent();
    }

    @Test
    void concurrentRejectedTokenCleanupNeedsOnlyOnePoolConnection() throws Exception {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
        List<String> rawTokens = new ArrayList<>();
        List<Long> tokenIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User user = createTestUser("pool_cleanup_" + i, i % 2 == 0 ? 0L : 2L);
            String rawToken = UUID.randomUUID().toString().replace("-", "").repeat(2);
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(sha256Hex(rawToken));
            token.setAuthVersion(0L);
            token.setExpiresAt(Instant.now().plusSeconds(i % 2 == 0 ? -60 : 300));
            tokenIds.add(resetTokenRepository.saveAndFlush(token).getId());
            rawTokens.add(rawToken);
        }
        CyclicBarrier barrier = new CyclicBarrier(rawTokens.size());
        ExecutorService executor = Executors.newFixedThreadPool(rawTokens.size());
        ownedExecutors.add(executor);
        {
            List<Future<ErrorCode>> outcomes = new ArrayList<>();
            for (String rawToken : rawTokens) {
                outcomes.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        passwordResetService.resetPassword(
                                new ResetPasswordRequest(rawToken, "UnusedNewPass123!", "UnusedNewPass123!"));
                        return null;
                    } catch (AppException ex) {
                        return ex.getErrorCode();
                    }
                }));
            }
            for (Future<ErrorCode> outcome : outcomes) {
                assertThat(outcome.get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.BAD_REQUEST);
            }
        }
        assertThat(resetTokenRepository.findAllById(tokenIds)).isEmpty();
        for (Long userId : ownedUserIds) {
            assertThat(passwordEncoder.matches("InitialPassword123!",
                    userRepository.findById(userId).orElseThrow().getPasswordHash())).isTrue();
        }
    }

    @Test
    @DisplayName("7. Concurrent single-use: exactly one thread succeeds and the second thread is rejected")
    void concurrentSingleUseAllowsExactlyOneSuccess() throws Exception {
        User user = createTestUser("concurrent_user", 0L);
        String rawToken = "8888888888888888888888888888888888888888888888888888888888888888";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token = resetTokenRepository.saveAndFlush(token);
        Long tokenId = token.getId();

        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        ownedExecutors.add(executor);
        {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        passwordResetService.resetPassword(
                                new ResetPasswordRequest(rawToken, "NewConcurrentPass123!", "NewConcurrentPass123!"));
                        successCount.incrementAndGet();
                    } catch (AppException ex) {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                        failCount.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(successCount.get()).as("Exactly 1 concurrent reset must succeed").isEqualTo(1);
        assertThat(failCount.get()).as("The competing concurrent reset must fail").isEqualTo(1);

        // User password updated and authVersion incremented exactly once
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getAuthVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches("NewConcurrentPass123!", updatedUser.getPasswordHash())).isTrue();

        // Token consumed
        assertThat(resetTokenRepository.findById(tokenId)).isEmpty();
    }
}
