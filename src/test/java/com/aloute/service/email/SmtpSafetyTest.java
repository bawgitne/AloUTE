package com.aloute.service.email;

import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.entity.RegistrationOtp;
import com.aloute.entity.User;
import com.aloute.repository.PasswordResetTokenRepository;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.PasswordResetService;
import com.aloute.service.RegistrationOtpService;
import com.aloute.service.impl.PasswordResetServiceImpl;
import com.aloute.service.impl.RegistrationOtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.registration-otp-secret=dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=",
        "aloute.auth.registration-otp-expiry-minutes=5",
        "aloute.auth.registration-otp-cooldown-seconds=60",
        "aloute.auth.registration-otp-max-attempts=5"
})
class SmtpSafetyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegistrationOtpRepository otpRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private RegistrationOtpService registrationOtpService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private TestEmailSink testEmailSink;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        testEmailSink.clearSink();
        testEmailSink.setDeliveryFailureException(null);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "@example.com";
    }

    private User createTestUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername("u_" + System.currentTimeMillis());
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(Role.USER);
        user.setActive(true);
        user.setVerified(false);
        user.setVerificationRequired(true);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("Email sanitization masks recipient identity and preserves null safety")
    void emailSanitizationBehavior() {
        assertThat(DefaultEmailService.sanitizeForLog(null)).isEqualTo("null");
        assertThat(DefaultEmailService.sanitizeForLog("")).isEqualTo("***");
        assertThat(DefaultEmailService.sanitizeForLog("a@b.com")).isEqualTo("***@b.com");
        assertThat(DefaultEmailService.sanitizeForLog("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(DefaultEmailService.sanitizeForLog("user.name+tag@sub.domain.vn")).isEqualTo("u***@sub.domain.vn");
    }

    @Test
    @DisplayName("SMTP delivery failure during OTP dispatch leaves OTP record intact for resend recovery")
    void otpDeliveryFailurePreservesOtpRecord() {
        String email = uniqueEmail("otp_fail");
        User user = createTestUser(email);

        // Configure test sink to simulate SMTP delivery failure
        testEmailSink.setDeliveryFailureException(new MailDeliveryException("SMTP connection refused by upstream"));

        // Dispatch OTP - should not throw, should commit OTP to DB
        assertThatCode(() -> registrationOtpService.initiateRegistrationOtp(user))
                .doesNotThrowAnyException();

        // Verify OTP record exists in DB and is usable for recovery
        Optional<RegistrationOtp> otpOpt = otpRepository.findByUser(user);
        assertThat(otpOpt).isPresent();
        RegistrationOtp otp = otpOpt.get();
        assertThat(otp.getOtpHash()).isNotBlank();
        assertThat(otp.getAttempts()).isEqualTo(0);
        assertThat(otp.isExpired(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("Executor queue rejection during OTP dispatch leaves OTP record intact for resend recovery")
    void otpExecutorRejectionPreservesOtpRecord() {
        String email = uniqueEmail("otp_reject");
        User user = createTestUser(email);

        Executor saturatingExecutor = runnable -> {
            throw new RejectedExecutionException("Bounded queue is full");
        };

        // Temporarily inject rejecting executor
        Object originalExecutor = ReflectionTestUtils.getField(registrationOtpService, "emailExecutor");
        try {
            ReflectionTestUtils.setField(registrationOtpService, "emailExecutor", saturatingExecutor);

            assertThatCode(() -> registrationOtpService.initiateRegistrationOtp(user))
                    .doesNotThrowAnyException();

            // OTP record must still be committed safely
            Optional<RegistrationOtp> otpOpt = otpRepository.findByUser(user);
            assertThat(otpOpt).isPresent();
        } finally {
            ReflectionTestUtils.setField(registrationOtpService, "emailExecutor", originalExecutor);
        }
    }

    @Test
    @DisplayName("SMTP delivery failure during password reset leaves reset token intact for operational retry")
    void passwordResetDeliveryFailurePreservesToken() {
        String email = uniqueEmail("reset_fail");
        User user = createTestUser(email);
        user.setVerified(true);
        user.setVerificationRequired(false);
        userRepository.saveAndFlush(user);

        testEmailSink.setDeliveryFailureException(new MailDeliveryException("SMTP authentication failed"));

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);

        assertThatCode(() -> passwordResetService.initiateForgotPassword(request))
                .doesNotThrowAnyException();

        // Verify token was committed to DB
        assertThat(tokenRepository.findByUser(user)).isPresent();
    }

    @Test
    @DisplayName("Executor queue rejection during password reset leaves token intact for operational retry")
    void passwordResetExecutorRejectionPreservesToken() {
        String email = uniqueEmail("reset_reject");
        User user = createTestUser(email);
        user.setVerified(true);
        user.setVerificationRequired(false);
        userRepository.saveAndFlush(user);

        Executor saturatingExecutor = runnable -> {
            throw new RejectedExecutionException("Bounded queue is full");
        };

        Object originalExecutor = ReflectionTestUtils.getField(passwordResetService, "emailExecutor");
        try {
            ReflectionTestUtils.setField(passwordResetService, "emailExecutor", saturatingExecutor);

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail(email);

            assertThatCode(() -> passwordResetService.initiateForgotPassword(request))
                    .doesNotThrowAnyException();

            assertThat(tokenRepository.findByUser(user)).isPresent();
        } finally {
            ReflectionTestUtils.setField(passwordResetService, "emailExecutor", originalExecutor);
        }
    }

    @Test
    @DisplayName("Unknown email in forgot-password returns generic success without dispatching email")
    void unknownEmailForgotPasswordAntiEnumeration() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown_nonexistent_user@example.com");

        int initialEmailCount = testEmailSink.getEmailSink().size();

        assertThatCode(() -> passwordResetService.initiateForgotPassword(request))
                .doesNotThrowAnyException();

        // Zero emails dispatched
        assertThat(testEmailSink.getEmailSink()).hasSize(initialEmailCount);
    }

    @Test
    @DisplayName("Unknown email in resendOtp returns generic success without dispatching email")
    void unknownEmailResendOtpAntiEnumeration() {
        int initialOtpCount = testEmailSink.getOtpSink().size();

        assertThatCode(() -> registrationOtpService.resendOtp("unknown_nonexistent_user@example.com"))
                .doesNotThrowAnyException();

        // Zero OTPs dispatched
        assertThat(testEmailSink.getOtpSink()).hasSize(initialOtpCount);
    }

    @Test
    @DisplayName("Resend within cooldown period is silently suppressed without email dispatch")
    void resendOtpWithinCooldownSuppressesDuplicateDispatch() {
        String email = uniqueEmail("cooldown_test");
        User user = createTestUser(email);

        registrationOtpService.initiateRegistrationOtp(user);
        boolean received = testEmailSink.awaitOtpCount(1, 5, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        int dispatchedAfterFirst = testEmailSink.getOtpSink().size();

        // Immediate resend should be within 60s cooldown
        registrationOtpService.resendOtp(email);

        // No new OTP should have been dispatched
        assertThat(testEmailSink.getOtpSink()).hasSize(dispatchedAfterFirst);
    }

    @Test
    @DisplayName("API /api/auth/resend-registration-otp returns truthful neutral copy for unverified user")
    void resendRegistrationOtpReturnsNeutralCopyForUnverifiedUser() throws Exception {
        String email = uniqueEmail("neutral_unverified");
        createTestUser(email);

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Yêu cầu gửi lại mã xác thực đã được tiếp nhận. Vui lòng kiểm tra hộp thư nếu thông tin tài khoản hợp lệ."));
    }

    @Test
    @DisplayName("API /api/auth/resend-registration-otp returns identical neutral copy for unknown recipient without email dispatch")
    void resendRegistrationOtpReturnsIdenticalNeutralCopyForUnknownRecipient() throws Exception {
        String unknownEmail = "unknown_" + System.currentTimeMillis() + "@example.com";
        int initialCount = testEmailSink.getOtpSink().size();

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unknownEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Yêu cầu gửi lại mã xác thực đã được tiếp nhận. Vui lòng kiểm tra hộp thư nếu thông tin tài khoản hợp lệ."));

        assertThat(testEmailSink.getOtpSink()).hasSize(initialCount);
    }

    @Test
    @DisplayName("API /api/auth/resend-registration-otp returns identical neutral copy for already verified user without email dispatch")
    void resendRegistrationOtpReturnsIdenticalNeutralCopyForAlreadyVerifiedUser() throws Exception {
        String email = uniqueEmail("already_verified");
        User user = createTestUser(email);
        user.setVerified(true);
        user.setVerificationRequired(false);
        userRepository.saveAndFlush(user);

        int initialCount = testEmailSink.getOtpSink().size();

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Yêu cầu gửi lại mã xác thực đã được tiếp nhận. Vui lòng kiểm tra hộp thư nếu thông tin tài khoản hợp lệ."));

        assertThat(testEmailSink.getOtpSink()).hasSize(initialCount);
    }
}
