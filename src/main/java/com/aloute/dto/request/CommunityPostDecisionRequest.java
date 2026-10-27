package com.aloute.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Moderator decision body for community post moderation endpoints
 * (approve / reject / remove). The reason is optional for approve/remove
 * and required-by-policy (frontend) for reject.
 */
public record CommunityPostDecisionRequest(
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự") String reason
) {}
