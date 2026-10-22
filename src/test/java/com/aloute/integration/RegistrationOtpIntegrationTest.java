package com.aloute.integration;

import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.request.ResendRegistrationOtpRequest;
import com.aloute.dto.request.VerifyRegistrationOtpRequest;
import com.aloute.entity.RegistrationOtp;
import com.aloute.entity.User;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.email.TestEmailSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.aloute.service.impl.RegistrationOtpServiceImpl;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
class RegistrationOtpIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RegistrationOtpRepository otpRepository;
    @Autowired private TestEmailSink emailSink;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RegistrationOtpServiceImpl registrationOtpService;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        emailSink.clearSink();
    }

    private String generateEmail(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@example.com";
    }

    private String generateUsername(String prefix) {
        return prefix + "_" + System.currentTimeMillis() % 1000000 + "_" + ThreadLocalRandom.current().nextInt(100, 999);
    }

    @Test
    @DisplayName("Cases 1, 2, 4, 5, 6: Full valid registration -> OTP dispatch -> Blocked login -> Verify -> Successful login")
    void registrationLifecycleVerification() throws Exception {
        String email = generateEmail("reg_flow");
        String username = generateUsername("flow");
        String password = "StrongPassword123!";

        // Case 1: Register with valid email
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.isVerified").value(false));

        // Case 2: Account remains unverified in database
        User createdUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(createdUser.isVerified()).isFalse();
        assertThat(createdUser.isVerificationRequired()).isTrue();

        // Case 4: The committed OTP is delivered before the registration response returns.
        List<TestEmailSink.SentOtp> sentOtps = emailSink.getOtpSink();
        assertThat(sentOtps).hasSize(1);
        assertThat(sentOtps.get(0).toEmail()).isEqualTo(email);
        String rawOtp = sentOtps.get(0).otpCode();
        assertThat(rawOtp).matches("^\\d{6}$");

        // Case 3: Login is blocked before verification
        LoginRequest loginReq = new LoginRequest(email, password);
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_NOT_VERIFIED"))
                .andExpect(cookie().doesNotExist("aloute_access_token"));

        // Case 5: Correct OTP activates account
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, rawOtp);
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());

        User activatedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(activatedUser.isVerified()).isTrue();
        assertThat(activatedUser.isVerificationRequired()).isFalse();
        assertThat(otpRepository.findByUser(activatedUser)).isEmpty();

        // Case 6: Login works after verification
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.isVerified").value(true));
    }

    @Test
    @DisplayName("Case 7: Wrong OTP is rejected and increments attempt count")
    void wrongOtpRejectedAndIncrementsAttempts() throws Exception {
        String email = generateEmail("wrong_otp");
        String username = generateUsername("wrong");
        String password = "Password123!";

        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpBefore = otpRepository.findByUser(user).orElseThrow();
        assertThat(otpBefore.getAttempts()).isEqualTo(0);

        // Submit incorrect OTP
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, "999999");
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        RegistrationOtp otpAfter = otpRepository.findByUser(user).orElseThrow();
        assertThat(otpAfter.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Case 8: Expired OTP is rejected")
    void expiredOtpRejected() throws Exception {
        String email = generateEmail("expired_otp");
        String username = generateUsername("exp");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String rawOtp = emailSink.getOtpSink().get(0).otpCode();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setExpiresAt(Instant.now().minusSeconds(10));
        otpRepository.saveAndFlush(otpRecord);

        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, rawOtp);
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));
    }

    @Test
    @DisplayName("Case 9: Reused OTP is rejected")
    void reusedOtpRejected() throws Exception {
        String email = generateEmail("reused_otp");
        String username = generateUsername("reused");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String rawOtp = emailSink.getOtpSink().get(0).otpCode();

        // First verification succeeds
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, rawOtp);
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk());

        // Reusing same OTP must fail
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Case 10: Five failed attempts enforce max limit")
    void maxAttemptsEnforced() throws Exception {
        String email = generateEmail("max_attempts");
        String username = generateUsername("attempts");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String correctOtp = emailSink.getOtpSink().get(0).otpCode();

        // 4 failed attempts
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/verify-registration-otp")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, "000000"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));
        }

        // 5th failed attempt hits limit
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        // 6th attempt with correct OTP is still rejected because limit is reached
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, correctOtp))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        RegistrationOtp exhaustedOtp = otpRepository.findByUser(userRepository.findByEmailIgnoreCase(email).orElseThrow()).orElseThrow();
        assertThat(exhaustedOtp.getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Case 11: Resend cooldown suppresses dispatch silently with 200, and flooding triggers 429")
    void resendCooldownEnforced() throws Exception {
        String email = generateEmail("cooldown");
        String username = generateUsername("cool");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        assertThat(emailSink.getOtpSink()).hasSize(1);

        // Immediate resend within 60s cooldown returns 200 silently without dispatching additional email
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isOk());

        // Still exactly 1 email dispatched (cooldown prevented second dispatch)
        assertThat(emailSink.getOtpSink()).hasSize(1);

        // Rapid flooding exceeding 10 requests/minute triggers 429 RATE_LIMIT_EXCEEDED
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post("/api/auth/resend-registration-otp")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))));
        }

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));

        // Unknown email also hits 429 when flooded
        String unknownEmail = "flooded_unknown@example.com";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/resend-registration-otp")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(unknownEmail))));
        }
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(unknownEmail))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Case 12: Previous OTP becomes invalid after resend")
    void resendInvalidatesPriorOtp() throws Exception {
        String email = generateEmail("resend_inval");
        String username = generateUsername("inval");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String firstOtp = emailSink.getOtpSink().get(0).otpCode();

        // Fast-forward cooldown in database
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        // Resend OTP
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isOk());

        assertThat(emailSink.awaitOtpCount(2, 3, TimeUnit.SECONDS)).isTrue();
        String secondOtp = emailSink.getOtpSink().get(1).otpCode();

        // Prior OTP must now fail
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, firstOtp))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        // New OTP succeeds
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, secondOtp))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 13: Concurrent verification cannot reuse OTP (atomic locking)")
    void concurrentVerificationCannotReuseOtp() throws Exception {
        String email = generateEmail("concurrent_otp");
        String username = generateUsername("conc");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String validOtp = emailSink.getOtpSink().get(0).otpCode();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    MvcResult result = mockMvc.perform(post("/api/auth/verify-registration-otp")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, validOtp))))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one concurrent verification succeeds; remaining concurrent requests are rejected
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        User finalUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(finalUser.isVerified()).isTrue();
        assertThat(otpRepository.findByUser(finalUser)).isEmpty();
    }

    @Test
    @DisplayName("Case 14: Existing legacy users remain compatible and are not locked out")
    void existingLegacyUsersRemainCompatible() throws Exception {
        String email = generateEmail("legacy_user");
        String username = generateUsername("legacy");
        String password = "LegacyPassword123!";

        // Create legacy user: verificationRequired = false, isVerified = false
        User legacyUser = new User();
        legacyUser.setUsername(username);
        legacyUser.setEmail(email);
        legacyUser.setPasswordHash(passwordEncoder.encode(password));
        legacyUser.setVerified(false);
        legacyUser.setVerificationRequired(false);
        legacyUser.setActive(true);
        legacyUser.setRole(Role.USER);
        userRepository.saveAndFlush(legacyUser);

        // 1. Verify initial legacy flags in database
        User legacyBefore = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(legacyBefore.isVerified()).isFalse();
        assertThat(legacyBefore.isVerificationRequired()).isFalse();

        // 2. Submit verify and resend on legacy user (anti-enumeration behavior: verify returns 400, resend returns 200)
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isOk());

        // 3. Verify legacy flags remain completely unchanged after OTP endpoints
        User legacyAfter = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(legacyAfter.isVerified()).isFalse();
        assertThat(legacyAfter.isVerificationRequired()).isFalse();
        assertThat(otpRepository.findByUser(legacyAfter)).isEmpty();

        // 4. Legacy user can log in without verification requirement blocking them
        LoginRequest loginReq = new LoginRequest(email, password);
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    @DisplayName("Case 15: CSRF remains enforced on registration OTP endpoints")
    void csrfRemainsEnforced() throws Exception {
        String email = generateEmail("csrf_test");
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, "123456");

        // Without CSRF token, endpoint must be rejected with 403 Forbidden
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isForbidden());

        // Resend without CSRF must also be rejected
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Case 16: Email delivery failure permits safe resend")
    void emailFailurePermitsSafeResend() throws Exception {
        String email = generateEmail("email_fail");
        String username = generateUsername("efail");
        String password = "Password123!";

        // Simulate failure in email delivery
        emailSink.setDeliveryFailureException(new RuntimeException("Simulated SMTP network outage"));

        // Registration still completes successfully (transaction not rolled back by email error)
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.isVerificationRequired()).isTrue();

        // Clear failure exception and fast-forward cooldown
        emailSink.setDeliveryFailureException(null);
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        // Controlled resend succeeds and delivers new OTP
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                .andExpect(status().isOk());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String deliveredOtp = emailSink.getOtpSink().get(0).otpCode();

        // Verification with newly delivered OTP succeeds
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(email, deliveredOtp))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 17: Anti-enumeration verify returns identical INVALID_OTP for nonexistent, inactive, already verified, and legacy accounts")
    void antiEnumerationVerifyReturnsIdenticalResponse() throws Exception {
        // 1. Nonexistent email
        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest("nonexistent_user@example.com", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        // 2. Inactive account
        String inactiveEmail = generateEmail("inactive_user");
        User inactiveUser = new User();
        inactiveUser.setUsername(generateUsername("inactive"));
        inactiveUser.setEmail(inactiveEmail);
        inactiveUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        inactiveUser.setVerified(false);
        inactiveUser.setVerificationRequired(true);
        inactiveUser.setActive(false);
        inactiveUser.setRole(Role.USER);
        userRepository.saveAndFlush(inactiveUser);

        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(inactiveEmail, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        // 3. Already verified account
        String verifiedEmail = generateEmail("verified_user");
        User verifiedUser = new User();
        verifiedUser.setUsername(generateUsername("verified"));
        verifiedUser.setEmail(verifiedEmail);
        verifiedUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        verifiedUser.setVerified(true);
        verifiedUser.setVerificationRequired(false);
        verifiedUser.setActive(true);
        verifiedUser.setRole(Role.USER);
        userRepository.saveAndFlush(verifiedUser);

        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(verifiedEmail, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));

        // 4. Legacy account (verificationRequired = false)
        String legacyEmail = generateEmail("legacy_user");
        User legacyUser = new User();
        legacyUser.setUsername(generateUsername("legacy"));
        legacyUser.setEmail(legacyEmail);
        legacyUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        legacyUser.setVerified(false);
        legacyUser.setVerificationRequired(false);
        legacyUser.setActive(true);
        legacyUser.setRole(Role.USER);
        userRepository.saveAndFlush(legacyUser);

        mockMvc.perform(post("/api/auth/verify-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationOtpRequest(legacyEmail, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));
    }

    @Test
    @DisplayName("Case 18: Anti-enumeration resend returns silent generic 200 without email delivery for nonexistent, inactive, already verified, and legacy accounts")
    void antiEnumerationResendReturnsSilentGenericSuccess() throws Exception {
        emailSink.clearSink();

        // 1. Nonexistent email
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest("ghost_resend@example.com"))))
                .andExpect(status().isOk());

        // 2. Inactive account
        String inactiveEmail = generateEmail("resend_inact");
        User inactiveUser = new User();
        inactiveUser.setUsername(generateUsername("inact"));
        inactiveUser.setEmail(inactiveEmail);
        inactiveUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        inactiveUser.setVerified(false);
        inactiveUser.setVerificationRequired(true);
        inactiveUser.setActive(false);
        inactiveUser.setRole(Role.USER);
        userRepository.saveAndFlush(inactiveUser);

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(inactiveEmail))))
                .andExpect(status().isOk());

        // 3. Already verified account
        String verifiedEmail = generateEmail("resend_ver");
        User verifiedUser = new User();
        verifiedUser.setUsername(generateUsername("ver"));
        verifiedUser.setEmail(verifiedEmail);
        verifiedUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        verifiedUser.setVerified(true);
        verifiedUser.setVerificationRequired(false);
        verifiedUser.setActive(true);
        verifiedUser.setRole(Role.USER);
        userRepository.saveAndFlush(verifiedUser);

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(verifiedEmail))))
                .andExpect(status().isOk());

        // 4. Legacy account
        String legacyEmail = generateEmail("resend_leg");
        User legacyUser = new User();
        legacyUser.setUsername(generateUsername("leg"));
        legacyUser.setEmail(legacyEmail);
        legacyUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        legacyUser.setVerified(false);
        legacyUser.setVerificationRequired(false);
        legacyUser.setActive(true);
        legacyUser.setRole(Role.USER);
        userRepository.saveAndFlush(legacyUser);

        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(legacyEmail))))
                .andExpect(status().isOk());

        // Verify zero emails were dispatched for nonexistent/inactive/already-verified/legacy accounts
        assertThat(emailSink.getOtpSink()).isEmpty();
    }

    @Test
    @DisplayName("Case 19: Concurrent resend respects cooldown and eliminates duplicate dispatches")
    void concurrentResendRespectsCooldown() throws Exception {
        String email = generateEmail("conc_resend");
        String username = generateUsername("cresend");
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        emailSink.clearSink();

        // Fast-forward cooldown so resend is initially permitted
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    MvcResult res = mockMvc.perform(post("/api/auth/resend-registration-otp")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(email))))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // All concurrent resend requests receive 200 generic success, but exactly one email dispatch occurs
        assertThat(successCount.get()).isEqualTo(threads);
        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        assertThat(emailSink.getOtpSink()).hasSize(1);
    }

    @Test
    @DisplayName("Case 20: Concurrent near-capacity limiter admissions and fair saturation")
    void recipientRateLimiterCapacityEvictionAndSaturation() throws Exception {
        registrationOtpService.clearRateLimiter();

        // 1. Pre-fill to near capacity: 9,995 active recipients (5 slots remaining)
        Instant fresh = Instant.now();
        for (int i = 0; i < 9_995; i++) {
            registrationOtpService.recordManualRequestForTesting("prefill_" + i + "@example.com", fresh);
        }
        assertThat(registrationOtpService.getTrackedRecipientsCount()).isEqualTo(9_995);

        // 2. Real concurrent admission proof: burst of 20 concurrent unseen emails competing for 5 slots
        int burstSize = 20;
        int remainingSlots = 5;
        ExecutorService executor = Executors.newFixedThreadPool(burstSize);
        CyclicBarrier barrier = new CyclicBarrier(burstSize);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < burstSize; i++) {
            final String burstEmail = generateEmail("burst_limiter_" + i);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    MvcResult res = mockMvc.perform(post("/api/auth/resend-registration-otp")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(burstEmail))))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else if (res.getResponse().getStatus() == 429) {
                        rateLimitCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 5 admitted (200), exactly 15 rejected with 429
        assertThat(successCount.get()).isEqualTo(remainingSlots);
        assertThat(rateLimitCount.get()).isEqualTo(burstSize - remainingSlots);
        // Hard map bound enforced at 10,000
        assertThat(registrationOtpService.getTrackedRecipientsCount()).isEqualTo(10_000);

        // 3. Clear and prove expired-state reclamation with 10,000 stale entries
        registrationOtpService.clearRateLimiter();
        Instant stale = Instant.now().minus(Duration.ofMinutes(2));
        for (int i = 0; i < 10_000; i++) {
            registrationOtpService.recordManualRequestForTesting("stale_" + i + "@example.com", stale);
        }
        assertThat(registrationOtpService.getTrackedRecipientsCount()).isEqualTo(10_000);

        // A new recipient request evicts stale entries and succeeds
        String freshEmail = generateEmail("evict_test");
        mockMvc.perform(post("/api/auth/resend-registration-otp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendRegistrationOtpRequest(freshEmail))))
                .andExpect(status().isOk());

        // Stale entries evicted, only freshEmail remains
        assertThat(registrationOtpService.getTrackedRecipientsCount()).isEqualTo(1);

        // 4. Clean up rate limiter
        registrationOtpService.clearRateLimiter();
    }

    @Test
    @DisplayName("Case 21: Transaction dispatch lifecycle: no email before commit, email after commit, no email on rollback")
    void transactionControlledEmailDispatchLifecycle() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        emailSink.clearSink();

        String email = generateEmail("tx_lifecycle");
        String username = generateUsername("txl");
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setVerified(false);
        user.setVerificationRequired(true);
        user.setActive(true);
        user.setRole(Role.USER);
        userRepository.saveAndFlush(user);

        transactionTemplate.execute(status -> {
            registrationOtpService.initiateRegistrationOtp(user);
            assertThat(emailSink.getInvocationCount()).isZero();
            assertThat(emailSink.getOtpSink()).isEmpty();
            return null;
        });

        // Delivery has already finished when commit returns; there is no background task left.
        assertThat(emailSink.getInvocationCount()).isEqualTo(1);
        assertThat(emailSink.getOtpSink()).hasSize(1);
        assertThat(emailSink.getOtpSink().get(0).toEmail()).isEqualTo(email);
        emailSink.clearSink();

        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);
        String committedHash = otpRecord.getOtpHash();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            registrationOtpService.resendOtp(email);
            assertThat(emailSink.getInvocationCount()).isZero();
            throw new RuntimeException("Simulated transaction rollback");
        })).isInstanceOf(RuntimeException.class).hasMessage("Simulated transaction rollback");

        assertThat(emailSink.getInvocationCount()).isZero();
        assertThat(emailSink.getOtpSink()).isEmpty();
        assertThat(otpRepository.findByUser(user).orElseThrow().getOtpHash()).isEqualTo(committedHash);

        transactionTemplate.execute(status -> {
            registrationOtpService.resendOtp(email);
            assertThat(emailSink.getInvocationCount()).isZero();
            status.setRollbackOnly();
            return null;
        });

        assertThat(emailSink.getInvocationCount()).isZero();
        assertThat(emailSink.getOtpSink()).isEmpty();
        assertThat(otpRepository.findByUser(user).orElseThrow().getOtpHash()).isEqualTo(committedHash);
    }
}
