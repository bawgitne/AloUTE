package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminRemovePostRequest(
        @NotNull(message = "Reason code is required")
        ReportReason reasonCode,

        @NotBlank(message = "Reason detail is required")
        @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
        String reasonDetail,

        Long relatedReportId,

        @Size(max = 500, message = "Public notice must be at most 500 characters")
        String publicNotice
) {}
