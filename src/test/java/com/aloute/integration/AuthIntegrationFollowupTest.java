package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.dto.request.VerifyTwoFactorLoginRequest;
import com.aloute.entity.PasswordResetToken;
import com.aloute.entity.User;
import com.aloute.repository.PasswordResetTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.crypto.TwoFactorEncryptionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.PasswordResetService;
import com.aloute.service.TotpService;
import com.aloute.service.TwoFactorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration follow-ups found during the release audit:
 * 1. A completed password reset proves ownership and must clear login-lockout
 *    state (previously the user stayed locked out of their fresh password).
 * 2. An undecryptable 2FA secret (key rotation / corruption) must fail
 *    verification cleanly on the login path instead of surfacing a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AuthIntegrationFollowupTest {

    private static final String PASSWORD = "Correct@Password1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private TotpService totpService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TwoFactorEncryptionService twoFactorEncryptionService;

    private User user;
    private String email;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        email = "followup_" + suffix + "@aloute.test";
        User u = new User();
        u.setUsername("followup_" + suffix);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        user = userRepository.saveAndFlush(u);
    }

    private void failedLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private String sha256Hex(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    @DisplayName("Locked account unlocks immediately after a completed password reset")
    void passwordResetClearsLockout() throws Exception {
        for (int i = 0; i < 5; i++) {
            failedLogin();
        }
        entityManager.clear();
        // Account is now locked: even the correct password is refused with 429.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests());

        // Complete the real reset flow with a directly seeded, still-valid token.
        String rawToken = "raw-reset-token-" + System.nanoTime();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(userRepository.findByEmailIgnoreCase(email).orElseThrow());
        token.setTokenHash(sha256Hex(rawToken));
        token.setAuthVersion(0L);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        tokenRepository.saveAndFlush(token);

        passwordResetService.resetPassword(new ResetPasswordRequest(rawToken, "NewPassword@123", "NewPassword@123"));
        entityManager.clear();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"NewPassword@123\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Undecryptable 2FA secret answers 401 on verifyLogin, not 500")
    void undecryptableSecretFailsCleanly() {
        // Simulate a master-key rotation: the stored secret was encrypted under a
        // different key than the running application's configured one.
        TwoFactorEncryptionService otherKeyService =
                new TwoFactorEncryptionService("a-completely-different-master-key", new MockEnvironment());
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String corrupted = otherKeyService.encrypt(plainSecret);

        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setTwoFactorEnabled(true);
        u.setTwoFactorSecret(corrupted);
        u.setTwoFactorConfirmedAt(Instant.now());
        u.setTwoFactorType("TOTP");
        userRepository.saveAndFlush(u);
        entityManager.clear();

        String ticket = jwtTokenProvider.generateTwoFactorTicket(u.getId(), "TOTP", 300, 0L);
        Throwable thrown = catchThrowableOfType(
                () -> twoFactorService.verifyLogin(new VerifyTwoFactorLoginRequest(
                        ticket, totpService.generateCurrentCode(plainSecret), false)),
                ApiException.class);
        assertThat(thrown).isNotNull();
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((ApiException) thrown).getCode()).isEqualTo("AUTH_2FA_INVALID_CODE");
    }
}
