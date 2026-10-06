package com.aloute.controller.page;

import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PostPageController {
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;

    public PostPageController(CurrentUserProvider currentUserProvider, UserService userService) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
    }

    @GetMapping("/post/create")
    public String create(Model model) {
        Long userId = currentUserProvider.requireUserId();
        model.addAttribute("currentUser", userService.getProfile(userId));
        model.addAttribute("activeNav", "create-post");
        return "post/create";
    }
}
