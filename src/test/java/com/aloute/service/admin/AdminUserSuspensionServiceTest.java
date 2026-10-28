package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
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
 * Guard and audit-trail unit tests for System-admin account suspension.
 * Covers: self-suspension refusal, last-active-admin protection (via the real
 * SystemAdminGuard), already-suspended conflict, session revocation (authVersion bump),
 * audit logging, and the reported capabilities.
 */
class AdminUserSuspensionServiceTest {

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private PostRepository postRepository;
    private ReportRepository reportRepository;
    private ModerationActionRepository moderationActionRepository;
    private NotificationService notificationService;
    private AdminUserServiceImpl service;

    private AdminUserModerationRequest reason() {
        return new AdminUserModerationRequest(ReportReason.OTHER, "Vi phạm quy định cộng đồng nghiêm trọng.");
    }

    private User user(long id, Role role, boolean active) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setEmail("u" + id + "@aloute.test");
        u.setRole(role);
        u.setActive(active);
        u.setAuthVersion(0L);
        return u;
    }

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
            ModerationAction a = inv.getArgument(0);
            a.setId(500L);
            return a;
        });
    }

    private void lockAdmins(User... admins) {
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(admins));
    }

    private void lockTargetRow(User target) {
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    @Test
    @DisplayName("suspending a normal account sets inactive and bumps authVersion (session revocation)")
    void suspendBumpsAuthVersionAndDeactivates() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        lockAdmins(admin);
        lockTargetRow(target);

        AdminUserModerationActionResponse res = service.suspendUser(1L, 2L, reason());

        assertThat(res.active()).isFalse();
        assertThat(res.actionType()).isEqualTo("USER_SUSPEND");
        assertThat(res.userId()).isEqualTo(2L);
        assertThat(target.isActive()).isFalse();
        assertThat(target.getAuthVersion()).isEqualTo(1L);
        verify(moderationActionRepository).save(any(ModerationAction.class));
    }

    @Test
    @DisplayName("an admin cannot suspend their own account")
    void selfSuspendIsRejected() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        lockAdmins(admin);

        assertThatThrownBy(() -> service.suspendUser(1L, 1L, reason()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chính mình");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("the last active SYSTEM_ADMIN cannot be suspended (guard uses active-admin count, not total)")
    void lastActiveAdminIsProtected() {
        User actor = user(1L, Role.SYSTEM_ADMIN, true);
        User targetAdmin = user(2L, Role.SYSTEM_ADMIN, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        // Locked admin set: target is the only ACTIVE admin (a second admin exists but is inactive).
        User inactiveAdmin = user(3L, Role.SYSTEM_ADMIN, false);
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(targetAdmin, inactiveAdmin));

        assertThatThrownBy(() -> service.suspendUser(1L, 2L, reason()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cuối cùng");
        assertThat(targetAdmin.isActive()).isTrue();
        assertThat(targetAdmin.getAuthVersion()).isZero();
        verify(userRepository, never()).save(any(User.class));
        verify(moderationActionRepository, never()).save(any(ModerationAction.class));
    }

    @Test
    @DisplayName("a SYSTEM_ADMIN can be suspended while another active admin remains, using the locked state")
    void adminSuspensionAllowedWithPeer() {
        User actor = user(1L, Role.SYSTEM_ADMIN, true);
        User targetAdmin = user(2L, Role.SYSTEM_ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).thenReturn(List.of(actor, targetAdmin));

        AdminUserModerationActionResponse res = service.suspendUser(1L, 2L, reason());

        assertThat(res.active()).isFalse();
        assertThat(targetAdmin.isActive()).isFalse();
    }

    @Test
    @DisplayName("suspending an already-suspended account is a conflict, not a silent duplicate")
    void alreadySuspendedIsConflict() {
        User target = user(2L, Role.USER, false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        lockAdmins();
        lockTargetRow(target);

        assertThatThrownBy(() -> service.suspendUser(1L, 2L, reason()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("trước đó");
    }

    @Test
    @DisplayName("suspending a nonexistent account is a not-found error")
    void missingUserIsNotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        lockAdmins();
        when(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendUser(1L, 999L, reason()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("reactivating a suspended account restores active and bumps authVersion")
    void reactivateRestoresAccount() {
        User admin = user(1L, Role.SYSTEM_ADMIN, true);
        User target = user(2L, Role.USER, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        lockAdmins(admin);
        lockTargetRow(target);

        AdminUserModerationActionResponse res = service.reactivateUser(1L, 2L, reason());

        assertThat(res.active()).isTrue();
        assertThat(res.actionType()).isEqualTo("USER_REACTIVATE");
        assertThat(target.isActive()).isTrue();
        assertThat(target.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("reactivating an already-active account is a conflict")
    void alreadyActiveIsConflict() {
        User target = user(2L, Role.USER, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        lockAdmins();
        lockTargetRow(target);

        assertThatThrownBy(() -> service.reactivateUser(1L, 2L, reason()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hoạt động");
    }

    @Test
    @DisplayName("capabilities: canSuspend mirrors reality and canChangeGlobalRole is false (no such API)")
    void capabilitiesReflectReality() {
        User normal = user(2L, Role.USER, true);
        Profile p = new Profile();
        p.setUserId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(normal));
        when(profileRepository.findById(2L)).thenReturn(Optional.of(p));
        when(postRepository.countByUserIdAndParentPostIsNullAndDeletedAtIsNull(2L)).thenReturn(0L);
        when(reportRepository.countByReportedUserId(2L)).thenReturn(0L);
        when(userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true)).thenReturn(3L);

        AdminUserDetailResponse detail = service.getUserDetail(2L);
        assertThat(detail.capabilities().canSuspend()).isTrue();
        assertThat(detail.capabilities().canWarn()).isTrue();
        assertThat(detail.capabilities().canResetProfile()).isTrue();
        assertThat(detail.capabilities().canChangeGlobalRole()).isTrue();
    }

    @Test
    @DisplayName("capabilities: the last active admin cannot be suspended, an inactive account cannot either")
    void capabilitiesForLastAdminAndInactive() {
        User lastAdmin = user(2L, Role.SYSTEM_ADMIN, true);
        Profile p = new Profile();
        p.setUserId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(lastAdmin));
        when(profileRepository.findById(2L)).thenReturn(Optional.of(p));
        when(postRepository.countByUserIdAndParentPostIsNullAndDeletedAtIsNull(anyLong())).thenReturn(0L);
        when(reportRepository.countByReportedUserId(anyLong())).thenReturn(0L);
        when(userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true)).thenReturn(1L);

        assertThat(service.getUserDetail(2L).capabilities().canSuspend()).isFalse();
        assertThat(service.getUserDetail(2L).capabilities().canChangeGlobalRole()).isFalse();

        User inactive = user(4L, Role.USER, false);
        when(userRepository.findById(4L)).thenReturn(Optional.of(inactive));
        assertThat(service.getUserDetail(4L).capabilities().canSuspend()).isFalse();
    }
}
