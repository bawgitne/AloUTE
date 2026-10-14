package com.aloute.dto.response.admin;

import java.time.Instant;

public record PostRemovalActionResponse(
        Long actionId,
        Long postId,
        String actionType,
        String contentState,
        Long removalId,
        Instant occurredAt
) {}
