package com.aloute.dto.response;

import java.time.Instant;

/**
 * Audit entry of community-scoped moderation activity (approve/reject/remove
 * of community posts), rendered in the dashboard overview "recent activity" list.
 */
public record CommunityModerationActionResponse(
        Long id,
        UserSummary moderator,
        Long postId,
        String actionType,
        String reasonDetail,
        Instant createdAt
) {}
