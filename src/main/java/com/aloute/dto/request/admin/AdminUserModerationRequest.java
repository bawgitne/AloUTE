package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * System-admin account suspension / reactivation request.
 * A reason code and a human-readable detail are mandatory so every status
 * change carries an audit trail (domain: admin actions must be justified).
 */
public record AdminUserModerationRequest(
        @NotNull(message = "Reason code is required")
        ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail
) {}
