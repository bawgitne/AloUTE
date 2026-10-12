package com.aloute.event;

import lombok.Builder;

@Builder
public record NotificationEvent(
        Long recipientId,
        Long actorId,
        String type,
        String targetType,
        Long targetId,
        String contentPreview
) {
    public static NotificationEvent of(Long recipientId, Long actorId, String type, String targetType, Long targetId, String contentPreview) {
        return new NotificationEvent(recipientId, actorId, type, targetType, targetId, contentPreview);
    }
}
