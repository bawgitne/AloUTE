package com.aloute.integration;

import com.aloute.entity.User;
import com.aloute.entity.UserBackupCode;
import com.aloute.repository.UserBackupCodeRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.crypto.TwoFactorEncryptionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.TotpService;
import com.aloute.service.email.TestEmailSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A1 (release closure): after a master-key rotation the stored TOTP secret can no
 * longer be decrypted. Every 2FA surface must degrade to a clean, fail-secure
 * answer instead of a 500, and the user must keep a documented way back in:
 *
 * <ul>
 *   <li>login: TOTP is treated as unverified, the email-OTP fallback still works
 *       (covered by {@code AuthIntegrationFollowupTest});</li>
 *   <li>disable: backup codes and email OTP still prove ownership, so the user can
 *       turn 2FA off and re-enroll under the new key.</li>
 * </ul>
 *
 * Fail-secure means: if no second factor can be proven, 2FA stays ENABLED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class TwoFactorKeyRotationRecoveryTest {

    private static final String PASSWORD = "Correct@Password1";
    private static final String PLAIN_SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserBackupCodeRepository userBackupCodeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TotpService totpService;
    @Autowired private TwoFactorEncryptionService twoFactorEncryptionService;
    @Autowired private TestEmailSink emailSink;
    @Autowired private ObjectMapper objectMapper;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        User u = new User();
        u.setUsername("keyrot_" + suffix);
        u.setEmail("keyrot_" + suffix + "@aloute.test");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        user = userRepository.saveAndFlush(u);
        token = jwtTokenProvider.generate(UserPrincipal.from(user));
    }

    /** Stores a secret encrypted under a key the running application does not have. */
    private void enableTwoFactorUnderForeignKey() {
        TwoFactorEncryptionService foreignKey =
                new TwoFactorEncryptionService("a-completely-different-master-key", new MockEnvironment());
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setTwoFactorEnabled(true);
        u.setTwoFactorSecret(foreignKey.encrypt(PLAIN_SECRET));
        u.setTwoFactorConfirmedAt(Instant.now());
        u.setTwoFactorType("TOTP");
        userRepository.saveAndFlush(u);
    }

    private String seedBackupCode() {
        String raw = "ABCD-EFGH";
        UserBackupCode code = new UserBackupCode();
        code.setUser(userRepository.findById(user.getId()).orElseThrow());
        code.setCodeHash(totpService.hashBackupCode(raw));
        code.setUsed(false);
        userBackupCodeRepository.saveAndFlush(code);
        return raw;
    }

    private MvcResult disable(String code) throws Exception {
        return mockMvc.perform(post("/api/users/me/2fa/disable")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"" + code + "\"}"))
                .andReturn();
    }

    private boolean twoFactorEnabled() {
        return userRepository.findById(user.getId()).orElseThrow().isTwoFactorEnabled();
    }

    private String lastOtpFor(String email) {
        return emailSink.getOtpSink().stream()
                .filter(sent -> email.equalsIgnoreCase(sent.toEmail()))
                .reduce((first, second) -> second)
                .map(TestEmailSink.SentOtp::otpCode)
                .orElseThrow(() -> new IllegalStateException("no OTP captured for " + email));
    }

    @Test
    @DisplayName("A1: disable with an undecryptable secret answers 200 via a backup code, not 500")
    void disableSucceedsWithBackupCodeAfterKeyRotation() throws Exception {
        enableTwoFactorUnderForeignKey();
        String backupCode = seedBackupCode();

        MvcResult result = disable(backupCode);

        assertThat(result.getResponse().getStatus())
                .as("disable must not surface a 500 when the TOTP secret cannot be decrypted")
                .isEqualTo(200);
        assertThat(twoFactorEnabled()).isFalse();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTwoFactorSecret()).isNull();
    }

    @Test
    @DisplayName("A1: disable with an undecryptable secret answers 200 via an email OTP")
    void disableSucceedsWithEmailOtpAfterKeyRotation() throws Exception {
        enableTwoFactorUnderForeignKey();

        mockMvc.perform(post("/api/users/me/2fa/email/send-code")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(emailSink.awaitOtpCount(1, 5, TimeUnit.SECONDS)).isTrue();
        String otp = lastOtpFor(user.getEmail());

        MvcResult result = disable(otp);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(twoFactorEnabled()).isFalse();
    }

    @Test
    @DisplayName("A1: fail-secure - an unprovable second factor keeps 2FA enabled with 400, not 500")
    void disableWithoutValidSecondFactorStaysEnabled() throws Exception {
        enableTwoFactorUnderForeignKey();

        MvcResult result = disable("000000");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.at("/error/code").asText()).isEqualTo("AUTH_2FA_INVALID_CODE");
        assertThat(twoFactorEnabled()).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTwoFactorSecret()).isNotNull();
    }

    @Test
    @DisplayName("A1: a correct key still disables 2FA with a live TOTP code (no regression)")
    void disableStillWorksWithTheCorrectKey() throws Exception {
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setTwoFactorEnabled(true);
        u.setTwoFactorSecret(twoFactorEncryptionService.encrypt(PLAIN_SECRET));
        u.setTwoFactorConfirmedAt(Instant.now());
        u.setTwoFactorType("TOTP");
        userRepository.saveAndFlush(u);

        MvcResult result = disable(totpService.generateCurrentCode(PLAIN_SECRET));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(twoFactorEnabled()).isFalse();
    }

    @Test
    @DisplayName("A1: legacy row with 2FA enabled but no stored secret still disables cleanly")
    void disableHandlesMissingStoredSecret() throws Exception {
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setTwoFactorEnabled(true);
        u.setTwoFactorSecret(null);
        u.setTwoFactorType("EMAIL");
        userRepository.saveAndFlush(u);
        String backupCode = seedBackupCode();

        MvcResult result = disable(backupCode);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(twoFactorEnabled()).isFalse();
    }

    @Test
    @DisplayName("A1: after a rotation recovery the user can re-enroll and the new secret decrypts")
    void reEnrollmentAfterRotationWorks() throws Exception {
        enableTwoFactorUnderForeignKey();
        assertThat(disable(seedBackupCode()).getResponse().getStatus()).isEqualTo(200);

        // Disabling bumps authVersion, so the pre-disable session is now stale.
        token = jwtTokenProvider.generate(
                UserPrincipal.from(userRepository.findById(user.getId()).orElseThrow()));

        // Re-enroll: setup issues a secret encrypted under the CURRENT key.
        MvcResult setup = mockMvc.perform(post("/api/users/me/2fa/setup")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String freshSecret = objectMapper.readTree(setup.getResponse().getContentAsString())
                .at("/data/secretKey").asText();

        mockMvc.perform(post("/api/users/me/2fa/enable")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + totpService.generateCurrentCode(freshSecret) + "\"}"))
                .andExpect(status().isOk());

        assertThat(twoFactorEnabled()).isTrue();
        assertThat(twoFactorEncryptionService.decrypt(
                userRepository.findById(user.getId()).orElseThrow().getTwoFactorSecret()))
                .isEqualTo(freshSecret);
    }

    @Test
    @DisplayName("A1: enabling 2FA with a temp secret encrypted under another key answers 400, not 500")
    void enableWithUndecryptableTempSecretAnswers400() throws Exception {
        TwoFactorEncryptionService foreignKey =
                new TwoFactorEncryptionService("a-completely-different-master-key", new MockEnvironment());
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setTwoFactorTempSecret(foreignKey.encrypt(PLAIN_SECRET));
        userRepository.saveAndFlush(u);

        MvcResult result = mockMvc.perform(post("/api/users/me/2fa/enable")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + totpService.generateCurrentCode(PLAIN_SECRET) + "\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.at("/error/code").asText()).isEqualTo("AUTH_2FA_SETUP_STALE");
        assertThat(twoFactorEnabled()).isFalse();
    }

    @Test
    @DisplayName("A1: status stays readable after a rotation (no decrypt on the read path)")
    void statusSurvivesRotation() throws Exception {
        enableTwoFactorUnderForeignKey();

        mockMvc.perform(get("/api/users/me/2fa/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }
}
