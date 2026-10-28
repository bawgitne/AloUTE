package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import com.aloute.security.authorization.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Global role change. Community roles are deliberately excluded from this contract.
 */
public record AdminUserRoleChangeRequest(
        @NotNull(message = "New global role is required")
        Role newRole,

        @NotNull(message = "Reason code is required")
        ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail
) {
}
