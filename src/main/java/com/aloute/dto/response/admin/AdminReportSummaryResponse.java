package com.aloute.dto.response.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;

import java.time.Instant;

public record AdminReportSummaryResponse(
        Long id,
        String targetType,
        Long targetId,
        UserSummary reporter,
        ReportReason reason,
        String descriptionExcerpt,
        ReportStatus status,
        Long reviewedBy,
        Instant createdAt,
        Long version
) {}
