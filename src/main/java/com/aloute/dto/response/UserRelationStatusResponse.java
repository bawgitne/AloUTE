package com.aloute.dto.response;

public record UserRelationStatusResponse(
        Long targetUserId,
        boolean following,
        boolean blocked,
        boolean muted,
        boolean restricted
) {}
