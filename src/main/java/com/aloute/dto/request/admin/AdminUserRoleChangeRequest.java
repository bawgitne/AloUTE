package com.aloute.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * System-admin global role change request (USER &lt;-&gt; SYSTEM_ADMIN).
 * The role is carried as a raw string so an unknown value is refused with the
 * stable ADMIN_ROLE_INVALID error instead of a generic JSON-mapping failure.
 */
public record AdminUserRoleChangeRequest(
        @NotBlank(message = "Vai trò hệ thống mới là bắt buộc")
        String newRole,

        @NotNull(message = "Reason code is required")
        com.aloute.entity.ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail
) {}
