package com.aloute.controller.page;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.CommunityResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class CommunityPageController {

    private final CommunityService communityService;
    private final PostService postService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/communities")
    public String showCommunities(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception e) {
                log.warn("Could not retrieve current user profile for id {}", currentUserId);
            }
        }

        var res = communityService.list(q, page, size, currentUserId);
        model.addAttribute("communities", res != null ? res.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("searchQuery", q != null ? q : "");
        model.addAttribute("activeNav", "communities");

        return "community/index";
    }

    @GetMapping({"/c/{slug}", "/communities/{slug}"})
    public String showCommunityDetail(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model,
            HttpServletResponse response
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception e) {
                log.warn("Could not retrieve current user profile for id {}", currentUserId);
            }
        }

        CommunityResponse community = null;
        try {
            if (slug.matches("\\d+")) {
                community = communityService.get(Long.parseLong(slug), currentUserId);
            } else {
                community = communityService.getBySlug(slug, currentUserId);
            }
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        // Do not query Posts or render detail metadata when Community is absent or concealed.
        Long communityId = community != null ? community.id() : null;
        var postsRes = communityId != null ? postService.communityPosts(communityId, page, size, currentUserId) : null;

        model.addAttribute("community", community);
        model.addAttribute("posts", postsRes != null ? postsRes.getData() : List.of());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "communities");

        return "community/detail";
    }
}
