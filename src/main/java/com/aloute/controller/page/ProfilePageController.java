package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Controller
public class ProfilePageController {

    private final UserService userService;
    private final PostService postService;
    private final com.aloute.service.SocialService socialService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/profile")
    public String showMyProfile(
            @RequestParam(required = false, defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            ProfileResponse current = userService.getProfile(currentUserId);
            if (current != null && current.username() != null && !current.username().isBlank()) {
                String query = (tab != null && !tab.equals("posts")) ? "?tab=" + tab : "";
                return "redirect:/users/" + current.username() + query;
            }
        } catch (Exception e) {
            log.warn("Could not retrieve profile for current user {}: {}", currentUserId, e.getMessage());
        }
        return showUserProfileByIdentifier(String.valueOf(currentUserId), tab, page, size, model);
    }

    @GetMapping({"/profile/{username}", "/@{username}", "/u/{username}", "/user/{username}", "/users/{username}"})
    public String showUserProfile(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        // Canonical slug enforcement: if numeric ID is used in URL, redirect to /users/{username}
        if (username != null && username.matches("\\d+")) {
            try {
                ProfileResponse target = userService.getProfile(Long.parseLong(username));
                if (target != null && target.username() != null && !target.username().isBlank()) {
                    String query = (tab != null && !tab.equals("posts")) ? "?tab=" + tab : "";
                    return "redirect:/users/" + target.username() + query;
                }
            } catch (Exception e) {
                log.warn("Profile not found by id for redirect: {}", username);
            }
        }
        return showUserProfileByIdentifier(username, tab, page, size, model);
    }

    private String showUserProfileByIdentifier(
            String identifier,
            String tab,
            int page,
            int size,
            Model model
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        ProfileResponse targetProfile = null;
        try {
            if (identifier.matches("\\d+")) {
                targetProfile = userService.getProfile(Long.parseLong(identifier));
            } else {
                targetProfile = userService.getProfileByUsername(identifier);
            }
        } catch (Exception e) {
            log.warn("Profile not found for identifier: {}", identifier);
        }

        ProfileResponse currentUser = null;
        if (currentUserId != null) {
            try {
                currentUser = userService.getProfile(currentUserId);
            } catch (Exception ignored) {}
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));

        ApiResponse<List<PostResponse>> postsRes = null;
        boolean postsError = false;
        if (targetProfile != null) {
            try {
                postsRes = postService.userPosts(targetProfile.id(), safePage, safeSize, currentUserId);
            } catch (Exception e) {
                log.warn("Failed to load user posts for profile {}: {}", targetProfile.id(), e.getMessage());
                postsError = true;
            }
        }

        boolean isSelf = (currentUserId != null && targetProfile != null && currentUserId.equals(targetProfile.id()));

        List<PostResponse> userPosts = (postsRes != null && postsRes.getData() != null) ? postsRes.getData() : List.of();
        PageMeta pageMeta = (postsRes != null && postsRes.meta() instanceof PageMeta pm) ? pm : null;

        List<PostResponse> savedPosts = null;
        boolean savedPostsError = false;
        if (isSelf) {
            try {
                var savedRes = postService.bookmarkedPosts(currentUserId, 0, 20);
                if (savedRes != null && savedRes.getData() != null) {
                    savedPosts = savedRes.getData();
                } else {
                    savedPosts = List.of();
                }
            } catch (Exception e) {
                log.warn("Failed to load saved posts for profile {}: {}", currentUserId, e.getMessage());
                savedPostsError = true;
            }
        }

        boolean isBlocked = false;
        boolean isMuted = false;
        boolean isRestricted = false;
        boolean isFollowing = false;
        if (currentUserId != null && targetProfile != null && !isSelf) {
            try {
                var rel = socialService.getRelationStatus(currentUserId, targetProfile.id());
                isBlocked = rel.blocked();
                isMuted = rel.muted();
                isRestricted = rel.restricted();
                isFollowing = rel.following();
            } catch (Exception e) {
                log.warn("Failed to check social relation status between user {} and target {}: {}", currentUserId, targetProfile.id(), e.getMessage());
            }
        }

        model.addAttribute("profile", targetProfile);
        model.addAttribute("targetUser", targetProfile);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("title", targetProfile != null ? targetProfile.displayName() : "Hồ sơ");
        model.addAttribute("posts", userPosts);
        model.addAttribute("profilePosts", postsError ? null : userPosts);
        model.addAttribute("postsError", postsError);
        model.addAttribute("savedPosts", savedPosts);
        model.addAttribute("savedPostsError", savedPostsError);
        model.addAttribute("pageMeta", pageMeta);
        model.addAttribute("isSelf", isSelf);
        model.addAttribute("isBlocked", isBlocked);
        model.addAttribute("isMuted", isMuted);
        model.addAttribute("isRestricted", isRestricted);
        model.addAttribute("isFollowing", isFollowing);
        model.addAttribute("currentTab", tab);
        model.addAttribute("activeNav", "profile");
        model.addAttribute("activeTab", "profile");

        return "user/profile";
    }
}
