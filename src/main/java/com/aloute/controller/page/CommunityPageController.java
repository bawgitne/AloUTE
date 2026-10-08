package com.aloute.controller.page;

import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.AuthService;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CommunityPageController {

    private final CommunityService communityService;
    private final PostService postService;
    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/communities")
    public String showCommunities(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        UserResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = authService.getCurrentUser(currentUserId);
            } catch (Exception ignored) {}
        }

        var res = communityService.list(q, page, size, currentUserId);
        model.addAttribute("communities", res != null ? res.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("searchQuery", q != null ? q : "");
        model.addAttribute("activeNav", "communities");

        return "community/index";
    }

    @GetMapping("/communities/{communityId}")
    public String showCommunityDetail(
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        UserResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = authService.getCurrentUser(currentUserId);
            } catch (Exception ignored) {}
        }

        CommunityResponse community = null;
        try {
            community = communityService.get(communityId, currentUserId);
        } catch (Exception ignored) {}

        var postsRes = postService.communityPosts(communityId, page, size, currentUserId);

        model.addAttribute("community", community);
        model.addAttribute("posts", postsRes != null ? postsRes.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "communities");

        return "community/detail";
    }
}
