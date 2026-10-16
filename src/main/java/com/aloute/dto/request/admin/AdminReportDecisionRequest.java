package com.aloute.dto.request.admin;

import com.aloute.entity.ReportReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminReportDecisionRequest(
        @NotNull(message = "Expected version is required")
        Long expectedVersion,

        @NotBlank(message = "Decision is required")
        String decision, // "RESOLVE" or "REJECT"

        @NotBlank(message = "Resolution note is required")
        @Size(min = 10, max = 1000, message = "Resolution note must be between 10 and 1000 characters")
        String resolutionNote,

        @Valid
        ResolutionAction resolutionAction
) {
    public record ResolutionAction(
            String type, // "POST_REMOVE"
            ReportReason reasonCode,
            @Pattern(regexp = ".*\\S.*", message = "Reason detail must not be blank")
            @Size(min = 10, max = 1000, message = "Reason detail must be between 10 and 1000 characters")
            String reasonDetail,
            @Size(max = 500, message = "Public notice must be at most 500 characters")
            String publicNotice
    ) {}
}
