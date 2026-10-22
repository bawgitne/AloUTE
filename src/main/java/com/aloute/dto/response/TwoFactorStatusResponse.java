package com.aloute.dto.response;

import java.time.Instant;

public record TwoFactorStatusResponse(
        boolean enabled,
        String type,
        Instant confirmedAt,
        long remainingBackupCodes
) {}
