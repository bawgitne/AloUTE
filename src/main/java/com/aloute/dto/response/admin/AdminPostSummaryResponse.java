package com.aloute.dto.response.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.PostVisibility;

import java.time.Instant;

public record AdminPostSummaryResponse(
        Long id,
        UserSummary author,
        String contentExcerpt,
        Long parentPostId,
        Long quotedPostId,
        Long communityId,
        PostVisibility visibility,
        String contentState,
        long reportedCount,
        Instant createdAt,
        PostCapabilities capabilities
) {
    public record PostCapabilities(
            boolean canRemove,
            boolean canRestore
    ) {}
}
