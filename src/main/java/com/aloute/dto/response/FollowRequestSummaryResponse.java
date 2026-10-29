package com.aloute.dto.response;

import java.time.Instant;

/**
 * One entry of the target's pending private-account follow-request inbox. BL-05, issue #92.
 */
public record FollowRequestSummaryResponse(
        Long requestId,
        UserSummary requester,
        Instant createdAt
) {}
