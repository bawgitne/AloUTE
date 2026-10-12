package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class ProfilePageController {

    private final UserService userService;
    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/profile")
    public String showMyProfile(
            @RequestParam(required = false, defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        return showUserProfileByIdentifier(String.valueOf(currentUserId), tab, page, size, model);
    }

    @GetMapping({"/profile/{username}", "/@{username}", "/u/{username}", "/user/{username}", "/users/{username}"})
    public String showUserProfile(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        return showUserProfileByIdentifier(username, tab, page, size, model);
    }

    private String showUserProfileByIdentifier(
            String identifier,
            String tab,
            int page,
            int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        ProfileResponse targetProfile = null;
        try {
            if (identifier.matches("\\d+")) {
                targetProfile = userService.getProfile(Long.parseLong(identifier));
            } else {
                targetProfile = userService.getProfileByUsername(identifier);
            }
        } catch (Exception e) {
            log.warn("Profile not found for identifier: {}", identifier);
        }

        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception ignored) {}
        }

        var postsRes = (targetProfile != null)
                ? postService.userPosts(targetProfile.id(), page, size, currentUserId)
                : null;

        boolean isSelf = (currentUserId != null && targetProfile != null && currentUserId.equals(targetProfile.id()));

        model.addAttribute("profile", targetProfile);
        model.addAttribute("targetUser", targetProfile);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("posts", postsRes != null ? postsRes.getData() : List.of());
        model.addAttribute("isSelf", isSelf);
        model.addAttribute("currentTab", tab);
        model.addAttribute("activeNav", "profile");

        return "user/profile";
    }
}
