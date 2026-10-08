package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class FeedPageController {
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final PostService postService;

    public FeedPageController(CurrentUserProvider currentUserProvider, UserService userService, PostService postService) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping({"/", "/feed"})
    public String feed(Model model) {
        Long userId = currentUserProvider.getCurrentUserId().orElse(null);
        if (userId != null) {
            model.addAttribute("currentUser", userService.getProfile(userId));
        }
        model.addAttribute("activeNav", "feed");
        try {
            var postPage = postService.getRecommendedFeed(userId, 0, 20);
            model.addAttribute("posts", postPage != null ? postPage.getData() : List.of());
        } catch (Exception ex) {
            model.addAttribute("posts", List.of());
        }
        return "feed/index";
    }
}
