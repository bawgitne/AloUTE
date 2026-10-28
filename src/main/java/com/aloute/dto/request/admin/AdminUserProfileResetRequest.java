package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Confirmed reset of the visible profile fields. {@code confirmUsername} must match
 * the target account exactly, so a client-side id mix-up cannot silently reset a
 * different account.
 */
public record AdminUserProfileResetRequest(
        @NotNull(message = "Reason code is required")
        ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail,

        @NotBlank(message = "Username confirmation is required")
        @Size(max = 50, message = "Username confirmation must not exceed 50 characters")
        String confirmUsername
) {
}
