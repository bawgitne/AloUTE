package com.aloute.dto.response.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.ReportReason;

import java.time.Instant;
import java.util.Map;

public record ModerationActionDetailResponse(
        Long id,
        UserSummary actor,
        ModerationTargetType targetType,
        Long targetId,
        String actionType,
        ReportReason reasonCode,
        String reasonDetail,
        Long relatedReportId,
        Instant createdAt,
        Map<String, Object> metadata
) {}
