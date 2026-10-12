package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityService;
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
public class SearchPageController {

    private final PostService postService;
    private final UserService userService;
    private final CommunityService communityService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/search")
    public String showSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);

        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception ignored) {}
        }

        String query = q != null ? q.trim() : "";
        if ("communities".equalsIgnoreCase(tab)) {
            var commRes = communityService.list(query, page, size, currentUserId);
            model.addAttribute("communities", commRes != null ? commRes.getData() : List.of());
        } else if ("people".equalsIgnoreCase(tab)) {
            // If user search is needed
            model.addAttribute("users", List.of());
        } else {
            var postRes = postService.searchPosts(query, page, size, currentUserId);
            model.addAttribute("posts", postRes != null ? postRes.getData() : List.of());
        }

        model.addAttribute("searchQuery", query);
        model.addAttribute("currentTab", tab);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "search");
        model.addAttribute("activeTab", "search");

        return "search/index";
    }
}
