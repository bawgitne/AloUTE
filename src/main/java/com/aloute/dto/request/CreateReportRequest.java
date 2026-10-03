package com.aloute.dto.request;

import com.aloute.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @Positive Long reportedUserId,
        @Positive Long reportedPostId,
        @NotNull ReportReason reason,
        @Size(max = 2000) String description
) {}
