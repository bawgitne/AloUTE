package com.aloute.dto.response;

/**
 * Aggregated counters for the community management dashboard overview tab.
 * Counts reflect live visibility rules (soft-deleted and non-approved posts excluded).
 */
public record CommunityManageStatsResponse(
        long memberCount,
        long publishedPostCount,
        long pendingPostCount,
        long removedPostCount
) {}
