package com.aloute.dto.response;

import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import java.time.Instant;

public record ReportResponse(Long id, Long reportedUserId, Long reportedPostId,
                             ReportReason reason, String description,
                             ReportStatus status, Instant createdAt) {}
