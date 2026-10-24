package com.aloute.service.impl;

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
import com.aloute.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Autowired(required = false)
    private com.aloute.repository.UserNotificationSettingRepository notificationSettingRepository;

    @Autowired(required = false)
    private com.aloute.repository.MuteRepository muteRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private com.aloute.service.FcmNotificationService fcmNotificationService;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long recipientId, String filter, Pageable pageable) {
        String f = (filter != null ? filter.trim().toUpperCase() : "ALL");
        Page<Notification> page;

        switch (f) {
            case "LIKE" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(
                    recipientId, List.of("POST_LIKE", "REPLY_LIKE"), pageable);
            case "REPLY" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(
                    recipientId, List.of("POST_REPLY", "REPLY_REPLY", "POST_QUOTE", "POST_REPOST"), pageable);
            case "MENTION" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(
                    recipientId, List.of("POST_MENTION", "REPLY_MENTION"), pageable);
            case "FOLLOW" -> page = notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(
                    recipientId, List.of("FOLLOW", "FRIEND_REQUEST", "FRIEND_ACCEPT"), pageable);
            default -> page = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, pageable);
        }

        // Batch-load actor profiles for the whole page: the previous per-row
        // profile lookup made a 20-row page cost 2 statements per distinct actor.
        Map<Long, Profile> actorProfiles = batchActorProfiles(page.getContent());
        return page.map(n -> mapToNotificationResponse(n, actorProfiles));
    }

    private Map<Long, Profile> batchActorProfiles(List<Notification> notifications) {
        Set<Long> actorIds = notifications.stream()
                .map(Notification::getActor)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findWithUserByUserIdIn(actorIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long recipientId) {
        long count = notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
        return UnreadCountResponse.builder().unreadCount(count).build();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToNotificationResponse(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long recipientId) {
        notificationRepository.markAllAsRead(recipientId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long recipientId) {
        Notification n = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found or access denied"));
        notificationRepository.delete(n);
    }

    @Override
    @Transactional
    public void createNotification(Long recipientId, Long actorId, String type, String targetType, Long targetId, String contentPreview) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }

        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }

        if (muteRepository != null && actorId != null && muteRepository.existsByMuterIdAndMutedId(recipientId, actorId)) {
            log.debug("Notification skipped because actor {} is muted by recipient {}", actorId, recipientId);
            return;
        }

        if (notificationSettingRepository != null) {
            com.aloute.entity.UserNotificationSetting setting = notificationSettingRepository.findById(recipientId).orElse(null);
            if (setting != null) {
                String t = type != null ? type.toUpperCase() : "";
                if (t.contains("LIKE") && !setting.isLikesEnabled()) return;
                if ((t.contains("REPLY") || t.contains("COMMENT")) && !setting.isCommentsEnabled()) return;
                if (t.contains("FOLLOW") && !setting.isFollowsEnabled()) return;
                if (t.contains("MENTION") && !setting.isMentionsEnabled()) return;
            }
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

        // FCM Push Notification transport (Web Push / Background)
        if (fcmNotificationService != null) {
            try {
                String title = "ALOUTE";
                String body = contentPreview != null ? contentPreview : "Bạn có một thông báo mới";
                fcmNotificationService.sendNotificationToUser(recipientId, title, body, "/feed");
            } catch (Exception ex) {
                log.warn("Failed to dispatch FCM push notification: {}", ex.getMessage());
            }
        }
    }

    @Override
    public NotificationResponse mapToNotificationResponse(Notification n) {
        UserSummary actorSummary = null;
        if (n.getActor() != null) {
            actorSummary = toActorSummary(n.getActor(), profileRepository.findById(n.getActor().getId()).orElse(null));
        }
        return buildResponse(n, actorSummary);
    }

    private NotificationResponse mapToNotificationResponse(Notification n, Map<Long, Profile> actorProfiles) {
        UserSummary actorSummary = null;
        if (n.getActor() != null) {
            Profile profile = actorProfiles.get(n.getActor().getId());
            // profile.getUser() is fetch-joined by the batch query: reading the
            // summary from it avoids initializing the notification's lazy actor proxy.
            actorSummary = toActorSummary(profile != null ? profile.getUser() : n.getActor(), profile);
        }
        return buildResponse(n, actorSummary);
    }

    private UserSummary toActorSummary(User actor, Profile profile) {
        String displayName = (profile != null && profile.getDisplayName() != null)
                ? profile.getDisplayName() : actor.getUsername();
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;
        return new UserSummary(actor.getId(), actor.getUsername(), displayName, avatarUrl, actor.isVerified());
    }

    private NotificationResponse buildResponse(Notification n, UserSummary actorSummary) {
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
