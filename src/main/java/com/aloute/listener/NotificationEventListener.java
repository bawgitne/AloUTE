package com.aloute.listener;

import com.aloute.event.NotificationEvent;
import com.aloute.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationEvent(NotificationEvent event) {
        log.debug("Processing NotificationEvent: type={}, recipientId={}, actorId={}",
                event.type(), event.recipientId(), event.actorId());
        try {
            notificationService.createNotification(
                    event.recipientId(),
                    event.actorId(),
                    event.type(),
                    event.targetType(),
                    event.targetId(),
                    event.contentPreview()
            );
        } catch (Exception ex) {
            log.error("Failed to process notification event: {}", ex.getMessage(), ex);
        }
    }
}
