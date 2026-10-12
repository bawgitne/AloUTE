package com.aloute.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.NotificationResponse;
import com.aloute.dto.response.UnreadCountResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.Notification;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long recipientId, String filter, Pageable pageable) {
        String f = (filter != null ? filter.trim().toUpperCase() : "ALL");
        Page<Notification> page;

        switch (f) {
            case "LIKE" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(
                    recipientId, List.of("POST_LIKE", "REPLY_LIKE"), pageable);
            case "REPLY" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(
                    recipientId, List.of("POST_REPLY", "REPLY_REPLY", "POST_QUOTE", "POST_REPOST"), pageable);
            case "MENTION" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(
                    recipientId, List.of("POST_MENTION", "REPLY_MENTION"), pageable);
            case "FOLLOW" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(
                    recipientId, List.of("FOLLOW", "FRIEND_REQUEST", "FRIEND_ACCEPT"), pageable);
            default -> page = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        }

        return page.map(this::mapToNotificationResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long recipientId) {
        long count = notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
        return UnreadCountResponse.builder().unreadCount(count).build();
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToNotificationResponse(saved);
    }

    @Transactional
    public void markAllAsRead(Long recipientId) {
        notificationRepository.markAllAsRead(recipientId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long recipientId) {
        Notification n = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found or access denied"));
        notificationRepository.delete(n);
    }

    @Transactional
    public void createNotification(Long recipientId, Long actorId, String type, String targetType, Long targetId, String contentPreview) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }

        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }

        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .contentPreview(contentPreview != null && contentPreview.length() > 200 ? contentPreview.substring(0, 197) + "..." : contentPreview)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification: type={}, recipientId={}, actorId={}", type, recipientId, actorId);

        // Realtime STOMP transport (Persist-Before-Publish)
        if (messagingTemplate != null) {
            try {
                NotificationResponse response = mapToNotificationResponse(saved);
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(recipientId),
                        "/queue/notifications",
                        response
                );
            } catch (Exception ex) {
                log.warn("Failed to publish realtime websocket notification: {}", ex.getMessage());
            }
        }
    }

    public NotificationResponse mapToNotificationResponse(Notification n) {
        UserSummary actorSummary = null;
        if (n.getActor() != null) {
            User actor = n.getActor();
            Profile profile = profileRepository.findById(actor.getId()).orElse(null);
            String displayName = (profile != null && profile.getDisplayName() != null) ? profile.getDisplayName() : actor.getUsername();
            String avatarUrl = profile != null ? profile.getAvatarUrl() : null;
            actorSummary = new UserSummary(actor.getId(), actor.getUsername(), displayName, avatarUrl, actor.isVerified());
        }

        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipient().getId())
                .actorId(n.getActor() != null ? n.getActor().getId() : null)
                .actor(actorSummary)
                .type(n.getType())
                .targetType(n.getTargetType())
                .targetId(n.getTargetId())
                .contentPreview(n.getContentPreview())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
