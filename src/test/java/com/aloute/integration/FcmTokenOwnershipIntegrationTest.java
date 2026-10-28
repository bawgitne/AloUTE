package com.aloute.integration;

import com.aloute.entity.User;
import com.aloute.repository.UserFcmTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-10: FCM token lifecycle is per-owner. Only the owning user may unregister a
 * token, deletion must not be a global deleteByToken, and persistence must live
 * behind a service (controllers never touch repositories).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class FcmTokenOwnershipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserFcmTokenRepository fcmTokenRepository;

    private User userA;
    private User userB;
    private Authentication authA;
    private Authentication authB;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        userA = newUser("fcmtok_a_" + suffix);
        userB = newUser("fcmtok_b_" + suffix);
        authA = authOf(userA);
        authB = authOf(userB);
    }

    private User newUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@aloute.test");
        u.setPasswordHash("$2a$10$placeholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        return userRepository.saveAndFlush(u);
    }

    private Authentication authOf(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private String body(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private void register(Authentication auth, String token) throws Exception {
        mockMvc.perform(post("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token))
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Owner can delete their own token: 200 and row removed")
    void ownerDeletesOwnToken() throws Exception {
        register(authA, "token-A");
        assertThat(fcmTokenRepository.findByToken("token-A")).isPresent();

        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("token-A"))
                        .with(authentication(authA))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(fcmTokenRepository.findByToken("token-A")).isEmpty();
    }

    @Test
    @DisplayName("BL-10: foreign user deleting someone else's token gets 404 and the token survives")
    void foreignUserCannotDeleteOthersToken() throws Exception {
        register(authB, "token-B");

        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("token-B"))
                        .with(authentication(authA))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(fcmTokenRepository.findByToken("token-B")).isPresent();
        assertThat(fcmTokenRepository.findByToken("token-B").orElseThrow().getUserId())
                .isEqualTo(userB.getId());
    }

    @Test
    @DisplayName("Deleting an unknown token returns 404")
    void deleteNonexistentTokenReturns404() throws Exception {
        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("no-such-token"))
                        .with(authentication(authA))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Duplicate registration by the same user keeps a single row")
    void duplicateRegistrationStaysSingleRow() throws Exception {
        register(authA, "dup-token");
        register(authA, "dup-token");

        assertThat(fcmTokenRepository.findByUserId(userA.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Re-registering a shared device token reassigns it to the current user")
    void registrationReassignsSharedDeviceTokenToCurrentUser() throws Exception {
        register(authA, "shared-device-token");
        register(authB, "shared-device-token");

        assertThat(fcmTokenRepository.findByToken("shared-device-token").orElseThrow().getUserId())
                .isEqualTo(userB.getId());
    }

    @Test
    @DisplayName("Guest cannot register or unregister FCM tokens: 401")
    void guestCannotMutateFcmTokens() throws Exception {
        mockMvc.perform(post("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("guest-token"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("guest-token"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
