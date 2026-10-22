package com.aloute.dto.response;

public record NotificationSettingsResponse(
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean followsEnabled,
        boolean mentionsEnabled,
        boolean emailNotificationsEnabled,
        boolean soundEnabled
) {
    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(true, true, true, true, true, true);
    }
}
