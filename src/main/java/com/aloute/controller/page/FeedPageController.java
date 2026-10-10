package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Slf4j
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

    @GetMapping("/feed")
    public String feed(Model model) {
        Long userId = currentUserProvider.getCurrentUserId().orElse(null);
        if (userId != null) {
            model.addAttribute("currentUser", userService.getProfile(userId));
        }
        model.addAttribute("activeNav", "feed");
        try {
            var postPage = postService.getRecommendedFeed(userId, 0, 20);
            model.addAttribute("posts", postPage != null ? postPage.getData() : List.of());
            model.addAttribute("feedError", false);
            if (postPage != null && postPage.meta() != null) {
                model.addAttribute("pageMeta", postPage.meta());
            }
        } catch (Exception ex) {
            log.error("Failed to load recommended feed for userId {}", userId, ex);
            model.addAttribute("posts", List.of());
            model.addAttribute("feedError", true);
        }
        return "feed/index";
    }
}
