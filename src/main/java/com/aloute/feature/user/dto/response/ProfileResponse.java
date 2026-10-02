package com.aloute.feature.user.dto.response;

public record ProfileResponse(
        Long id,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        String website,
        boolean isVerified,
        boolean isPrivate,
        long followersCount,
        long followingCount,
        long postsCount,
        Object capabilities
) {
}
