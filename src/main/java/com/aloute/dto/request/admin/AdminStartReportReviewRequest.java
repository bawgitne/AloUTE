package com.aloute.dto.request.admin;

import jakarta.validation.constraints.NotNull;

public record AdminStartReportReviewRequest(
        @NotNull(message = "Expected version is required")
        Long expectedVersion
) {}
