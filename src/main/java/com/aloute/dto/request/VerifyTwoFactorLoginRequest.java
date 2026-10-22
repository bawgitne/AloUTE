package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyTwoFactorLoginRequest(
        @NotBlank(message = "twoFactorTicket không được để trống")
        String twoFactorTicket,

        @NotBlank(message = "Mã xác thực không được để trống")
        String code,

        boolean isBackupCode
) {}
