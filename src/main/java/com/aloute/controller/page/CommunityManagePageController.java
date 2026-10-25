package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.CommunityManageStatsResponse;
import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.CommunityModerationActionResponse;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityModerationService;
import com.aloute.service.CommunityService;
import com.aloute.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Community management dashboard (distinct from the system-admin area):
 * /communities/{id}/manage with tabs Overview / Pending posts / Posts /
 * Members / Settings. Accessible only to the community owner and ADMIN-role
 * members; private communities stay concealed (404) to outsiders.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CommunityManagePageController {

    private final CommunityService communityService;
    private final CommunityModerationService communityModerationService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"/communities/{communityId}/manage", "/communities/{communityId}/manage/"})
    public String overview(@PathVariable Long communityId, Model model, HttpServletResponse response) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            CommunityResponse community = communityService.get(communityId, currentUserId);
            requireManageable(community, currentUserId);
            CommunityManageStatsResponse stats = communityModerationService.stats(currentUserId, communityId);
            List<CommunityModerationActionResponse> recentActions =
                    communityModerationService.recentActions(currentUserId, communityId);
            populateCommon(model, currentUserId, community, "overview");
            model.addAttribute("stats", stats);
            model.addAttribute("recentActions", recentActions);
            return "community/manage/overview";
        } catch (ApiException ex) {
            return accessDeniedView(model, response, ex);
        }
    }

    @GetMapping("/communities/{communityId}/manage/pending")
    public String pendingPosts(@PathVariable Long communityId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Model model,
                               HttpServletResponse response) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            CommunityResponse community = communityService.get(communityId, currentUserId);
            requireManageable(community, currentUserId);
            ApiResponse<List<PostResponse>> posts =
                    communityModerationService.pendingPosts(currentUserId, communityId, page, size);
            populateCommon(model, currentUserId, community, "pending");
            model.addAttribute("posts", posts.getData());
            model.addAttribute("pageMeta", posts.getMeta());
            model.addAttribute("manageBaseUrl", "/communities/" + communityId + "/manage/pending");
            return "community/manage/pending";
        } catch (ApiException ex) {
            return accessDeniedView(model, response, ex);
        }
    }

    @GetMapping("/communities/{communityId}/manage/posts")
    public String managePosts(@PathVariable Long communityId,
                              @RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model,
                              HttpServletResponse response) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            CommunityResponse community = communityService.get(communityId, currentUserId);
            requireManageable(community, currentUserId);
            ApiResponse<List<PostResponse>> posts =
                    communityModerationService.publishedPosts(currentUserId, communityId, q, page, size);
            populateCommon(model, currentUserId, community, "posts");
            model.addAttribute("posts", posts.getData());
            model.addAttribute("pageMeta", posts.getMeta());
            model.addAttribute("searchQuery", q != null ? q : "");
            model.addAttribute("manageBaseUrl", "/communities/" + communityId + "/manage/posts");
            return "community/manage/posts";
        } catch (ApiException ex) {
            return accessDeniedView(model, response, ex);
        }
    }

    @GetMapping("/communities/{communityId}/manage/members")
    public String members(@PathVariable Long communityId,
                          @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          Model model,
                          HttpServletResponse response) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            CommunityResponse community = communityService.get(communityId, currentUserId);
            requireManageable(community, currentUserId);
            ApiResponse<List<CommunityMembershipResponse>> members =
                    communityService.members(communityId, q, page, size, currentUserId);
            populateCommon(model, currentUserId, community, "members");
            model.addAttribute("members", members.getData());
            model.addAttribute("pageMeta", members.getMeta());
            model.addAttribute("searchQuery", q != null ? q : "");
            model.addAttribute("manageBaseUrl", "/communities/" + communityId + "/manage/members");
            return "community/manage/members";
        } catch (ApiException ex) {
            return accessDeniedView(model, response, ex);
        }
    }

    @GetMapping("/communities/{communityId}/manage/settings")
    public String settings(@PathVariable Long communityId, Model model, HttpServletResponse response) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return "redirect:/login";
        }
        try {
            CommunityResponse community = communityService.get(communityId, currentUserId);
            requireManageable(community, currentUserId);
            populateCommon(model, currentUserId, community, "settings");
            return "community/manage/settings";
        } catch (ApiException ex) {
            return accessDeniedView(model, response, ex);
        }
    }

    // ------------------------------------------------------------------

    private void requireManageable(CommunityResponse community, Long currentUserId) {
        if (!community.canManage()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Chỉ chủ sở hữu hoặc quản trị viên cộng đồng mới được mở trang quản lý");
        }
    }

    private void populateCommon(Model model, Long currentUserId, CommunityResponse community, String tab) {
        ProfileResponse currentUser = null;
        try {
            currentUser = userService.getProfile(currentUserId);
        } catch (Exception e) {
            log.warn("Could not retrieve current user profile for id {}", currentUserId);
        }
        model.addAttribute("community", community);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "communities");
        model.addAttribute("activeManageTab", tab);
        model.addAttribute("isCommunityDetailPage", true);
        model.addAttribute("hideGlobalCreatePost", true);
    }

    private String accessDeniedView(Model model, HttpServletResponse response, ApiException ex) {
        if (ex.getStatus() != HttpStatus.NOT_FOUND && ex.getStatus() != HttpStatus.FORBIDDEN) {
            throw ex;
        }
        response.setStatus(ex.getStatus().value());
        model.addAttribute("notFound", ex.getStatus() == HttpStatus.NOT_FOUND);
        model.addAttribute("activeNav", "communities");
        model.addAttribute("isCommunityDetailPage", true);
        model.addAttribute("hideGlobalCreatePost", true);
        return "community/manage/no-access";
    }
}
