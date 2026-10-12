package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
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
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/settings")
    public String showSettingsOverview(Model model) {
        return showSettingsTab("overview", model);
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
        try {
            currentUser = userService.getProfile(currentUserId);
        } catch (Exception ignored) {}

        model.addAttribute("currentSection", section);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "settings");
        model.addAttribute("activeTab", "settings");

        return "settings/index";
    }
}
