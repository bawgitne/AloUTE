package com.aloute.dto.response.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;

import java.time.Instant;

public record AdminReportDetailResponse(
        Long id,
        ReportTarget target,
        UserSummary reporter,
        ReportReason reason,
        String description,
        ReportStatus status,
        Long reviewedBy,
        Instant reviewedAt,
        Instant closedAt,
        String resolutionNote,
        Instant createdAt,
        Long version,
        ReportCapabilities capabilities
) {
    public record ReportTarget(
            String type,
            Long id
    ) {}

    public record ReportCapabilities(
            boolean canStartReview,
            boolean canDecide
    ) {}
}
