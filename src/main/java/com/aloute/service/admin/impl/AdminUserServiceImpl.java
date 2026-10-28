package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.request.admin.AdminUserProfileResetRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.dto.request.admin.AdminUserWarnRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserProfileResetResponse;
import com.aloute.dto.response.admin.AdminUserRoleChangeResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import com.aloute.dto.response.admin.AdminUserWarnResponse;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
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
import com.aloute.service.admin.AdminUserService;
import com.aloute.service.admin.SystemAdminGuard;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final SystemAdminGuard systemAdminGuard;
    private final NotificationService notificationService;

    @Override
    public Page<AdminUserSummaryResponse> getUsers(String query, String roleStr, Boolean active, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (roleStr != null && !roleStr.isBlank()) {
                try {
                    Role role = Role.valueOf(roleStr.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("role"), role));
                } catch (IllegalArgumentException ignored) {}
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public AdminUserDetailResponse getUserDetail(Long userId) {
        return getUserDetail(userId, null);
    }

    @Override
    public AdminUserDetailResponse getUserDetail(Long userId, Long viewerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Profile profile = profileRepository.findById(userId).orElse(null);
        String displayName = profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : user.getUsername();
        String bio = profile != null ? profile.getBio() : null;
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;

        long postCount = postRepository.countByUserIdAndParentPostIsNullAndDeletedAtIsNull(userId);
        long reportsCount = reportRepository.countByReportedUserId(userId);

        long activeAdminCount = userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true);

        boolean isSelf = viewerId != null && Objects.equals(viewerId, userId);
        boolean isLastActiveAdmin = user.getRole() == Role.SYSTEM_ADMIN && activeAdminCount <= 1L;
        // Every flag mirrors the action service rules. "Last active admin" blocks both
        // suspension and demotion; self blocks warn/suspend/role change.
        boolean canWarn = !isSelf;
        boolean canSuspend = !isSelf && user.isActive() && !isLastActiveAdmin;
        boolean canResetProfile = true;
        boolean canChangeGlobalRole = !isSelf && user.isActive() && !isLastActiveAdmin;

        AdminUserDetailResponse.UserCounts counts = new AdminUserDetailResponse.UserCounts(postCount, reportsCount);
        AdminUserDetailResponse.UserCapabilities capabilities = new AdminUserDetailResponse.UserCapabilities(
                canWarn,
                canSuspend,
                canResetProfile,
                canChangeGlobalRole
        );

        return new AdminUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                displayName,
                bio,
                avatarUrl,
                user.isActive(),
                user.isVerified(),
                user.isPrivateAccount(),
                user.getRole(),
                user.getCreatedAt(),
                counts,
                capabilities
        );
    }

    private AdminUserSummaryResponse toSummaryResponse(User user) {
        Profile profile = profileRepository.findById(user.getId()).orElse(null);
        String displayName = profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : user.getUsername();
        String moderationStatus = user.isActive() ? "NORMAL" : "SUSPENDED";

        return new AdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                displayName,
                user.isActive(),
                user.isVerified(),
                user.isPrivateAccount(),
                moderationStatus,
                user.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AdminUserModerationActionResponse suspendUser(Long adminId, Long userId, AdminUserModerationRequest request) {
        // Lock order is always: all SYSTEM_ADMIN rows first, then the target row.
        // This serializes suspension with role changes and self-deactivation.
        SystemAdminGuard.LockedAccount locked = systemAdminGuard.lockAdminsAndTarget(userId);
        User target = locked.target();
        requireNotSelf(adminId, target);

        if (!target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_SUSPENDED", "Tài khoản đã bị đình chỉ trước đó.");
        }
        systemAdminGuard.requireActiveAdminRemains(target, locked.admins());
        return applyStateChange(adminId, target, false, "USER_SUSPEND", request);
    }

    @Override
    @Transactional
    public AdminUserModerationActionResponse reactivateUser(Long adminId, Long userId, AdminUserModerationRequest request) {
        User target = systemAdminGuard.lockAdminsAndTarget(userId).target();
        if (target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_ACTIVE", "Tài khoản đang hoạt động bình thường.");
        }
        return applyStateChange(adminId, target, true, "USER_REACTIVATE", request);
    }

    @Override
    @Transactional
    public AdminUserWarnResponse warnUser(Long adminId, Long userId, AdminUserWarnRequest request) {
        User target = requireTarget(userId);
        requireNotSelfWarn(adminId, target);
        User actor = requireActor(adminId);

        ModerationAction saved = saveAction(actor, target, "USER_WARN",
                request.reasonCode(), request.reasonDetail(), null);

        // Administrative warning is persisted through the regular notification pipeline
        // but bypasses mute/preference filters: a formal warning must reach the account.
        Long notificationId = notificationService.createAdministrativeNotification(
                target.getId(),
                adminId,
                "USER_WARN",
                "USER",
                target.getId(),
                "Cảnh báo từ quản trị viên: " + request.reasonDetail()
        );
        if (notificationId == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Không thể xác nhận thông báo cảnh báo đã được lưu.");
        }

        return new AdminUserWarnResponse(
                saved.getId(),
                target.getId(),
                notificationId,
                "USER_WARN",
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AdminUserProfileResetResponse resetUserProfile(Long adminId, Long userId,
                                                          AdminUserProfileResetRequest request) {
        User target = requireTarget(userId);
        if (!request.confirmUsername().trim().equals(target.getUsername())) {
            throw new ApiException(ErrorCode.USER_PROFILE_RESET_CONFIRMATION_MISMATCH,
                    "Tên đăng nhập xác nhận không khớp với tài khoản cần đặt lại hồ sơ.");
        }
        User actor = requireActor(adminId);

        Profile profile = profileRepository.findById(target.getId()).orElseGet(() -> {
            Profile created = new Profile();
            created.setUser(target);
            return created;
        });
        profile.setDisplayName(target.getUsername());
        profile.setBio(null);
        profile.setWebsite(null);
        profile.setAvatarUrl(null);
        profile.setCoverUrl(null);
        Profile savedProfile = profileRepository.save(profile);

        String metadata = "{\"resetFields\":[\"displayName\",\"bio\",\"website\",\"avatarUrl\",\"coverUrl\"]}";
        ModerationAction saved = saveAction(actor, target, "USER_PROFILE_RESET",
                request.reasonCode(), request.reasonDetail(), metadata);

        return new AdminUserProfileResetResponse(
                saved.getId(),
                target.getId(),
                savedProfile.getDisplayName(),
                List.of("displayName", "bio", "website", "avatarUrl", "coverUrl"),
                "USER_PROFILE_RESET",
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AdminUserRoleChangeResponse changeUserRole(Long adminId, Long userId,
                                                      AdminUserRoleChangeRequest request) {
        if (Objects.equals(adminId, userId)) {
            throw new ApiException(ErrorCode.USER_SELF_ROLE_CHANGE_NOT_ALLOWED,
                    "Bạn không thể tự thay đổi vai trò hệ thống của chính mình.");
        }
        if (request.newRole() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Vai trò hệ thống mới là bắt buộc.");
        }

        // Same lock order as suspension and self-deactivation: admins first, then target.
        SystemAdminGuard.LockedAccount locked = systemAdminGuard.lockAdminsAndTarget(userId);
        User target = locked.target();
        if (!target.isActive()) {
            throw new ApiException(ErrorCode.USER_ROLE_CHANGE_REQUIRES_ACTIVE,
                    "Chỉ có thể đổi vai trò hệ thống của tài khoản đang hoạt động. Vui lòng kích hoạt lại trước.");
        }

        Role previousRole = target.getRole();
        if (previousRole == request.newRole()) {
            throw new ApiException(ErrorCode.USER_ROLE_UNCHANGED, "Tài khoản đã có vai trò hệ thống này.");
        }
        if (previousRole == Role.SYSTEM_ADMIN) {
            // Demotion can remove the final active administrator; evaluate under the lock.
            systemAdminGuard.requireActiveAdminRemains(target, locked.admins());
        }

        User actor = requireActor(adminId);
        target.setRole(request.newRole());
        // Old JWTs keep the previous role in their signed principal until expiry; bumping
        // authVersion makes every outstanding token fail closed immediately.
        target.setAuthVersion((target.getAuthVersion() == null ? 0L : target.getAuthVersion()) + 1L);
        userRepository.save(target);

        String metadata = "{\"previousRole\":\"" + previousRole.name()
                + "\",\"newRole\":\"" + request.newRole().name() + "\"}";
        ModerationAction saved = saveAction(actor, target, "USER_ROLE_CHANGE",
                request.reasonCode(), request.reasonDetail(), metadata);

        return new AdminUserRoleChangeResponse(
                saved.getId(),
                target.getId(),
                previousRole,
                request.newRole(),
                "USER_ROLE_CHANGE",
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    private User requireTarget(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    private User requireActor(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin not found"));
    }

    /** An administrator must never lock themselves out through the admin panel. */
    private void requireNotSelf(Long adminId, User target) {
        if (Objects.equals(adminId, target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SELF_MODERATION_NOT_ALLOWED",
                    "Bạn không thể tự đình chỉ tài khoản của chính mình.");
        }
    }

    private void requireNotSelfWarn(Long adminId, User target) {
        if (Objects.equals(adminId, target.getId())) {
            throw new ApiException(ErrorCode.USER_SELF_WARN_NOT_ALLOWED,
                    "Bạn không thể tự gửi cảnh báo cho tài khoản của chính mình.");
        }
    }

    private AdminUserModerationActionResponse applyStateChange(Long adminId, User target, boolean active,
                                                               String actionType, AdminUserModerationRequest request) {
        User actor = requireActor(adminId);

        target.setActive(active);
        // Bump the signed session version so every outstanding JWT for this account is
        // rejected on its next request (suspension) or forced to re-authenticate (reactivation).
        target.setAuthVersion((target.getAuthVersion() == null ? 0L : target.getAuthVersion()) + 1L);
        userRepository.save(target);

        ModerationAction saved = saveAction(actor, target, actionType,
                request.reasonCode(), request.reasonDetail(), null);

        return new AdminUserModerationActionResponse(
                saved.getId(),
                target.getId(),
                target.isActive(),
                actionType,
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    private ModerationAction saveAction(User actor, User target, String actionType,
                                        ReportReason reasonCode, String reasonDetail, String metadata) {
        ModerationAction action = new ModerationAction();
        action.setActor(actor);
        action.setTargetType(ModerationTargetType.USER);
        action.setTargetId(target.getId());
        action.setActionType(actionType);
        action.setReasonCode(reasonCode);
        action.setReasonDetail(reasonDetail);
        action.setMetadata(metadata);
        return moderationActionRepository.save(action);
    }
}
