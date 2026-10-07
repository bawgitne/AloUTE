package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.NotificationResponse;
import com.aloute.dto.response.UnreadCountResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @RequestParam(required = false, defaultValue = "ALL") String filter,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        Page<NotificationResponse> page = notificationService.getNotifications(currentUserId, filter, pageable);
        return ApiResponse.page(page.getContent(), PageMeta.from(page));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount() {
        Long currentUserId = currentUserProvider.requireUserId();
        UnreadCountResponse response = notificationService.getUnreadCount(currentUserId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable Long id) {
        Long currentUserId = currentUserProvider.requireUserId();
        NotificationResponse response = notificationService.markAsRead(id, currentUserId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllAsRead() {
        Long currentUserId = currentUserProvider.requireUserId();
        notificationService.markAllAsRead(currentUserId);
        return ApiResponse.success(Map.of("markedAllAsRead", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteNotification(@PathVariable Long id) {
        Long currentUserId = currentUserProvider.requireUserId();
        notificationService.deleteNotification(id, currentUserId);
        return ApiResponse.success(Map.of("deleted", true, "id", id));
    }
}
