package com.aloute.service;

import com.aloute.dto.response.NotificationResponse;
import com.aloute.dto.response.UnreadCountResponse;
import com.aloute.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Page<NotificationResponse> getNotifications(Long recipientId, String filter, Pageable pageable);
    UnreadCountResponse getUnreadCount(Long recipientId);
    NotificationResponse markAsRead(Long notificationId, Long recipientId);
    void markAllAsRead(Long recipientId);
    void deleteNotification(Long notificationId, Long recipientId);
    void createNotification(Long recipientId, Long actorId, String type, String targetType, Long targetId, String contentPreview);

    /**
     * Persists a mandatory account notice (e.g. an official admin warning) and pushes
     * it through the realtime transports. Unlike {@link #createNotification}, this
     * path ignores mute relationships and per-type preference switches so an official
     * notice always reaches the recipient's notification store, and it fails loudly
     * (404) instead of silently dropping when the recipient does not exist.
     */
    NotificationResponse createModerationNotice(Long recipientId, Long actorId, String type, String targetType, Long targetId, String contentPreview);

    NotificationResponse mapToNotificationResponse(Notification n);
}
