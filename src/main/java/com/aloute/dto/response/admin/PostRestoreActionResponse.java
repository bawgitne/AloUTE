package com.aloute.dto.response.admin;

import java.time.Instant;

public record PostRestoreActionResponse(
        Long actionId,
        Long postId,
        String actionType,
        String contentState,
        Long restoredRemovalId,
        Instant occurredAt
) {}
