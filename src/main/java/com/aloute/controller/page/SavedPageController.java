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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class SavedPageController {

    private final PostService postService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"/saved", "/bookmarks"})
    public String showSavedPosts(
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

        var res = postService.bookmarkedPosts(currentUserId, page, size);

        model.addAttribute("posts", res != null ? res.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "saved");
        model.addAttribute("activeTab", "saved");

        return "post/saved";
    }
}
