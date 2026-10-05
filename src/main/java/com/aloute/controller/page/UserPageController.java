package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class UserPageController {
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;

    public UserPageController(CurrentUserProvider currentUserProvider, UserService userService) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
    }

    @GetMapping("/users/{userId}")
    public String profile(@PathVariable Long userId, Model model) {
        Long currentUserId = currentUserProvider.requireUserId();
        model.addAttribute("currentUser", userService.getProfile(currentUserId));
        model.addAttribute("profile", userService.getProfile(userId));
        model.addAttribute("activeNav", "profile");
        return "user/profile";
    }
}
