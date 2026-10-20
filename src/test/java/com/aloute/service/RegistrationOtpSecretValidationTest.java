package com.aloute.service;

import com.aloute.common.exception.ApiException;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.email.EmailService;
import com.aloute.service.impl.RegistrationOtpServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RegistrationOtpSecretValidationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegistrationOtpRepository otpRepository = mock(RegistrationOtpRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final Executor emailExecutor = mock(Executor.class);

    private static final String VALID_BASE64_32B = Base64.getEncoder().encodeToString(
            "random-secure-high-entropy-bytes-length-32b!".getBytes()
    );
    private static final String SHORT_BASE64 = Base64.getEncoder().encodeToString(
            "short-key".getBytes()
    );
    private static final String REPEATED_BYTE_BASE64 = Base64.getEncoder().encodeToString(
            new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    );

    @Test
    @DisplayName("Non-test profile: missing or blank secret throws IllegalStateException")
    void nonTestProfileMissingSecretThrows() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must be configured as a Base64-encoded secret");
    }

    @Test
    @DisplayName("Non-test profile: non-Base64 secret throws IllegalStateException")
    void nonTestProfileInvalidBase64Throws() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "not-a-valid-base64-string!@#$%");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be valid Base64");
    }

    @Test
    @DisplayName("Non-test profile: Base64 decoded to < 32 bytes throws IllegalStateException")
    void nonTestProfileShortSecretThrows() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, devEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", SHORT_BASE64);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to at least 32 bytes");
    }

    @Test
    @DisplayName("Non-test profile: repeated-byte secret throws IllegalStateException")
    void nonTestProfileRepeatedByteSecretThrows() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, devEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", REPEATED_BYTE_BASE64);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be a trivial repeated-byte key");
    }

    @Test
    @DisplayName("Non-test profile: valid high-entropy Base64 secret >= 32 bytes succeeds")
    void nonTestProfileValidSecretSucceeds() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", VALID_BASE64_32B);

        assertThatCode(service::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Configuration bounds: expiry > 5 minutes is rejected")
    void expiryBoundValidation() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "expiryMinutes", 6);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aloute.auth.registration-otp-expiry-minutes must be between 1 and 5 minutes");
    }

    @Test
    @DisplayName("Configuration bounds: maxAttempts > 5 is rejected")
    void maxAttemptsBoundValidation() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "maxAttempts", 10);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aloute.auth.registration-otp-max-attempts must be between 1 and 5");
    }

    @Test
    @DisplayName("Configuration bounds: cooldown < 60 seconds is rejected")
    void cooldownBoundValidation() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "cooldownSeconds", 30);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aloute.auth.registration-otp-cooldown-seconds must be at least 60 seconds");
    }

    @Test
    @DisplayName("Combined prod and test profile: missing secret fails closed and throws IllegalStateException")
    void prodAndTestCombinedProfileMissingSecretThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, env
        );
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must be configured as a Base64-encoded secret");
    }

    @Test
    @DisplayName("Prod profile: rejects known public test fixture in both padded and unpadded Base64 forms")
    void prodProfileRejectsPaddedAndUnpaddedKnownPublicTestFixture() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl servicePadded = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(servicePadded, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        assertThatThrownBy(servicePadded::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");

        RegistrationOtpServiceImpl serviceUnpadded = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        // Unpadded Base64 without trailing '='
        ReflectionTestUtils.setField(serviceUnpadded, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");

        assertThatThrownBy(serviceUnpadded::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");
    }

    @Test
    @DisplayName("Combined prod and test profile: production wins and rejects known public test fixture")
    void combinedProdAndTestProfileRejectsKnownPublicTestFixture() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, env
        );
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");

        // Unpadded
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");
    }

    @Test
    @DisplayName("Dev profile alone: rejects known public test fixture in both padded and unpadded forms")
    void devProfileRejectsPaddedAndUnpaddedKnownPublicTestFixture() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, devEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");

        // Unpadded
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");
    }

    @Test
    @DisplayName("Default profile (no active profiles): rejects known public test fixture")
    void defaultProfileRejectsKnownPublicTestFixture() {
        MockEnvironment defaultEnv = new MockEnvironment();

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, defaultEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");

        // Unpadded
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use known public test fixture");
    }

    @Test
    @DisplayName("Pure test profile: permits known public test fixture in both padded and unpadded forms")
    void testProfilePermitsPaddedAndUnpaddedKnownPublicTestFixture() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl servicePadded = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(servicePadded, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");
        assertThatCode(servicePadded::validateConfiguration).doesNotThrowAnyException();

        RegistrationOtpServiceImpl serviceUnpadded = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(serviceUnpadded, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");
        assertThatCode(serviceUnpadded::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dev and test combined profile: permits known public test fixture")
    void devAndTestCombinedProfilePermitsKnownPublicTestFixture() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, env
        );
        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");
        assertThatCode(service::validateConfiguration).doesNotThrowAnyException();

        ReflectionTestUtils.setField(service, "secretKey", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE");
        assertThatCode(service::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test profile: missing or empty secret fails closed and throws IllegalStateException")
    void testProfileMissingSecretFailsClosed() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", null);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must be configured as a Base64-encoded secret");
    }

    @Test
    @DisplayName("Test profile: raw non-Base64 secret is rejected and throws IllegalStateException")
    void testProfileRejectsNonBase64Secret() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "raw-plain-non-base64-secret-string!");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be valid Base64");
    }

    @Test
    @DisplayName("Rate limiter: evicts stale entries when capacity reaches 10,000")
    void limiterEvictsStaleEntriesAtCapacity() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");
        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", VALID_BASE64_32B);
        service.validateConfiguration();

        Instant staleTime = Instant.now().minus(Duration.ofMinutes(5));
        for (int i = 0; i < 10_000; i++) {
            service.recordManualRequestForTesting("user" + i + "@example.com", staleTime);
        }
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(10_000);

        // Attempting a new recipient should trigger eviction of stale entries rather than 429
        try {
            service.verifyOtp("newuser@example.com", "123456");
        } catch (ApiException e) {
            // Expected BAD_REQUEST (INVALID_OTP) because user does not exist, NOT TOO_MANY_REQUESTS
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo("INVALID_OTP");
        }

        // Stale entries should have been evicted; only 1 entry (newuser@example.com) should remain
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Rate limiter: saturates and rejects unseen keys with 429 when all 10,000 entries are active")
    void limiterSaturatesAndRejectsUnseenKeysWhenFullOfActiveEntries() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");
        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", VALID_BASE64_32B);
        service.validateConfiguration();

        Instant freshTime = Instant.now();
        for (int i = 0; i < 10_000; i++) {
            service.recordManualRequestForTesting("active" + i + "@example.com", freshTime);
        }
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(10_000);

        // An unseen key must be rejected with 429 RATE_LIMIT_EXCEEDED
        assertThatThrownBy(() -> service.verifyOtp("unseen@example.com", "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiEx.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                });

        // But an already-tracked active key with capacity remaining (< 10 requests) is NOT blocked by total map size
        try {
            service.verifyOtp("active0@example.com", "123456");
        } catch (ApiException e) {
            // Should pass rate limiter and reach user check (BAD_REQUEST INVALID_OTP)
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo("INVALID_OTP");
        }
    }

    @Test
    @DisplayName("Concurrent near-capacity admission: competing unseen recipients respect exact remaining slots and hard capacity")
    void concurrentNearCapacityAdmissionBurst() throws Exception {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");
        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", VALID_BASE64_32B);
        service.validateConfiguration();

        // 1. Pre-fill to near capacity: 9,995 active recipients (5 slots remaining)
        Instant fresh = Instant.now();
        for (int i = 0; i < 9_995; i++) {
            service.recordManualRequestForTesting("prefill_" + i + "@example.com", fresh);
        }
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(9_995);

        // 2. Burst of 20 concurrent threads with distinct unseen emails competing for 5 slots
        int burstSize = 20;
        int remainingSlots = 5;
        ExecutorService executor = Executors.newFixedThreadPool(burstSize);
        CyclicBarrier barrier = new CyclicBarrier(burstSize);
        AtomicInteger admittedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < burstSize; i++) {
            final String burstEmail = "burst_unit_" + i + "_" + System.nanoTime() + "@example.com";
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    service.verifyOtp(burstEmail, "123456");
                } catch (ApiException e) {
                    if ("RATE_LIMIT_EXCEEDED".equals(e.getCode())) {
                        rejectedCount.incrementAndGet();
                    } else if ("INVALID_OTP".equals(e.getCode())) {
                        admittedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 5 admitted, 15 rejected with RATE_LIMIT_EXCEEDED
        assertThat(admittedCount.get()).isEqualTo(remainingSlots);
        assertThat(rejectedCount.get()).isEqualTo(burstSize - remainingSlots);
        // Hard map bound strictly enforced at exactly 10,000
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(10_000);

        // 3. Prove expired-state reclamation:
        service.clearRateLimiter();
        Instant stale = Instant.now().minus(Duration.ofMinutes(2));
        for (int i = 0; i < 10_000; i++) {
            service.recordManualRequestForTesting("stale_unit_" + i + "@example.com", stale);
        }
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(10_000);

        try {
            service.verifyOtp("fresh_unit_after_reclaim@example.com", "123456");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo("INVALID_OTP");
        }
        assertThat(service.getTrackedRecipientsCount()).isEqualTo(1);
    }
}
