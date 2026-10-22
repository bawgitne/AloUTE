package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.User;
import com.aloute.repository.UserBackupCodeRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.service.TwoFactorService;
import com.aloute.service.email.TestEmailSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class TwoFactorAuthenticationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserBackupCodeRepository userBackupCodeRepository;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private TestEmailSink emailSink;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User testUser;

    @BeforeEach
    void setUp() {
        emailSink.clearSink();
        twoFactorService.clearEmailOtpCache();
        userBackupCodeRepository.deleteAll();

        testUser = userRepository.findByEmailIgnoreCase("twofactor_test@aloute.edu.vn").orElseGet(() -> {
            User u = new User();
            u.setUsername("twofactor_test");
            u.setEmail("twofactor_test@aloute.edu.vn");
            u.setPasswordHash(passwordEncoder.encode("Password123!"));
            u.setActive(true);
            u.setVerified(true);
            u.setVerificationRequired(false);
            return userRepository.save(u);
        });

        testUser.setTwoFactorEnabled(false);
        testUser.setTwoFactorType(null);
        testUser.setTwoFactorSecret(null);
        testUser.setTwoFactorTempSecret(null);
        testUser.setTwoFactorConfirmedAt(null);
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Gửi lại mã OTP trong vòng 60s bị chặn bởi Cooldown (HTTP 429 AUTH_OTP_COOLDOWN)")
    void testEmailSetupOtp_CooldownEnforced() {
        // First send: succeeds
        twoFactorService.sendEmailSetupOtp(testUser.getId());
        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String firstOtp = emailSink.getOtpSink().get(0).otpCode();
        assertThat(firstOtp).matches("^\\d{6}$");

        // Second send immediately: must fail with TOO_MANY_REQUESTS
        assertThatThrownBy(() -> twoFactorService.sendEmailSetupOtp(testUser.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiEx.getCode()).isEqualTo("AUTH_OTP_COOLDOWN");
                    assertThat(apiEx.getMessage()).contains("Vui lòng đợi");
                });

        // Email count should remain 1 (no duplicate email sent)
        assertThat(emailSink.getOtpSink()).hasSize(1);
    }

    @Test
    @DisplayName("Sliding multi-OTP window: mã cũ trong cửa sổ hợp lệ không bị đè mất và dùng được")
    void testMultiOtpSlidingWindow_CodeValidAndConsumesAll() {
        // Send OTP 1
        twoFactorService.sendEmailSetupOtp(testUser.getId());
        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String code1 = emailSink.getOtpSink().get(0).otpCode();

        // Verifying with invalid code fails without clearing active OTPs
        assertThatThrownBy(() -> twoFactorService.enableTwoFactorEmail(testUser.getId(), "000000"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("AUTH_2FA_INVALID_CODE");
                });

        // Valid code successfully enables 2FA Email
        var enableResponse = twoFactorService.enableTwoFactorEmail(testUser.getId(), code1);
        assertThat(enableResponse).isNotNull();
        assertThat(enableResponse.backupCodes()).hasSize(8);

        // Verify user in DB has 2FA enabled as EMAIL
        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.isTwoFactorEnabled()).isTrue();
        assertThat(reloaded.getTwoFactorType()).isEqualTo("EMAIL");

        // Verifying again with the same code must fail because OTP was atomically consumed (single-use)
        assertThatThrownBy(() -> twoFactorService.enableTwoFactorEmail(testUser.getId(), code1))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("AUTH_2FA_INVALID_CODE");
                });
    }

    @Test
    @DisplayName("Endpoint /api/auth/2fa/send-email-fallback chặn gửi liên tiếp với 429 và thông báo rõ ràng")
    void testFallbackOtpEndpoint_CooldownEnforced() throws Exception {
        String ticket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "EMAIL", 300);

        // First call: 200 OK
        mockMvc.perform(post("/api/auth/2fa/send-email-fallback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorTicket\":\"" + ticket + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();

        // Second call immediately: 429 Too Many Requests
        mockMvc.perform(post("/api/auth/2fa/send-email-fallback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorTicket\":\"" + ticket + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_OTP_COOLDOWN"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Vui lòng đợi")));
    }

    @Test
    @DisplayName("Đăng nhập với tài khoản 2FA Email tự động gửi OTP, chịu được submit lại và xác thực thành công")
    void testLoginWithEmail2fa_FullFlow() throws Exception {
        // Enable 2FA Email for user
        testUser.setTwoFactorEnabled(true);
        testUser.setTwoFactorType("EMAIL");
        testUser.setTwoFactorConfirmedAt(Instant.now());
        userRepository.save(testUser);

        // Step 1: Login
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"twofactor_test@aloute.edu.vn\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.data.twoFactorType").value("EMAIL"))
                .andReturn();

        assertThat(emailSink.awaitOtpCount(1, 3, TimeUnit.SECONDS)).isTrue();
        String sentOtp = emailSink.getOtpSink().get(0).otpCode();

        String responseJson = loginResult.getResponse().getContentAsString();
        String ticket = responseJson.replaceAll(".*\"twoFactorTicket\":\"([^\"]+)\".*", "$1");
        assertThat(ticket).isNotBlank();

        // Step 2: Re-submitting login immediately does not crash with 429, keeps existing OTP
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"twofactor_test@aloute.edu.vn\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorRequired").value(true));

        // Step 3: Verify login with the received OTP
        mockMvc.perform(post("/api/auth/2fa/verify-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorTicket\":\"" + ticket + "\",\"code\":\"" + sentOtp + "\",\"isBackupCode\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // Step 4: Using the same OTP a second time fails (single-use burned)
        String newTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "EMAIL", 300);
        mockMvc.perform(post("/api/auth/2fa/verify-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorTicket\":\"" + newTicket + "\",\"code\":\"" + sentOtp + "\",\"isBackupCode\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_2FA_INVALID_CODE"));
    }
}
