package com.aloute.dto.response.admin;

import com.aloute.entity.ReportReason;

import java.time.Instant;

/**
 * Warning result. The notification id proves the administrative warning was
 * persisted through the normal notification pipeline before this response was
 * returned; the account state is deliberately unchanged.
 */
public record AdminUserWarnResponse(
        Long actionId,
        Long userId,
        Long notificationId,
        String actionType,
        ReportReason reasonCode,
        Instant occurredAt
) {
}
