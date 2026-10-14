package com.aloute.dto.response.admin;

import java.time.Instant;

public record ReportReviewResponse(
        Long id,
        String status,
        Long reviewedBy,
        Instant reviewedAt,
        Long version
) {}
