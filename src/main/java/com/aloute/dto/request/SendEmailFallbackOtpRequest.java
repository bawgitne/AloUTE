package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendEmailFallbackOtpRequest(
        @NotBlank(message = "twoFactorTicket không được để trống")
        String twoFactorTicket
) {}
