package com.aloute.dto.response.admin;

import com.aloute.entity.ReportReason;

import java.time.Instant;
import java.util.List;

public record AdminUserProfileResetResponse(
        Long actionId,
        Long userId,
        String displayName,
        List<String> resetFields,
        String actionType,
        ReportReason reasonCode,
        Instant occurredAt
) {
}
