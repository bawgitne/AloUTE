package com.aloute.dto.response.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.PostVisibility;

import java.time.Instant;
import java.util.List;

public record AdminPostDetailResponse(
        Long id,
        UserSummary author,
        String content,
        PostVisibility visibility,
        String contentState,
        Long parentPostId,
        Long quotedPostId,
        Instant createdAt,
        Instant deletedAt,
        List<Long> relatedReportIds,
        Long activeRemovalId,
        PostCapabilities capabilities
) {
    public record PostCapabilities(
            boolean canRemove,
            boolean canRestore
    ) {}
}
