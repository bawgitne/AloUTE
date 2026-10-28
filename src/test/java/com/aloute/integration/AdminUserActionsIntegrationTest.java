package com.aloute.integration;

import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Notification;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backend for the /admin/users/{userId} account-management actions: warn,
 * profile reset and global role change (plus suspend/reactivate compatibility).
 *
 * Authorization is exercised with REAL signed JWTs obtained through
 * {@code POST /api/auth/login}, not mocks, and the audit / notification /
 * revocation side effects are asserted against the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminUserActionsIntegrationTest {

    private static final String PASSWORD = "Adm1n@Pass123";
    private static final String REASON_DETAIL = "Quy định cộng đồng yêu cầu lý do chi tiết cho mọi thao tác.";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    private User admin;
    private User target;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUpUsers() {
        tx = new TransactionTemplate(transactionManager);
        admin = createUser(Role.SYSTEM_ADMIN, "adm");
        target = createUser(Role.USER, "tgt");
        adminToken = loginAndGetBearer(admin.getEmail());
        userToken = loginAndGetBearer(target.getEmail());
    }

    @AfterEach
    void demoteCreatedAdmins() {
        // Keep later suites from seeing extra active SYSTEM_ADMINs.
        for (User u : List.of(admin, target)) {
            User fresh = userRepository.findById(u.getId()).orElse(null);
            if (fresh != null && fresh.getRole() == Role.SYSTEM_ADMIN) {
                fresh.setRole(Role.USER);
                fresh.setActive(false);
                userRepository.save(fresh);
            }
        }
    }

    private User createUser(Role role, String label) {
        long suffix = System.nanoTime();
        return tx.execute(status -> {
            User u = new User();
            u.setUsername(label + "_" + suffix);
            u.setEmail(label + "_" + suffix + "@aloute.test");
            u.setPasswordHash(passwordEncoder.encode(PASSWORD));
            u.setVerified(true);
            u.setActive(true);
            u.setRole(role);
            u.setAuthVersion(0L);
            u = userRepository.save(u);
            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Display " + label);
            profileRepository.save(p);
            return u;
        });
    }

    private String loginAndGetBearer(String email) {
        try {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            return result.getResponse().getHeaders("Set-Cookie").stream()
                    .filter(c -> c.startsWith("aloute_access_token="))
                    .findFirst()
                    .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                    .orElseThrow(() -> new IllegalStateException("missing aloute_access_token cookie"));
        } catch (Exception ex) {
            throw new IllegalStateException("login failed for " + email, ex);
        }
    }

    private MvcResult postAction(String path, Long userId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + userId + "/" + path)
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String moderationBody() {
        return "{\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}";
    }

    private String roleBody(String newRole) {
        return "{\"newRole\":\"" + newRole + "\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}";
    }

    private List<com.aloute.entity.ModerationAction> actionsFor(Long userId, String actionType) {
        return moderationActionRepository
                .findByTargetTypeAndTargetId(ModerationTargetType.USER, userId, PageRequest.of(0, 100))
                .getContent().stream()
                .filter(a -> actionType.equals(a.getActionType()))
                .toList();
    }

    private List<Notification> notificationsFor(Long recipientId, String type) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, PageRequest.of(0, 100))
                .getContent().stream()
                .filter(n -> type.equals(n.getType()))
                .toList();
    }

    // ------------------------------------------------------------------ warn

    @Test
    @DisplayName("WARN: admin warns a user - audit row and mandatory notification both persisted")
    void warnPersistsAuditAndNotification() throws Exception {
        MvcResult result = postAction("warn", target.getId(), adminToken, moderationBody());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("USER_WARN");

        var actions = actionsFor(target.getId(), "USER_WARN");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getReasonCode()).isEqualTo(com.aloute.entity.ReportReason.OTHER);
        assertThat(actions.get(0).getReasonDetail()).isEqualTo(REASON_DETAIL);
        assertThat(actions.get(0).getActor().getId()).isEqualTo(admin.getId());

        List<Notification> notices = notificationsFor(target.getId(), "ADMIN_WARN");
        assertThat(notices).as("the warned account must receive the notice").hasSize(1);
        assertThat(notices.get(0).getActor().getId()).isEqualTo(admin.getId());
        assertThat(notices.get(0).getTargetType()).isEqualTo("USER");

        // Advisory only: nothing about the account state changed.
        User fresh = userRepository.findById(target.getId()).orElseThrow();
        assertThat(fresh.isActive()).isTrue();
        assertThat(fresh.getAuthVersion()).isZero();
    }

    @Test
    @DisplayName("WARN: refused for anonymous, non-admin JWT and the admin's own account")
    void warnAuthorization() throws Exception {
        assertThat(postAction("warn", target.getId(), null, moderationBody())
                .getResponse().getStatus()).isEqualTo(401);

        // Real non-admin JWT through the same filter chain.
        assertThat(postAction("warn", admin.getId(), userToken, moderationBody())
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(actionsFor(admin.getId(), "USER_WARN")).isEmpty();

        assertThat(postAction("warn", admin.getId(), adminToken, moderationBody())
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(actionsFor(admin.getId(), "USER_WARN")).isEmpty();
        assertThat(notificationsFor(admin.getId(), "ADMIN_WARN")).isEmpty();
    }

    @Test
    @DisplayName("WARN: reason code and detail are mandatory; a muted actor cannot silence the notice")
    void warnValidationAndMuteImmunity() throws Exception {
        assertThat(postAction("warn", target.getId(), adminToken,
                "{\"reasonDetail\":\"" + REASON_DETAIL + "\"}").getResponse().getStatus()).isEqualTo(422);
        assertThat(postAction("warn", target.getId(), adminToken,
                "{\"reasonCode\":\"OTHER\",\"reasonDetail\":\"ngắn\"}").getResponse().getStatus()).isEqualTo(422);
        assertThat(actionsFor(target.getId(), "USER_WARN")).isEmpty();

        // Target mutes the admin: an official warning must still be delivered.
        Long adminId = admin.getId();
        Long targetId = target.getId();
        mockMvc.perform(post("/api/social/mutes/by-username/" + admin.getUsername())
                        .header("Authorization", "Bearer " + userToken)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(postAction("warn", targetId, adminToken, moderationBody())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(notificationsFor(targetId, "ADMIN_WARN"))
                .as("moderation notices bypass the recipient's mute of the admin")
                .hasSize(1);
    }

    // ---------------------------------------------------------- profile reset

    @Test
    @DisplayName("RESET: exactly the display profile fields are cleared, everything else preserved")
    void resetScopeAndPreservation() throws Exception {
        Profile p = profileRepository.findById(target.getId()).orElseThrow();
        p.setBio("bio trước reset");
        p.setWebsite("https://example.test/old");
        p.setAvatarUrl("https://cdn.example.test/a.png");
        p.setCoverUrl("https://cdn.example.test/c.png");
        p.setFollowersCount(7);
        p.setFollowingCount(9);
        p.setPostsCount(11);
        profileRepository.save(p);
        String passwordHashBefore = userRepository.findById(target.getId()).orElseThrow().getPasswordHash();

        MvcResult result = postAction("reset-profile", target.getId(), adminToken, moderationBody());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("USER_PROFILE_RESET");

        Profile after = profileRepository.findById(target.getId()).orElseThrow();
        assertThat(after.getDisplayName()).isEqualTo(target.getUsername());
        assertThat(after.getBio()).isNull();
        assertThat(after.getWebsite()).isNull();
        assertThat(after.getAvatarUrl()).isNull();
        assertThat(after.getCoverUrl()).isNull();
        // Counters and relationships are not part of the reset scope.
        assertThat(after.getFollowersCount()).isEqualTo(7L);
        assertThat(after.getFollowingCount()).isEqualTo(9L);
        assertThat(after.getPostsCount()).isEqualTo(11L);

        User afterUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(afterUser.getEmail()).isEqualTo(target.getEmail());
        assertThat(afterUser.getPasswordHash()).isEqualTo(passwordHashBefore);
        assertThat(afterUser.getRole()).isEqualTo(Role.USER);
        assertThat(afterUser.isActive()).isTrue();
        // Sessions survive a profile reset.
        assertThat(afterUser.getAuthVersion()).isZero();

        var actions = actionsFor(target.getId(), "USER_PROFILE_RESET");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getMetadata()).contains("displayName").contains("coverUrl");
    }

    @Test
    @DisplayName("RESET: self-reset refused; only the addressed account is touched")
    void resetRefusalsAndIsolation() throws Exception {
        Profile adminProfile = profileRepository.findById(admin.getId()).orElseThrow();
        adminProfile.setBio("admin bio");
        profileRepository.save(adminProfile);

        assertThat(postAction("reset-profile", admin.getId(), adminToken, moderationBody())
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(profileRepository.findById(admin.getId()).orElseThrow().getBio()).isEqualTo("admin bio");

        // A bystander account with the same display name is untouched.
        User bystander = createUser(Role.USER, "by");
        assertThat(postAction("reset-profile", target.getId(), adminToken, moderationBody())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(profileRepository.findById(bystander.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Display by");
    }

    @Test
    @DisplayName("RESET: legacy account without a profile row still audits the action")
    void resetWithMissingProfileRow() throws Exception {
        // Seed a reset-audited target without a profile row.
        long suffix = System.nanoTime();
        User orphan = new User();
        orphan.setUsername("orphan_" + suffix);
        orphan.setEmail("orphan_" + suffix + "@aloute.test");
        orphan.setPasswordHash(passwordEncoder.encode(PASSWORD));
        orphan.setVerified(true);
        orphan.setActive(true);
        orphan.setRole(Role.USER);
        orphan = userRepository.save(orphan);

        MvcResult result = postAction("reset-profile", orphan.getId(), adminToken, moderationBody());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var actions = actionsFor(orphan.getId(), "USER_PROFILE_RESET");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getMetadata()).contains("profileRowMissing");
    }

    // ---------------------------------------------------------- global role

    @Test
    @DisplayName("ROLE: promotion audited with old/new role and revokes outstanding tokens")
    void promoteUserToAdmin() throws Exception {
        String oldToken = loginAndGetBearer(target.getEmail());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());

        MvcResult result = postAction("role", target.getId(), adminToken,
                "{\"newRole\":\"SYSTEM_ADMIN\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"oldRole\":\"USER\"").contains("\"newRole\":\"SYSTEM_ADMIN\"");

        User fresh = userRepository.findById(target.getId()).orElseThrow();
        assertThat(fresh.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(fresh.getAuthVersion()).isEqualTo(1L);

        var actions = actionsFor(target.getId(), "USER_ROLE_CHANGE");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getMetadata()).contains("oldRole").contains("newRole");

        // The pre-change token must no longer authenticate (authVersion bumped).
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ROLE: demotion works with an active peer and keeps the last-admin invariant")
    void demoteAdminWithPeer() throws Exception {
        // A second, active admin so the demotion cannot empty the admin set.
        User peer = createUser(Role.SYSTEM_ADMIN, "peer");

        MvcResult result = postAction("role", target.getId(), adminToken,
                "{\"newRole\":\"USER\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}");
        // target is a USER already -> no-op conflict instead.
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("ADMIN_ROLE_UNCHANGED");

        MvcResult demotePeer = postAction("role", peer.getId(), adminToken,
                "{\"newRole\":\"USER\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}");
        assertThat(demotePeer.getResponse().getStatus()).isEqualTo(200);
        assertThat(demotePeer.getResponse().getContentAsString())
                .contains("\"oldRole\":\"SYSTEM_ADMIN\"").contains("\"newRole\":\"USER\"");

        User demoted = userRepository.findById(peer.getId()).orElseThrow();
        assertThat(demoted.getRole()).isEqualTo(Role.USER);
        assertThat(demoted.isActive()).isTrue();
        assertThat(demoted.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ROLE: self role change is refused with no audit and no state change")
    void selfRoleChangeRefused() throws Exception {
        long versionBefore = userRepository.findById(admin.getId()).orElseThrow().getAuthVersion();

        MvcResult result = postAction("role", admin.getId(), adminToken,
                "{\"newRole\":\"USER\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("USER_SELF_MODERATION_NOT_ALLOWED");
        assertThat(actionsFor(admin.getId(), "USER_ROLE_CHANGE")).isEmpty();
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getAuthVersion()).isEqualTo(versionBefore);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        // The last-admin-demote race (which no single HTTP caller can reach, because the
        // demoting actor is always an active admin themself) is covered by the
        // SystemAdminInvariantIntegrationTest concurrency suite on H2 and MySQL 8.
    }

    @Test
    @DisplayName("ROLE: invalid role value, inactive target and non-admin JWT are refused")
    void roleValidationAndAuthorization() throws Exception {
        assertThat(postAction("role", target.getId(), adminToken, roleBody("SUPERADMIN"))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(postAction("role", target.getId(), adminToken, roleBody("unknown-role"))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(actionsFor(target.getId(), "USER_ROLE_CHANGE")).isEmpty();

        // Inactive target.
        User fresh = userRepository.findById(target.getId()).orElseThrow();
        fresh.setActive(false);
        userRepository.save(fresh);
        MvcResult inactive = postAction("role", target.getId(), adminToken,
                "{\"newRole\":\"SYSTEM_ADMIN\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"" + REASON_DETAIL + "\"}");
        assertThat(inactive.getResponse().getStatus()).isEqualTo(409);
        assertThat(inactive.getResponse().getContentAsString()).contains("ADMIN_TARGET_INACTIVE");

        // Non-admin real JWT and anonymous (full valid body so authorization is
        // what is actually being exercised, not bean validation). `target`'s own
        // token is unusable here: it was just deactivated above.
        User bystander = createUser(Role.USER, "by2");
        String bystanderToken = loginAndGetBearer(bystander.getEmail());
        assertThat(postAction("role", bystander.getId(), bystanderToken, roleBody("USER"))
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(postAction("role", bystander.getId(), null, roleBody("USER"))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(actionsFor(bystander.getId(), "USER_ROLE_CHANGE")).isEmpty();
    }

    // ------------------------------------------------ suspend/reactivate compat

    @Test
    @DisplayName("SUSPEND: #95 contract unchanged alongside the new actions")
    void suspendReactivateStillWorks() throws Exception {
        MvcResult suspend = postAction("suspend", target.getId(), adminToken, moderationBody());
        assertThat(suspend.getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isFalse();
        assertThat(userRepository.findById(target.getId()).orElseThrow().getAuthVersion()).isEqualTo(1L);

        MvcResult reactivate = postAction("reactivate", target.getId(), adminToken, moderationBody());
        assertThat(reactivate.getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("DETAIL: capabilities advertise the three working actions and expose the role")
    void detailCapabilities() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities.canWarn").value(true))
                .andExpect(jsonPath("$.data.capabilities.canResetProfile").value(true))
                .andExpect(jsonPath("$.data.capabilities.canChangeGlobalRole").value(true))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }
}
