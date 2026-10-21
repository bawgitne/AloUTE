package com.aloute.dto.response;

import java.util.List;

public record TwoFactorEnableResponse(
        String message,
        List<String> backupCodes
) {}
