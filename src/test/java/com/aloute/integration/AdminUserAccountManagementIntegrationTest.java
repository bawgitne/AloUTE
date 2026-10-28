package com.aloute.integration;

import com.aloute.config.DevTestDataInitializer;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Mute;
import com.aloute.entity.Profile;
import com.aloute.entity.ReportReason;
import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.MuteRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-context tests for the three System-admin account-management APIs: warning,
 * confirmed profile reset, and global-role change. Uses real JWT cookies, H2 persistence,
 * the real notification pipeline and the shared database lock guard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminUserAccountManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private MuteRepository muteRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "Str0ng@Pass123";

    private Cookie adminCookie;
    private Long adminId;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminCookie = login(DevTestDataInitializer.ADMIN_EMAIL, DevTestDataInitializer.ADMIN_PASSWORD);
        adminId = userRepository.findByEmailIgnoreCase(DevTestDataInitializer.ADMIN_EMAIL).orElseThrow().getId();
    }

    private User createUser(String label) {
        return transactionTemplate.execute(status -> {
            long suffix = System.nanoTime();
            User user = new User();
            user.setUsername("acct_" + label + "_" + suffix);
            user.setEmail("acct_" + label + "_" + suffix + "@aloute.test");
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setVerified(true);
            user.setActive(true);
            user.setRole(Role.USER);
            user.setAuthVersion(0L);
            user = userRepository.save(user);

            Profile profile = new Profile();
            profile.setUser(user);
            profile.setDisplayName("Display " + label);
            profile.setBio("Bio " + label);
            profile.setWebsite("https://" + label + ".example.test");
            profile.setAvatarUrl("/api/media/file/" + label + "-avatar");
            profile.setCoverUrl("/api/media/file/" + label + "-cover");
            profile.setFollowersCount(11);
            profile.setFollowingCount(12);
            profile.setPostsCount(13);
            profileRepository.save(profile);
            return user;
        });
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow();
        return new Cookie("aloute_access_token", token);
    }

    private MvcResult warn(Cookie actor, Long targetId, ReportReason reason, String detail) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + targetId + "/warn")
                        .cookie(actor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"%s\",\"reasonDetail\":\"%s\"}".formatted(reason, detail)))
                .andReturn();
    }

    private MvcResult reset(Cookie actor, Long targetId, String confirmUsername, String detail) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + targetId + "/reset-profile")
                        .cookie(actor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OTHER\",\"reasonDetail\":\"%s\",\"confirmUsername\":\"%s\"}"
                                .formatted(detail, confirmUsername)))
                .andReturn();
    }

    private MvcResult changeRole(Cookie actor, Long targetId, String newRole, String detail) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + targetId + "/role")
                        .cookie(actor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"%s\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"%s\"}"
                                .formatted(newRole, detail)))
                .andReturn();
    }

    private List<ModerationAction> actionsFor(Long userId) {
        return moderationActionRepository.findByTargetTypeAndTargetId(
                ModerationTargetType.USER, userId, PageRequest.of(0, 100)).getContent();
    }

    private JsonNode notifications(Cookie targetCookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications")
                        .param("filter", "ALL").param("page", "0").param("size", "50")
                        .cookie(targetCookie))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("warning persists audit + notification, does not change state, and survives a mute of the admin")
    void warningIsAdvisoryAndDeliveredEvenWhenAdminMuted() throws Exception {
        User target = createUser("warn");
        Cookie targetCookie = login(target.getEmail(), PASSWORD);

        // The recipient mutes the acting administrator; an administrative warning must
        // still be persisted through the normal notification pipeline.
        transactionTemplate.executeWithoutResult(status -> muteRepository.save(
                Mute.builder().muter(userRepository.findById(target.getId()).orElseThrow())
                        .muted(userRepository.findById(adminId).orElseThrow()).build()));

        MvcResult result = warn(adminCookie, target.getId(), ReportReason.HARASSMENT,
                "Cảnh báo hành vi quấy rối trong bình luận.");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("actionType").asText()).isEqualTo("USER_WARN");
        assertThat(data.path("userId").asLong()).isEqualTo(target.getId());
        assertThat(data.path("notificationId").asLong()).isPositive();

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
        assertThat(reloaded.getAuthVersion()).isZero();
        mockMvc.perform(get("/api/users/me/profile").cookie(targetCookie)).andExpect(status().isOk());

        List<ModerationAction> actions = actionsFor(target.getId());
        assertThat(actions).anyMatch(a -> "USER_WARN".equals(a.getActionType())
                && a.getActor().getId().equals(adminId)
                && a.getReasonCode() == ReportReason.HARASSMENT);

        JsonNode dataNotifications = notifications(targetCookie);
        boolean found = false;
        for (JsonNode notification : dataNotifications) {
            if ("USER_WARN".equals(notification.path("type").asText())
                    && notification.path("targetId").asLong() == target.getId()) {
                found = true;
                assertThat(notification.path("actorId").asLong()).isEqualTo(adminId);
                assertThat(notification.path("contentPreview").asText()).isNotBlank();
            }
        }
        assertThat(found).as("USER_WARN notification persisted").isTrue();
    }

    @Test
    @DisplayName("admin detail exposes the global role and viewer-aware capabilities")
    void detailExposesRoleAndViewerAwareCapabilities() throws Exception {
        User target = createUser("detail");
        MvcResult other = mockMvc.perform(get("/api/admin/users/" + target.getId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode otherData = objectMapper.readTree(other.getResponse().getContentAsString()).path("data");
        assertThat(otherData.path("role").asText()).isEqualTo("USER");
        assertThat(otherData.path("capabilities").path("canWarn").asBoolean()).isTrue();
        assertThat(otherData.path("capabilities").path("canResetProfile").asBoolean()).isTrue();
        assertThat(otherData.path("capabilities").path("canChangeGlobalRole").asBoolean()).isTrue();

        MvcResult self = mockMvc.perform(get("/api/admin/users/" + adminId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode selfData = objectMapper.readTree(self.getResponse().getContentAsString()).path("data");
        assertThat(selfData.path("role").asText()).isEqualTo("SYSTEM_ADMIN");
        assertThat(selfData.path("capabilities").path("canWarn").asBoolean()).isFalse();
        assertThat(selfData.path("capabilities").path("canSuspend").asBoolean()).isFalse();
        assertThat(selfData.path("capabilities").path("canChangeGlobalRole").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("warning rejects self, normal users, missing users and invalid payloads")
    void warningAuthorizationAndValidation() throws Exception {
        User normal = createUser("warn_nosy");
        Cookie normalCookie = login(normal.getEmail(), PASSWORD);

        assertThat(warn(adminCookie, adminId, ReportReason.OTHER, "Tự cảnh báo chính mình.").getResponse().getStatus())
                .isEqualTo(409);
        assertThat(warn(normalCookie, adminId, ReportReason.OTHER, "Không có quyền cảnh báo.").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(warn(adminCookie, 99999999L, ReportReason.OTHER, "Tài khoản không tồn tại.").getResponse().getStatus())
                .isEqualTo(404);

        MvcResult invalid = mockMvc.perform(post("/api/admin/users/" + normal.getId() + "/warn")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonDetail\":\"thiếu reason\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertThat(objectMapper.readTree(invalid.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("profile reset requires username confirmation, clears only visible fields and keeps the session/content")
    void profileResetIsExactlyScopedAndSessionPreserving() throws Exception {
        User target = createUser("reset");
        Cookie targetCookie = login(target.getEmail(), PASSWORD);

        MvcResult postResult = mockMvc.perform(post("/api/posts")
                        .cookie(targetCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Bài viết phải được giữ sau reset hồ sơ.\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long postId = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult mismatch = reset(adminCookie, target.getId(), "not-" + target.getUsername(),
                "Đặt lại hồ sơ theo yêu cầu kiểm duyệt.");
        assertThat(mismatch.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(mismatch.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("USER_PROFILE_RESET_CONFIRMATION_MISMATCH");
        assertThat(profileRepository.findById(target.getId()).orElseThrow().getBio()).isEqualTo("Bio reset");

        MvcResult resetResult = reset(adminCookie, target.getId(), target.getUsername(),
                "Đặt lại hồ sơ theo yêu cầu kiểm duyệt.");
        assertThat(resetResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = objectMapper.readTree(resetResult.getResponse().getContentAsString()).path("data");
        assertThat(response.path("actionType").asText()).isEqualTo("USER_PROFILE_RESET");
        assertThat(response.path("displayName").asText()).isEqualTo(target.getUsername());
        assertThat(response.path("resetFields").toString())
                .contains("displayName", "bio", "website", "avatarUrl", "coverUrl");

        User reloadedUser = userRepository.findById(target.getId()).orElseThrow();
        Profile reloaded = profileRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo(target.getUsername());
        assertThat(reloaded.getBio()).isNull();
        assertThat(reloaded.getWebsite()).isNull();
        assertThat(reloaded.getAvatarUrl()).isNull();
        assertThat(reloaded.getCoverUrl()).isNull();
        assertThat(reloaded.getFollowersCount()).isEqualTo(11);
        assertThat(reloaded.getFollowingCount()).isEqualTo(12);
        assertThat(reloaded.getPostsCount()).isEqualTo(13);
        assertThat(reloadedUser.getEmail()).isEqualTo(target.getEmail());
        assertThat(reloadedUser.getPasswordHash()).isEqualTo(target.getPasswordHash());
        assertThat(reloadedUser.getRole()).isEqualTo(Role.USER);
        assertThat(reloadedUser.isActive()).isTrue();
        assertThat(reloadedUser.getAuthVersion()).isZero();

        // Session and content survive a profile reset.
        mockMvc.perform(get("/api/users/me/profile").cookie(targetCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + postId).cookie(targetCookie)).andExpect(status().isOk());
        List<ModerationAction> actions = actionsFor(target.getId());
        assertThat(actions).anyMatch(a -> "USER_PROFILE_RESET".equals(a.getActionType())
                && a.getMetadata() != null && a.getMetadata().contains("coverUrl"));
    }

    @Test
    @DisplayName("role promotion revokes the old JWT and grants admin access only after fresh login")
    void rolePromotionRevokesOldJwtAndGrantsNewAuthority() throws Exception {
        User target = createUser("promote");
        Cookie oldTargetCookie = login(target.getEmail(), PASSWORD);

        MvcResult promote = changeRole(adminCookie, target.getId(), "SYSTEM_ADMIN",
                "Bổ nhiệm quản trị viên hệ thống theo quyết định của ban quản trị.");
        assertThat(promote.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(promote.getResponse().getContentAsString()).path("data");
        assertThat(data.path("previousRole").asText()).isEqualTo("USER");
        assertThat(data.path("newRole").asText()).isEqualTo("SYSTEM_ADMIN");
        assertThat(data.path("actionType").asText()).isEqualTo("USER_ROLE_CHANGE");

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(reloaded.getAuthVersion()).isEqualTo(1L);

        // The old token still carries USER; the authVersion bump must fail it closed.
        mockMvc.perform(get("/api/admin/users").cookie(oldTargetCookie)).andExpect(status().isUnauthorized());

        Cookie freshTargetCookie = login(target.getEmail(), PASSWORD);
        mockMvc.perform(get("/api/admin/users").cookie(freshTargetCookie)).andExpect(status().isOk());

        List<ModerationAction> actions = actionsFor(target.getId());
        assertThat(actions).anyMatch(a -> "USER_ROLE_CHANGE".equals(a.getActionType())
                && a.getMetadata() != null
                && a.getMetadata().contains("\"previousRole\":\"USER\"")
                && a.getMetadata().contains("\"newRole\":\"SYSTEM_ADMIN\""));
    }

    @Test
    @DisplayName("role change rejects self, inactive, no-op and unknown roles; normal users get 403")
    void roleChangeGuards() throws Exception {
        User target = createUser("role_guards");
        Cookie normalCookie = login(target.getEmail(), PASSWORD);

        assertThat(changeRole(adminCookie, adminId, "USER", "Tự đổi vai trò của chính mình.").getResponse().getStatus())
                .isEqualTo(409);

        MvcResult noop = changeRole(adminCookie, target.getId(), "USER", "Không thay đổi vai trò thực tế.");
        assertThat(noop.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(noop.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("USER_ROLE_UNCHANGED");

        assertThat(changeRole(normalCookie, target.getId(), "SYSTEM_ADMIN",
                "Người dùng thường không được đổi vai trò.").getResponse().getStatus()).isEqualTo(403);

        // Suspend the target, then role change must require reactivation first.
        MvcResult suspended = mockMvc.perform(post("/api/admin/users/" + target.getId() + "/suspend")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OTHER\",\"reasonDetail\":\"Tạm đình chỉ để kiểm thử role inactive.\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(suspended.getResponse().getStatus()).isEqualTo(200);
        MvcResult inactive = changeRole(adminCookie, target.getId(), "SYSTEM_ADMIN",
                "Thử đổi vai trò tài khoản đang bị đình chỉ.");
        assertThat(inactive.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(inactive.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("USER_ROLE_CHANGE_REQUIRES_ACTIVE");

        MvcResult invalidRole = mockMvc.perform(post("/api/admin/users/" + target.getId() + "/role")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"SUPERUSER\",\"reasonCode\":\"OTHER\",\"reasonDetail\":\"Vai trò không tồn tại.\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(objectMapper.readTree(invalidRole.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("BAD_REQUEST");
    }
}
