package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;

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

        boolean canSuspend = !isSoleAdmin;
        boolean canChangeGlobalRole = !isSoleAdmin;

        AdminUserDetailResponse.UserCounts counts = new AdminUserDetailResponse.UserCounts(postCount, reportsCount);
        AdminUserDetailResponse.UserCapabilities capabilities = new AdminUserDetailResponse.UserCapabilities(
                false,
                false,
                false,
                false
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
}
