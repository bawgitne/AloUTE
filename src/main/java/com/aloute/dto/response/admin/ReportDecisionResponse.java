package com.aloute.dto.response.admin;

import java.time.Instant;

public record ReportDecisionResponse(
        Long reportId,
        String status,
        Long resolutionActionId,
        Instant closedAt,
        Long version
) {}
