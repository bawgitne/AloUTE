package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.admin.AdminUserService;
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

        long adminCount = userRepository.countByRole(Role.SYSTEM_ADMIN);
        boolean isSoleAdmin = user.getRole() == Role.SYSTEM_ADMIN && adminCount <= 1;

        // Suspension is a real, backed capability now: allowed for any account that is
        // not the last remaining SYSTEM_ADMIN. The self-suspension guard is enforced at
        // action time (the actor id is not part of this read-only detail lookup).
        boolean canSuspend = !isSoleAdmin;
        boolean canChangeGlobalRole = !isSoleAdmin;

        AdminUserDetailResponse.UserCounts counts = new AdminUserDetailResponse.UserCounts(postCount, reportsCount);
        AdminUserDetailResponse.UserCapabilities capabilities = new AdminUserDetailResponse.UserCapabilities(
                false,
                canSuspend,
                false,
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
        User target = requireTarget(userId);
        requireNotSelf(adminId, target);
        if (!target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_SUSPENDED", "Tài khoản đã bị đình chỉ trước đó.");
        }
        // A system must always keep at least one administrator able to sign in.
        if (target.getRole() == Role.SYSTEM_ADMIN && userRepository.countByRole(Role.SYSTEM_ADMIN) <= 1) {
            throw new ApiException(ErrorCode.USER_LAST_ADMIN_PROTECTED,
                    "Không thể đình chỉ quản trị viên hệ thống cuối cùng.");
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

    /** An administrator must never lock themselves out through the admin panel. */
    private void requireNotSelf(Long adminId, User target) {
        if (Objects.equals(adminId, target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SELF_MODERATION_NOT_ALLOWED",
                    "Bạn không thể tự đình chỉ tài khoản của chính mình.");
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

        ModerationAction action = new ModerationAction();
        action.setActor(actor);
        action.setTargetType(ModerationTargetType.USER);
        action.setTargetId(target.getId());
        action.setActionType(actionType);
        action.setReasonCode(request.reasonCode());
        action.setReasonDetail(request.reasonDetail());
        ModerationAction saved = moderationActionRepository.save(action);

        return new AdminUserModerationActionResponse(
                saved.getId(),
                target.getId(),
                target.isActive(),
                actionType,
                request.reasonCode(),
                saved.getCreatedAt()
        );
    }
}
