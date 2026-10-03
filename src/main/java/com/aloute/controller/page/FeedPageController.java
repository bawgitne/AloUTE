package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FeedPageController {
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;

    public FeedPageController(CurrentUserProvider currentUserProvider, UserService userService) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
    }

    @GetMapping("/feed")
    public String feed(Model model) {
        Long userId = currentUserProvider.requireUserId();
        model.addAttribute("currentUser", userService.getProfile(userId));
        model.addAttribute("activeNav", "feed");
        return "feed/index";
    }
}
