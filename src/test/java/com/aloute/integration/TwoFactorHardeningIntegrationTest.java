package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.VerifyTwoFactorLoginRequest;
import com.aloute.entity.User;
import com.aloute.entity.UserBackupCode;
import com.aloute.repository.UserBackupCodeRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.crypto.TwoFactorEncryptionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.service.TotpService;
import com.aloute.service.TwoFactorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * BL-08: 2FA login tickets are single-use and bound to the user's authVersion;
 * backup codes redeem atomically; legacy unsalted hashes keep working while new
 * codes are stored salted (v2 PBKDF2).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class TwoFactorHardeningIntegrationTest {

    private static final String PASSWORD = "Correct@Password1";

    @Autowired private UserRepository userRepository;
    @Autowired private UserBackupCodeRepository userBackupCodeRepository;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private TotpService totpService;
    @Autowired private TwoFactorEncryptionService twoFactorEncryptionService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate independentTx;
    private User user;
    private String totpSecret;

    @BeforeEach
    void setUp() {
        independentTx = new TransactionTemplate(transactionManager);
        independentTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        long suffix = System.nanoTime();
        user = independentTx.execute(status -> {
            User u = new User();
            u.setUsername("2faharden_" + suffix);
            u.setEmail("2faharden_" + suffix + "@aloute.test");
            u.setPasswordHash(passwordEncoder.encode(PASSWORD));
            u.setVerified(true);
            u.setActive(true);
            u.setRole(Role.USER);
            return userRepository.saveAndFlush(u);
        });

        // 2FA enabled with a known plaintext secret
        totpSecret = totpService.generateSecretKey();
        independentTx.executeWithoutResult(status -> {
            User u = userRepository.findById(user.getId()).orElseThrow();
            u.setTwoFactorEnabled(true);
            u.setTwoFactorSecret(twoFactorEncryptionService.encrypt(totpSecret));
            u.setTwoFactorConfirmedAt(Instant.now());
            u.setTwoFactorType("TOTP");
            userRepository.saveAndFlush(u);
        });
    }

    private String ticketFor(long authVersion) {
        return jwtTokenProvider.generateTwoFactorTicket(user.getId(), "TOTP", 300, authVersion);
    }

    private ApiException verifyExpectFailure(String ticket, String code, boolean backup, String expectedCode) {
        Throwable thrown = catchThrowableOfType(
                () -> twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(ticket, code, backup)),
                ApiException.class);
        assertThat(thrown).as("verifyLogin must reject with " + expectedCode).isNotNull();
        return (ApiException) thrown;
    }

    @Test
    @DisplayName("BL-08: a verified ticket cannot verify a second time (single-use)")
    void ticketIsSingleUse() {
        String ticket = ticketFor(0L);
        String code = totpService.generateCurrentCode(totpSecret);

        var session = twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(ticket, code, false));
        assertThat(session.token()).isNotBlank();

        ApiException replay = verifyExpectFailure(ticket, code, false, "AUTH_2FA_TICKET_ALREADY_USED");
        assertThat(replay.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("BL-08: ticket bound to authVersion — stale ticket after revocation is rejected")
    void ticketBoundToAuthVersion() {
        independentTx.executeWithoutResult(status -> {
            User u = userRepository.findById(user.getId()).orElseThrow();
            u.setAuthVersion(1L);
            userRepository.saveAndFlush(u);
        });

        ApiException rejected = verifyExpectFailure(ticketFor(0L),
                totpService.generateCurrentCode(totpSecret), false, "AUTH_INVALID_2FA_TICKET");
        assertThat(rejected.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Current authVersion still verifies fine.
        var session = twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(ticketFor(1L),
                totpService.generateCurrentCode(totpSecret), false));
        assertThat(session.token()).isNotBlank();
    }

    @Test
    @DisplayName("BL-08: concurrent redemption of one backup code succeeds exactly once")
    void backupCodeRedeemsAtomically() throws Exception {
        List<String> codes = independentTx.execute(status ->
                twoFactorService.regenerateBackupCodes(user.getId(), PASSWORD));
        assertThat(codes).isNotEmpty();
        String rawCode = codes.get(0);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger invalidCodes = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    // Distinct tickets so only the code redemption races.
                    twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(
                            jwtTokenProvider.generateTwoFactorTicket(user.getId(), "TOTP", 300, 0L),
                            rawCode, true));
                    successes.incrementAndGet();
                } catch (ApiException ex) {
                    if ("AUTH_2FA_INVALID_CODE".equals(ex.getCode())) {
                        invalidCodes.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                }
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).as("exactly one redemption wins").isEqualTo(1);
        assertThat(invalidCodes.get()).isEqualTo(1);

        int unusedLeft = independentTx.execute(status ->
                (int) userBackupCodeRepository.countByUserIdAndUsedFalse(user.getId()));
        assertThat(unusedLeft).isEqualTo(codes.size() - 1);
    }

    @Test
    @DisplayName("Legacy unsalted SHA-256 backup codes keep working; new codes are stored v2")
    void legacyBackupHashesRemainValid() {
        // New codes are stored with the salted v2 format.
        List<String> codes = independentTx.execute(status ->
                twoFactorService.regenerateBackupCodes(user.getId(), PASSWORD));
        String firstStored = independentTx.execute(status ->
                userBackupCodeRepository.findByUserIdAndUsedFalse(user.getId()).get(0).getCodeHash());
        assertThat(firstStored).startsWith("v2:");

        // A legacy-format row still redeems.
        String legacyRaw = "LEGACYCODE";
        String legacyHash = sha256Base64(legacyRaw);
        independentTx.executeWithoutResult(status -> {
            User managed = userRepository.findById(user.getId()).orElseThrow();
            UserBackupCode legacy = new UserBackupCode();
            legacy.setUser(managed);
            legacy.setCodeHash(legacyHash);
            legacy.setUsed(false);
            userBackupCodeRepository.saveAndFlush(legacy);
        });

        var session = twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(
                ticketFor(0L), legacyRaw, true));
        assertThat(session.token()).isNotBlank();
        assertThat(codes).hasSize(8);
    }

    private String sha256Base64(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.replaceAll("[^A-Za-z0-9]", "").toUpperCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
