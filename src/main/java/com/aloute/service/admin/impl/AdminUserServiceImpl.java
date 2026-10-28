package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final SystemAdminGuard systemAdminGuard;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Profile profile = profileRepository.findById(userId).orElse(null);
        String displayName = profile != null && profile.getDisplayName() != null ? profile.getDisplayName() : user.getUsername();
        String bio = profile != null ? profile.getBio() : null;
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;

        long postCount = postRepository.countByUserIdAndParentPostIsNullAndDeletedAtIsNull(userId);
        long reportsCount = reportRepository.countByReportedUserId(userId);

        long activeAdminCount = userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true);

        // canSuspend mirrors the backend rule exactly: an account can be suspended only
        // while it is active and it is not the last active SYSTEM_ADMIN. Self-suspension
        // is refused at action time (the actor id is not part of this read-only getter).
        boolean isLastActiveAdmin = user.getRole() == Role.SYSTEM_ADMIN && activeAdminCount <= 1L;
        boolean canSuspend = user.isActive() && !isLastActiveAdmin;
        // All three actions below now have working endpoints; the finer refusals
        // (self-action, no-op, last-admin demotion) are enforced and tested at
        // action time, where the actor id is known.
        boolean canChangeGlobalRole = user.isActive();

        AdminUserDetailResponse.UserCounts counts = new AdminUserDetailResponse.UserCounts(postCount, reportsCount);
        AdminUserDetailResponse.UserCapabilities capabilities = new AdminUserDetailResponse.UserCapabilities(
                true,
                canSuspend,
                true,
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
                user.getCreatedAt(),
                counts,
                capabilities,
                user.getRole() != null ? user.getRole().name() : null
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
        User target = requireTarget(userId);
        requireNotSelf(adminId, target);

        // For a SYSTEM_ADMIN target the last-active-admin invariant is enforced under a
        // database lock (see SystemAdminGuard): the lock is taken BEFORE the state is
        // re-read, so two admins suspending each other concurrently cannot both proceed.
        // The guard returns the lock-managed instance, whose active flag is authoritative.
        target = systemAdminGuard.lockAndRequireAdminRemains(target);

        if (!target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_SUSPENDED", "Tài khoản đã bị đình chỉ trước đó.");
        }
        return applyStateChange(adminId, target, false, "USER_SUSPEND", request);
    }

    @Override
    @Transactional
    public AdminUserModerationActionResponse reactivateUser(Long adminId, Long userId, AdminUserModerationRequest request) {
        User target = requireTarget(userId);
        if (target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_ACTIVE", "Tài khoản đang hoạt động bình thường.");
        }
        return applyStateChange(adminId, target, true, "USER_REACTIVATE", request);
    }

    private User requireTarget(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    /** An administrator must never take an admin-panel action on their own account. */
    private void requireNotSelf(Long adminId, User target) {
        if (Objects.equals(adminId, target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SELF_MODERATION_NOT_ALLOWED",
                    "Bạn không thể thực hiện thao tác quản trị này trên tài khoản của chính mình.");
        }
    }

    private AdminUserModerationActionResponse applyStateChange(Long adminId, User target, boolean active,
                                                               String actionType, AdminUserModerationRequest request) {
        User actor = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin not found"));

        target.setActive(active);
        // Bump the signed session version so every outstanding JWT for this account is
        // rejected on its next request (suspension) or forced to re-authenticate (reactivation).
        target.setAuthVersion((target.getAuthVersion() == null ? 0L : target.getAuthVersion()) + 1L);
        userRepository.save(target);

        ModerationAction saved = recordAction(actor, target.getId(), actionType, request.reasonCode(),
                request.reasonDetail(), null);

        return AdminUserModerationActionResponse.ofRoleUnchanged(
                saved.getId(),
                target.getId(),
                target.isActive(),
                actionType,
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    // ------------------------------------------------------------------ warn

    @Override
    @Transactional
    public AdminUserModerationActionResponse warnUser(Long adminId, Long userId, AdminUserModerationRequest request) {
        User target = requireTarget(userId);
        requireNotSelf(adminId, target);
        User actor = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin not found"));

        // Advisory only: no change to active state, role, sessions or permissions.
        ModerationAction saved = recordAction(actor, target.getId(), "USER_WARN",
                request.reasonCode(), request.reasonDetail(), null);

        // Mandatory account notice — not mute/preference filtered (see
        // NotificationService#createModerationNotice). Persisted in the same
        // transaction as the audit row; STOMP/FCM are best-effort on top.
        notificationService.createModerationNotice(
                target.getId(), adminId, "ADMIN_WARN", "USER", target.getId(), request.reasonDetail());

        return AdminUserModerationActionResponse.ofRoleUnchanged(
                saved.getId(),
                target.getId(),
                target.isActive(),
                "USER_WARN",
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    // -------------------------------------------------------- profile reset

    @Override
    @Transactional
    public AdminUserModerationActionResponse resetUserProfile(Long adminId, Long userId, AdminUserModerationRequest request) {
        User target = requireTarget(userId);
        requireNotSelf(adminId, target);
        User actor = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin not found"));

        List<String> resetFields = new ArrayList<>(List.of("displayName", "bio", "website", "avatarUrl", "coverUrl"));
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile != null) {
            profile.setDisplayName(target.getUsername());
            profile.setBio(null);
            profile.setWebsite(null);
            profile.setAvatarUrl(null);
            profile.setCoverUrl(null);
            profileRepository.save(profile);
        } else {
            // No profile row to reset (legacy/anomaly): nothing is mutated, the
            // audit row still records that the action was taken.
            resetFields = List.of();
        }
        // Deliberately untouched: email, password hash, role, active flag,
        // authVersion (sessions survive), follower/following/post counters,
        // posts, comments and prior moderation history. No storage file is deleted.

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resetFields", resetFields);
        if (profile == null) {
            meta.put("profileRowMissing", true);
        }
        ModerationAction saved = recordAction(actor, target.getId(), "USER_PROFILE_RESET",
                request.reasonCode(), request.reasonDetail(), meta);

        return AdminUserModerationActionResponse.ofRoleUnchanged(
                saved.getId(),
                target.getId(),
                target.isActive(),
                "USER_PROFILE_RESET",
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }

    // ------------------------------------------------------------ role change

    @Override
    @Transactional
    public AdminUserModerationActionResponse changeGlobalRole(Long adminId, Long userId,
                                                              AdminUserRoleChangeRequest request) {
        User target = requireTarget(userId);
        requireNotSelf(adminId, target);

        Role newRole = parseRole(request.newRole());

        Role oldRole = target.getRole();
        if (oldRole == newRole) {
            // No-op: refuse instead of writing a fake audit trail or revoking sessions.
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_ROLE_UNCHANGED",
                    "Tài khoản đã có vai trò này, không có gì thay đổi.");
        }
        if (!target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_TARGET_INACTIVE",
                    "Tài khoản đang bị vô hiệu hóa. Hãy kích hoạt lại tài khoản trước khi đổi vai trò.");
        }

        User actor = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin not found"));

        if (oldRole == Role.SYSTEM_ADMIN && newRole == Role.USER) {
            // Demotion can reduce the active-admin count to zero, so it is evaluated
            // under the same database lock as suspension and self-deactivation.
            target = systemAdminGuard.lockAndRequireAdminRemains(target);
        } else {
            // Promotion never lowers the active-admin count, but taking the same lock
            // serializes this mutation against concurrent suspensions and
            // self-deactivations so the invariant is always evaluated on fresh state.
            systemAdminGuard.lockAdmins();
        }

        target.setRole(newRole);
        // Bump the signed session version so outstanding tokens keep neither the
        // old admin privileges nor skip a re-authentication after promotion.
        target.setAuthVersion((target.getAuthVersion() == null ? 0L : target.getAuthVersion()) + 1L);
        userRepository.save(target);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("oldRole", oldRole.name());
        meta.put("newRole", newRole.name());
        ModerationAction saved = recordAction(actor, target.getId(), "USER_ROLE_CHANGE",
                request.reasonCode(), request.reasonDetail(), meta);

        return new AdminUserModerationActionResponse(
                saved.getId(),
                target.getId(),
                target.isActive(),
                "USER_ROLE_CHANGE",
                request.reasonCode(),
                saved.getCreatedAt(),
                oldRole.name(),
                newRole.name()
        );
    }

    private Role parseRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_INVALID",
                    "Vai trò hệ thống không hợp lệ. Chỉ chấp nhận USER hoặc SYSTEM_ADMIN.");
        }
        try {
            return Role.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_INVALID",
                    "Vai trò hệ thống không hợp lệ. Chỉ chấp nhận USER hoặc SYSTEM_ADMIN.");
        }
    }

    private ModerationAction recordAction(User actor, Long targetId, String actionType,
                                          ReportReason reasonCode, String reasonDetail, Map<String, Object> metadata) {
        ModerationAction action = new ModerationAction();
        action.setActor(actor);
        action.setTargetType(ModerationTargetType.USER);
        action.setTargetId(targetId);
        action.setActionType(actionType);
        action.setReasonCode(reasonCode);
        action.setReasonDetail(reasonDetail);
        if (metadata != null && !metadata.isEmpty()) {
            try {
                action.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception ex) {
                log.warn("Failed to serialize moderation metadata for {}", actionType, ex);
            }
        }
        return moderationActionRepository.save(action);
    }
}
