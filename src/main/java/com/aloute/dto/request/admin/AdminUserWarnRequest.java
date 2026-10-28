package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * System-admin advisory warning. A reason code and detail are mandatory because
 * every administrative action must be auditable; the action itself only records
 * the warning and sends a notification.
 */
public record AdminUserWarnRequest(
        @NotNull(message = "Reason code is required")
        ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail
) {
}
