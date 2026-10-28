package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.admin.AdminUserProfileResetRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.dto.request.admin.AdminUserWarnRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserProfileResetResponse;
import com.aloute.dto.response.admin.AdminUserRoleChangeResponse;
import com.aloute.dto.response.admin.AdminUserWarnResponse;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.Profile;
import com.aloute.entity.ReportReason;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.NotificationService;
import com.aloute.service.admin.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the three account-management actions: warning, profile reset and
 * global-role change. Complements the real-context integration tests.
 */
class AdminUserAccountManagementServiceTest {

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private PostRepository postRepository;
    private ReportRepository reportRepository;
    private ModerationActionRepository moderationActionRepository;
    private NotificationService notificationService;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        postRepository = mock(PostRepository.class);
        reportRepository = mock(ReportRepository.class);
        moderationActionRepository = mock(ModerationActionRepository.class);
        notificationService = mock(NotificationService.class);
        service = new AdminUserServiceImpl(userRepository, profileRepository, postRepository,
                reportRepository, moderationActionRepository, new SystemAdminGuard(userRepository),
                notificationService);
        when(moderationActionRepository.save(any(ModerationAction.class))).thenAnswer(inv -> {
            ModerationAction action = inv.getArgument(0);
            action.setId(500L);
            return action;
        });
        when(notificationService.createAdministrativeNotification(anyLong(), anyLong(), any(), any(), anyLong(), any()))
                .thenReturn(600L);
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(long id, Role role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername("u" + id);
        user.setEmail("u" + id + "@aloute.test");
        user.setPasswordHash("password-hash-" + id);
        user.setRole(role);
        user.setActive(active);
        user.setAuthVersion(0L);
        return user;
    }

    private AdminUserWarnRequest warn() {
        return new AdminUserWarnRequest(ReportReason.HARASSMENT, "Cảnh báo hành vi quấy rối trong bình luận.");
    }

    private AdminUserProfileResetRequest reset(String confirmUsername) {
        return new AdminUserProfileResetRequest(ReportReason.OTHER,
                "Đặt lại hồ sơ theo yêu cầu kiểm duyệt nội dung.", confirmUsername);
    }

    private AdminUserRoleChangeRequest role(Role newRole) {
        return new AdminUserRoleChangeRequest(newRole, ReportReason.OTHER,
                "Thay đổi vai trò theo quyết định của ban quản trị.");
    }

    @Test
    @DisplayName("warning records USER_WARN and persists an administrative notification without changing account state")
    void warningRecordsAuditAndNotification() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        AdminUserWarnResponse response = service.warnUser(1L, 2L, warn());

        assertThat(response.actionType()).isEqualTo("USER_WARN");
        assertThat(response.notificationId()).isEqualTo(600L);
        assertThat(target.isActive()).isTrue();
        assertThat(target.getRole()).isEqualTo(Role.USER);
        assertThat(target.getAuthVersion()).isZero();

