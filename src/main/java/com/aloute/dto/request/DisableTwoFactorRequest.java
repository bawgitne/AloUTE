package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DisableTwoFactorRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mã xác thực không được để trống")
        String code
) {}
