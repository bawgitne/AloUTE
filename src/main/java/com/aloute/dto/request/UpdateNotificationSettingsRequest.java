package com.aloute.dto.request;

public record UpdateNotificationSettingsRequest(
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean followsEnabled,
        boolean mentionsEnabled,
        boolean emailNotificationsEnabled,
        boolean soundEnabled
) {
}
