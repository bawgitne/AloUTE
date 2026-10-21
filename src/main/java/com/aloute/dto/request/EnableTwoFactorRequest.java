package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EnableTwoFactorRequest(
        @NotBlank(message = "Mã xác thực 6 số không được để trống")
        String code
) {}
