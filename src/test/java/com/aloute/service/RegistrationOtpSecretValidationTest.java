package com.aloute.service;

import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.email.EmailService;
import com.aloute.service.impl.RegistrationOtpServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RegistrationOtpSecretValidationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegistrationOtpRepository otpRepository = mock(RegistrationOtpRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final Executor emailExecutor = mock(Executor.class);

    @Test
    @DisplayName("Non-test profile: missing or blank secret throws IllegalStateException")
    void nonTestProfileMissingSecretThrows() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatThrownBy(service::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must be configured and contain at least 32 bytes");
    }

    @Test
    @DisplayName("Non-test profile: secret shorter than 32 bytes throws IllegalStateException")
    void nonTestProfileShortSecretThrows() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, devEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "short-key-under-32-bytes");

        assertThatThrownBy(service::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must be configured and contain at least 32 bytes");
    }

    @Test
    @DisplayName("Non-test profile: secret with at least 32 bytes succeeds")
    void nonTestProfileValidSecretSucceeds() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, prodEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "secure-key-containing-at-least-32-bytes-length!");

        assertThatCode(service::validateSecret).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test profile: explicit weak secret (< 32 bytes) throws IllegalStateException")
    void testProfileWeakSecretThrows() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "too-short");

        assertThatThrownBy(service::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALOUTE_REGISTRATION_OTP_SECRET must contain at least 32 bytes");
    }

    @Test
    @DisplayName("Test profile: explicit valid secret (>= 32 bytes) succeeds")
    void testProfileValidSecretSucceeds() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        RegistrationOtpServiceImpl service = new RegistrationOtpServiceImpl(
                userRepository, otpRepository, emailService, emailExecutor, testEnv
        );
        ReflectionTestUtils.setField(service, "secretKey", "explicit-test-secret-at-least-32-bytes-long");

        assertThatCode(service::validateSecret).doesNotThrowAnyException();
    }
}
