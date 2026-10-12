package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.SocialService;
import com.aloute.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class FriendsPageController {

    private final SocialService socialService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/friends")
    public String showFriends(
            @RequestParam(required = false, defaultValue = "following") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        ProfileResponse currentUser = null;
        try {
            currentUser = userService.getProfile(currentUserId);
        } catch (Exception ignored) {}

        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        var res = "followers".equalsIgnoreCase(tab)
                ? socialService.followers(currentUserId, pageable)
                : socialService.following(currentUserId, pageable);

        model.addAttribute("users", res != null ? res.getData() : List.of());
        model.addAttribute("currentTab", tab);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "friends");
        model.addAttribute("activeTab", "friends");

        return "user/friends";
    }
}
