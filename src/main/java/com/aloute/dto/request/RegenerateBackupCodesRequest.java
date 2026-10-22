package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegenerateBackupCodesRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword
) {}
