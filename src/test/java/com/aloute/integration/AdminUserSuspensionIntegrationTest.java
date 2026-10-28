package com.aloute.integration;

import com.aloute.config.DevTestDataInitializer;
import com.aloute.entity.Profile;
import com.aloute.entity.ReportReason;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration tests for System-admin account suspension:
 * real @PreAuthorize, real JWT session revocation, and real audit persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminUserSuspensionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Cookie adminCookie;
    private Long adminId;

    private static final String TARGET_PASSWORD = "Str0ng@Pass123";

    @BeforeEach
    void loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(
                                DevTestDataInitializer.ADMIN_EMAIL, DevTestDataInitializer.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow();
        adminCookie = new Cookie("aloute_access_token", token);
        adminId = userRepository.findByEmailIgnoreCase(DevTestDataInitializer.ADMIN_EMAIL).orElseThrow().getId();
    }

    private User createUser(String label) {
        long suffix = System.nanoTime();
        // Persist the user + profile in one committed transaction so the profile's
        // @MapsId association is resolved while the user is still managed.
        return transactionTemplate.execute(status -> {
            User u = new User();
            u.setUsername("susp_" + label + "_" + suffix);
            u.setEmail("susp_" + label + "_" + suffix + "@aloute.test");
            u.setPasswordHash(passwordEncoder.encode(TARGET_PASSWORD));
            u.setVerified(true);
            u.setActive(true);
            u.setRole(Role.USER);
            u = userRepository.save(u);
            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Target " + label);
            profileRepository.save(p);
            return u;
        });
    }

    private Cookie login(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TARGET_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = r.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow();
        return new Cookie("aloute_access_token", token);
    }

    private String body(ReportReason reason) {
        return "{\"reasonCode\":\"%s\",\"reasonDetail\":\"Vi phạm quy định cộng đồng nghiêm trọng.\"}".formatted(reason);
    }

    @Test
    @DisplayName("admin suspends a user: revoked sessions + audit row + status reflected in list")
    void adminSuspendRevokesSessionsAndAudits() throws Exception {
        User target = createUser("victim");
        Cookie targetCookie = login(target.getEmail());

        // Target works before suspension.
        mockMvc.perform(get("/api/users/me/profile").cookie(targetCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/suspend")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.HARASSMENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.actionType").value("USER_SUSPEND"));

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getAuthVersion()).isEqualTo(1L);

        // The target's outstanding token is now rejected.
        mockMvc.perform(get("/api/users/me/profile").cookie(targetCookie))
                .andExpect(status().isUnauthorized());

        // Re-login is refused for a suspended account.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(target.getEmail(), TARGET_PASSWORD)))
                .andExpect(status().is4xxClientError());

        // An audit action is recorded against the target user.
        assertThat(moderationActionRepository.findByTargetTypeAndTargetId(
                com.aloute.entity.ModerationTargetType.USER, target.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .anyMatch(a -> "USER_SUSPEND".equals(a.getActionType()));
    }

    @Test
    @DisplayName("reactivating a suspended account restores access after a fresh login")
    void adminReactivateRestoresAccess() throws Exception {
        User target = createUser("returning");
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/suspend")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.SPAM)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/reactivate")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.actionType").value("USER_REACTIVATE"));

        Cookie fresh = login(target.getEmail());
        mockMvc.perform(get("/api/users/me/profile").cookie(fresh))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an administrator cannot suspend their own account via the API")
    void selfSuspensionRefused() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + adminId + "/suspend")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.OTHER)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("suspending a nonexistent user returns 404")
    void missingUserReturns404() throws Exception {
        mockMvc.perform(post("/api/admin/users/99999999/suspend")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.OTHER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a normal user cannot reach the suspension endpoint")
    void nonAdminIsForbidden() throws Exception {
        User normal = createUser("nosy");
        Cookie normalCookie = login(normal.getEmail());

        mockMvc.perform(post("/api/admin/users/" + normal.getId() + "/suspend")
                        .cookie(normalCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ReportReason.OTHER)))
                .andExpect(status().isForbidden());
    }
}
