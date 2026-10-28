package com.aloute.dto.response.admin;

import com.aloute.security.authorization.Role;

import java.time.Instant;

public record AdminUserDetailResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String bio,
        String avatarUrl,
        boolean isActive,
        boolean isVerified,
        boolean isPrivate,
        Role role,
        Instant createdAt,
        UserCounts counts,
        UserCapabilities capabilities
) {
    public record UserCounts(
            long posts,
            long reportsReceived
    ) {}

    public record UserCapabilities(
            boolean canWarn,
            boolean canSuspend,
            boolean canResetProfile,
            boolean canChangeGlobalRole
    ) {}
}
