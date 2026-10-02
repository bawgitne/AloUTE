package com.aloute.feature.user.dto.response;

public record UserSummary(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        boolean isVerified
) {
}
