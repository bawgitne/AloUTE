package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class PostPageController {
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final PostService postService;

    public PostPageController(CurrentUserProvider currentUserProvider, UserService userService, PostService postService) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/post/create")
    public String create(Model model) {
        Long userId = currentUserProvider.requireUserId();
        model.addAttribute("currentUser", userService.getProfile(userId));
        model.addAttribute("activeNav", "create-post");
        return "post/create";
    }

    @GetMapping("/posts/{postId}")
    public String detail(@PathVariable Long postId, Model model) {
        Long userId = currentUserProvider.getCurrentUserId().orElse(null);
        if (userId != null) {
            model.addAttribute("currentUser", userService.getProfile(userId));
        }
        try {
            var post = postService.get(postId, userId);
            model.addAttribute("post", post);
            var repliesPage = postService.replies(postId, 0, 50, userId);
            model.addAttribute("replies", repliesPage != null ? repliesPage.getData() : List.of());
        } catch (Exception ex) {
            model.addAttribute("post", null);
        }
        model.addAttribute("activeNav", "feed");
        return "post/detail";
    }
}
