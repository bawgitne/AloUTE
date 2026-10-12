package com.aloute.listener;

import com.aloute.event.NotificationEvent;
import com.aloute.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventListenerTest {

    @Test
    void onNotificationEvent_shouldDelegateToNotificationService() {
        NotificationService mockService = mock(NotificationService.class);
        NotificationEventListener listener = new NotificationEventListener(mockService);
        NotificationEvent event = NotificationEvent.of(10L, 20L, "POST_LIKE", "POST", 100L, "Sample preview");

        listener.onNotificationEvent(event);

        verify(mockService).createNotification(10L, 20L, "POST_LIKE", "POST", 100L, "Sample preview");
    }
}
