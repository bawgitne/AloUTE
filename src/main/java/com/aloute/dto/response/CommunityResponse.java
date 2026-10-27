package com.aloute.dto.response;

import com.aloute.entity.CommunityRole;

public record CommunityResponse(Long id, Long ownerId, String name, String slug,
                                String description, String avatarUrl, String coverUrl,
                                boolean isPrivate, long memberCount, long postCount,
                                CommunityRole currentUserRole, Boolean isOwner,
                                boolean requirePostApproval, boolean canManage, long pendingPostCount) {
    /** Compatibility constructor for pre-moderation call sites and tests. */
    public CommunityResponse(Long id, Long ownerId, String name, String slug,
                             String description, String avatarUrl, String coverUrl,
                             boolean isPrivate, long memberCount, long postCount,
                             CommunityRole currentUserRole, Boolean isOwner) {
        this(id, ownerId, name, slug, description, avatarUrl, coverUrl, isPrivate, memberCount, postCount,
                currentUserRole, isOwner, false, false, 0L);
    }
}
