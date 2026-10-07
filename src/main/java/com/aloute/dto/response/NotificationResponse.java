package com.aloute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long recipientId;
    private Long actorId;
    private UserSummary actor;
    private String type;
    private String targetType;
    private Long targetId;
    private String contentPreview;
    private boolean isRead;
    private Instant createdAt;
}
