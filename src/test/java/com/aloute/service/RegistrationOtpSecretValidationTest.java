package com.aloute.service;

import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.email.EmailService;
import com.aloute.service.impl.RegistrationOtpServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.concurrent.Executor;

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
}
