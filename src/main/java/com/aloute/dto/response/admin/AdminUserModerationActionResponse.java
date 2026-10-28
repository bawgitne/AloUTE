package com.aloute.dto.response.admin;

import com.aloute.entity.ReportReason;

import java.time.Instant;

/**
 * Result of a System-admin account suspension / reactivation action.
 * Returns the new active state so the UI can reconcile without a reload race.
 */
public record AdminUserModerationActionResponse(
        Long actionId,
        Long userId,
        boolean active,
        String actionType,
        ReportReason reasonCode,
        Instant occurredAt
) {}
