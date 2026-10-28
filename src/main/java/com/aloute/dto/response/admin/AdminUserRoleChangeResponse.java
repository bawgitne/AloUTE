package com.aloute.dto.response.admin;

import com.aloute.entity.ReportReason;
import com.aloute.security.authorization.Role;

import java.time.Instant;

public record AdminUserRoleChangeResponse(
        Long actionId,
        Long userId,
        Role previousRole,
        Role newRole,
        String actionType,
        ReportReason reasonCode,
        Instant occurredAt
) {
}
