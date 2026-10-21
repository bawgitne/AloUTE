package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.AuthService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RequiredArgsConstructor
@Slf4j
@Controller
public class SettingsPageController {

    private final UserService userService;
    private final AuthService authService;
    private final com.aloute.service.TwoFactorService twoFactorService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/settings")
    public String showSettingsOverview(Model model) {
        return showSettingsTab("profile", model);
    }

    @GetMapping("/settings/{section}")
    public String showSettingsSection(@PathVariable String section, Model model) {
        return showSettingsTab(section, model);
    }

    private String showSettingsTab(String section, Model model) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        ProfileResponse currentUser = null;
        UserResponse accountUser = null;
        com.aloute.dto.response.TwoFactorStatusResponse twoFactorStatus = null;
        com.aloute.dto.response.NotificationSettingsResponse notificationSettings = null;
        try {
            currentUser = userService.getProfile(currentUserId);
            accountUser = authService.getCurrentUser(currentUserId);
            twoFactorStatus = twoFactorService.getStatus(currentUserId);
            notificationSettings = userService.getNotificationSettings(currentUserId);
        } catch (Exception ignored) {}

        String activeSection = (section == null || section.isBlank() || "overview".equalsIgnoreCase(section))
                ? "profile" : section.toLowerCase();

        model.addAttribute("currentSection", activeSection);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("accountUser", accountUser);
        model.addAttribute("twoFactorStatus", twoFactorStatus);
        model.addAttribute("notificationSettings", notificationSettings);
        model.addAttribute("activeNav", "settings");
        model.addAttribute("activeTab", "settings");

        return "settings/index";
    }
}
