package com.aloute.dto.response.admin;

import java.time.Instant;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String displayName,
        boolean isActive,
        boolean isVerified,
        boolean isPrivate,
        String moderationStatus,
        Instant createdAt
) {}
