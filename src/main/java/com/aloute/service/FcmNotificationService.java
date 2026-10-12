package com.aloute.service;

import com.aloute.entity.UserFcmToken;
import com.aloute.repository.UserFcmTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmNotificationService {

    private final UserFcmTokenRepository fcmTokenRepository;

    @Async
    public void sendNotificationToUser(Long recipientId, String title, String body, String clickUrl) {
        if (recipientId == null) {
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("FirebaseApp is not initialized. Skipping FCM push notification.");
            return;
        }

        List<UserFcmToken> tokens = fcmTokenRepository.findByUserId(recipientId);
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens registered for recipientId: {}", recipientId);
            return;
        }

        for (UserFcmToken tokenRecord : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(tokenRecord.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title != null ? title : "ALOUTE")
                                .setBody(body != null ? body : "Bạn có thông báo mới")
                                .build())
                        .putData("click_url", clickUrl != null ? clickUrl : "/feed")
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM push sent to recipientId: {} (msgId: {})", recipientId, response);
            } catch (Exception ex) {
                log.warn("Failed to send FCM push to token {}: {}", tokenRecord.getToken(), ex.getMessage());
                if (ex.getMessage() != null && (ex.getMessage().contains("UNREGISTERED") || ex.getMessage().contains("invalid-registration-token"))) {
                    try {
                        fcmTokenRepository.delete(tokenRecord);
                    } catch (Exception deleteEx) {
                        log.warn("Failed to delete stale FCM token: {}", deleteEx.getMessage());
                    }
                }
            }
        }
    }
}
