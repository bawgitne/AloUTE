package com.aloute.dto.response.admin;

import com.aloute.entity.ReportReason;

import java.time.Instant;

/**
 * Result of a System-admin account moderation action (suspend / reactivate /
 * warn / profile reset / global role change).
 * Returns the new active state so the UI can reconcile without a reload race.
 * {@code oldRole} / {@code newRole} are only populated for USER_ROLE_CHANGE.
 */
public record AdminUserModerationActionResponse(
        Long actionId,
        Long userId,
        boolean active,
        String actionType,
        ReportReason reasonCode,
        Instant occurredAt,
        String oldRole,
        String newRole
) {
    /** Backwards-compatible factory for actions that do not touch the global role. */
    public static AdminUserModerationActionResponse ofRoleUnchanged(
            Long actionId, Long userId, boolean active, String actionType,
            ReportReason reasonCode, Instant occurredAt) {
        return new AdminUserModerationActionResponse(
                actionId, userId, active, actionType, reasonCode, occurredAt, null, null);
    }
}
