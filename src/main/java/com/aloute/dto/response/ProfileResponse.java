package com.aloute.dto.response;

public record ProfileResponse(Long id, String username, String displayName, String bio,
                              String avatarUrl, String coverUrl, String website, boolean isVerified,
                              boolean isPrivate, long followersCount, long followingCount,
                              long postsCount, Object capabilities) {

    public ProfileResponse(Long id, String username, String displayName, String bio,
                           String avatarUrl, String website, boolean isVerified,
                           boolean isPrivate, long followersCount, long followingCount,
                           long postsCount, Object capabilities) {
        this(id, username, displayName, bio, avatarUrl, null, website, isVerified, isPrivate, followersCount, followingCount, postsCount, capabilities);
    }
}