        ArgumentCaptor<ModerationAction> action = ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(action.capture());
        assertThat(action.getValue().getActionType()).isEqualTo("USER_WARN");
        assertThat(action.getValue().getActor()).isEqualTo(admin);
        assertThat(action.getValue().getTargetId()).isEqualTo(2L);
        assertThat(action.getValue().getReasonCode()).isEqualTo(ReportReason.HARASSMENT);
        verify(notificationService).createAdministrativeNotification(
                eq(2L), eq(1L), eq("USER_WARN"), eq("USER"), eq(2L), any());
    }

    @Test
    @DisplayName("self-warning is refused before any audit or notification is written")
    void selfWarningRefused() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.warnUser(1L, 1L, warn()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chính mình");
        verify(moderationActionRepository, never()).save(any(ModerationAction.class));
        verify(notificationService, never()).createAdministrativeNotification(anyLong(), anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("profile reset clears exactly the visible fields, preserves data and does not bump authVersion")
    void profileResetClearsOnlyVisibleFields() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, true);
        target.setPasswordHash("keep-password");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        Profile profile = new Profile();
        profile.setUserId(2L);
        profile.setDisplayName("Tên hiển thị");
        profile.setBio("bio");
        profile.setWebsite("https://example.test");
        profile.setAvatarUrl("/api/media/file/avatar");
        profile.setCoverUrl("/api/media/file/cover");
        profile.setFollowersCount(7);
        profile.setFollowingCount(8);
        profile.setPostsCount(9);
        when(profileRepository.findById(2L)).thenReturn(Optional.of(profile));

        AdminUserProfileResetResponse response = service.resetUserProfile(1L, 2L, reset("u2"));

        assertThat(response.displayName()).isEqualTo("u2");
        assertThat(response.resetFields()).containsExactly("displayName", "bio", "website", "avatarUrl", "coverUrl");
        assertThat(profile.getDisplayName()).isEqualTo("u2");
        assertThat(profile.getBio()).isNull();
        assertThat(profile.getWebsite()).isNull();
        assertThat(profile.getAvatarUrl()).isNull();
        assertThat(profile.getCoverUrl()).isNull();
        assertThat(profile.getFollowersCount()).isEqualTo(7);
        assertThat(profile.getFollowingCount()).isEqualTo(8);
        assertThat(profile.getPostsCount()).isEqualTo(9);
        assertThat(target.getEmail()).isEqualTo("u2@aloute.test");
        assertThat(target.getPasswordHash()).isEqualTo("keep-password");
        assertThat(target.getRole()).isEqualTo(Role.USER);
        assertThat(target.isActive()).isTrue();
        assertThat(target.getAuthVersion()).isZero();
        verify(profileRepository).save(profile);
        ArgumentCaptor<ModerationAction> action = ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(action.capture());
        assertThat(action.getValue().getActionType()).isEqualTo("USER_PROFILE_RESET");
        assertThat(action.getValue().getMetadata()).contains("displayName", "coverUrl");
    }

    @Test
    @DisplayName("profile reset confirmation mismatch leaves profile and audit untouched")
    void profileResetConfirmationMismatch() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.resetUserProfile(1L, 2L, reset("someone-else")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp");
        verify(profileRepository, never()).save(any(Profile.class));
        verify(moderationActionRepository, never()).save(any(ModerationAction.class));
    }

    @Test
    @DisplayName("promoting an active USER bumps authVersion and audits previous/new roles")
    void promoteUserChangesRoleAndRevokesSessions() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, true);
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminUserRoleChangeResponse response = service.changeUserRole(1L, 2L, role(Role.SYSTEM_ADMIN));

        assertThat(response.previousRole()).isEqualTo(Role.USER);
        assertThat(response.newRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(target.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(target.getAuthVersion()).isEqualTo(1L);
        ArgumentCaptor<ModerationAction> action = ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(action.capture());
        assertThat(action.getValue().getActionType()).isEqualTo("USER_ROLE_CHANGE");
        assertThat(action.getValue().getMetadata())
                .contains("\"previousRole\":\"USER\"", "\"newRole\":\"SYSTEM_ADMIN\"");
    }

    @Test
    @DisplayName("demoting an admin with an active peer succeeds; the last active admin is protected")
    void demoteAdminHonoursLastAdminInvariant() {
        User actor = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.SYSTEM_ADMIN, true);
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(actor, target));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        service.changeUserRole(1L, 2L, role(Role.USER));
        assertThat(target.getRole()).isEqualTo(Role.USER);

        User lastAdmin = user(3L, Role.SYSTEM_ADMIN, true);
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(lastAdmin));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(lastAdmin));
        assertThatThrownBy(() -> service.changeUserRole(1L, 3L, role(Role.USER)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cuối cùng");
        assertThat(lastAdmin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
    }

    @Test
    @DisplayName("self, inactive and no-op role changes are refused without audit mutations")
    void roleChangeRejectsSelfInactiveAndNoop() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        assertThatThrownBy(() -> service.changeUserRole(1L, 1L, role(Role.USER)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chính mình");

        User inactive = user(2L, Role.USER, false);
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.changeUserRole(1L, 2L, role(Role.SYSTEM_ADMIN)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đang hoạt động");

        User noop = user(3L, Role.USER, true);
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(noop));
        assertThatThrownBy(() -> service.changeUserRole(1L, 3L, role(Role.USER)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã có vai trò");
        verify(moderationActionRepository, never()).save(any(ModerationAction.class));
    }

    @Test
    @DisplayName("viewer-aware capabilities hide self actions and last-admin demotion")
    void capabilitiesAreViewerAware() {
        User target = user(2L, Role.USER, true);
        Profile profile = new Profile();
        profile.setUserId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(profileRepository.findById(2L)).thenReturn(Optional.of(profile));
        when(postRepository.countByUserIdAndParentPostIsNullAndDeletedAtIsNull(2L)).thenReturn(0L);
        when(reportRepository.countByReportedUserId(2L)).thenReturn(0L);
        when(userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true)).thenReturn(2L);

        AdminUserDetailResponse self = service.getUserDetail(2L, 2L);
        assertThat(self.role()).isEqualTo(Role.USER);
        assertThat(self.capabilities().canWarn()).isFalse();
        assertThat(self.capabilities().canSuspend()).isFalse();
        assertThat(self.capabilities().canChangeGlobalRole()).isFalse();
        assertThat(self.capabilities().canResetProfile()).isTrue();

        AdminUserDetailResponse other = service.getUserDetail(2L, 1L);
        assertThat(other.capabilities().canWarn()).isTrue();
        assertThat(other.capabilities().canChangeGlobalRole()).isTrue();
    }
}
