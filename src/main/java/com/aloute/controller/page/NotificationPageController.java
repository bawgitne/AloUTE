package com.aloute.controller.page;

import com.aloute.dto.response.NotificationResponse;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.AuthService;
import com.aloute.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationPageController {

    private final NotificationService notificationService;
    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/notifications")
    public String showNotifications(
            @RequestParam(required = false, defaultValue = "ALL") String filter,
            @PageableDefault(size = 20) Pageable pageable,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        UserResponse currentUser = null;
        try {
            currentUser = authService.getCurrentUser(currentUserId);
        } catch (Exception e) {
            log.warn("Could not retrieve current user profile for id {}", currentUserId);
        }

        Page<NotificationResponse> notifications = notificationService.getNotifications(currentUserId, filter, pageable);

        model.addAttribute("notifications", notifications.getContent());
        model.addAttribute("page", notifications);
        model.addAttribute("filter", filter);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "notifications");

        return "notification/index";
    }
}
