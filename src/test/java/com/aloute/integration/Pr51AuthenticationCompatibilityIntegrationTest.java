package com.aloute.integration;

import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class Pr51AuthenticationCompatibilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtSessionService jwtSessionService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        testUser = tx.execute(status -> {
            User u = new User();
            u.setUsername("compat_test_" + System.nanoTime());
            u.setEmail("compat_" + System.nanoTime() + "@aloute.edu.vn");
            u.setPasswordHash(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            u.setActive(true);
            u.setVerified(true);
            u.setAuthVersion(0L);
            u = userRepository.save(u);

            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Compat Test");
            profileRepository.save(p);
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        if (testUser != null && testUser.getId() != null) {
            Long userId = testUser.getId();
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.execute(status -> {
                if (profileRepository.existsById(userId)) {
                    profileRepository.deleteById(userId);
                }
                if (userRepository.existsById(userId)) {
                    userRepository.deleteById(userId);
                }
                assertThat(profileRepository.existsById(userId)).isFalse();
                assertThat(userRepository.existsById(userId)).isFalse();
                return null;
            });
            testUser = null;
        }
    }

    @Test
    @DisplayName("Positive control: Standard access token is accepted via Bearer and Cookie")
    void standardAccessToken_acceptedViaBearerAndCookie() throws Exception {
        String accessToken = jwtTokenProvider.generate(UserPrincipal.from(testUser));

        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/profile")
                        .cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Negative security gate: 2FA challenge ticket must NOT authenticate protected HTTP endpoint via Bearer or Cookie")
    void twoFactorChallengeTicket_mustBeRejectedForProtectedResource() throws Exception {
        String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUser.getId(), "TOTP", 300);

        // Verify Bearer rejection
        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + challengeTicket))
                .andExpect(status().isUnauthorized());

        // Verify Cookie rejection
        mockMvc.perform(get("/api/users/profile")
                        .cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, challengeTicket)))
                .andExpect(status().isUnauthorized());
    }
}