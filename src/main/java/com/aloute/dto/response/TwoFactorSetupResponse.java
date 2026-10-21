package com.aloute.dto.response;

public record TwoFactorSetupResponse(
        String secretKey,
        String otpAuthUri,
        String qrCodeDataUrl
) {}
