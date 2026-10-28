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
     * Persists an administrative notification through the normal notification pipeline
     * without applying recipient mute/notification-preference filters. Used for formal
     * System-admin warnings that must always reach the account.
     *
     * @return the persisted notification id
     */
    Long createAdministrativeNotification(Long recipientId, Long actorId, String type, String targetType,
                                          Long targetId, String contentPreview);
    NotificationResponse mapToNotificationResponse(Notification n);
}
