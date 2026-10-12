package com.aloute.controller.page;

import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import com.aloute.service.TopicService;
import com.aloute.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SearchPageController {

    private final CommunityService communityService;
    private final TopicService topicService;
    private final PostService postService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "all", "posts", "communities", "topics", "people"
    );

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tab,
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

        String query = q != null ? q.trim() : "";
        String rawCat = (category != null && !category.isBlank()) ? category.trim().toLowerCase()
                : ((tab != null && !tab.isBlank()) ? tab.trim().toLowerCase() : "all");

        String normalizedCategory = VALID_CATEGORIES.contains(rawCat) ? rawCat : "all";

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));

        List<PostResponse> posts = Collections.emptyList();
        List<CommunityResponse> communities = Collections.emptyList();
        List<TopicResponse> topics = Collections.emptyList();
        List<ProfileResponse> people = Collections.emptyList();

        boolean postsError = false;
        boolean communitiesError = false;
        boolean topicsError = false;
        boolean peopleError = false;

        PageMeta postsMeta = null;
        PageMeta communitiesMeta = null;
        PageMeta topicsMeta = null;

        if (!query.isEmpty()) {
            // Posts search via postService.searchPosts(q, ...)
            if ("all".equals(normalizedCategory) || "posts".equals(normalizedCategory)) {
                try {
                    var postRes = postService.searchPosts(query, safePage, safeSize, currentUserId);
                    if (postRes != null) {
                        if (postRes.getData() != null) {
                            posts = postRes.getData();
                        }
                        if (postRes.meta() instanceof PageMeta pm) {
                            postsMeta = pm;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Search posts failed for query '{}': {}", query, e.getMessage());
                    postsError = true;
                }
            }

            // Communities search via communityService.list(q, ...)
            if ("all".equals(normalizedCategory) || "communities".equals(normalizedCategory)) {
                try {
                    var commRes = communityService.list(query, safePage, safeSize, currentUserId);
                    if (commRes != null) {
                        if (commRes.getData() != null) {
                            communities = commRes.getData();
                        }
                        if (commRes.meta() instanceof PageMeta pm) {
                            communitiesMeta = pm;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Search communities failed for query '{}': {}", query, e.getMessage());
                    communitiesError = true;
                }
            }

            // Topics search via topicService.list(q, ...)
            if ("all".equals(normalizedCategory) || "topics".equals(normalizedCategory)) {
                try {
                    var topicRes = topicService.list(query, safePage, safeSize);
                    if (topicRes != null) {
                        if (topicRes.getData() != null) {
                            topics = topicRes.getData();
                        }
                        if (topicRes.meta() instanceof PageMeta pm) {
                            topicsMeta = pm;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Search topics failed for query '{}': {}", query, e.getMessage());
                    topicsError = true;
                }
            }

            // People search via exact username lookup per contract
            if ("all".equals(normalizedCategory) || "people".equals(normalizedCategory)) {
                String usernameLookup = query.startsWith("@") ? query.substring(1).trim() : query;
                if (!usernameLookup.isEmpty()) {
                    try {
                        ProfileResponse foundUser = userService.getProfileByUsername(usernameLookup);
                        if (foundUser != null) {
                            people = List.of(foundUser);
                        }
                    } catch (com.aloute.common.exception.ApiException e) {
                        if (e.getStatus() == org.springframework.http.HttpStatus.NOT_FOUND || "USER_NOT_FOUND".equals(e.getCode())) {
                            log.debug("Exact user lookup not found for '{}'", usernameLookup);
                        } else {
                            log.warn("Search user lookup failed for '{}': {}", usernameLookup, e.getMessage());
                            peopleError = true;
                        }
                    } catch (Exception e) {
                        log.warn("Search user lookup failed for '{}': {}", usernameLookup, e.getMessage());
                        peopleError = true;
                    }
                }
            }
        }

        model.addAttribute("q", query);
        model.addAttribute("searchQuery", query);
        model.addAttribute("category", normalizedCategory);
        model.addAttribute("currentTab", normalizedCategory);
        model.addAttribute("tab", normalizedCategory);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("posts", posts);
        model.addAttribute("postsError", postsError);
        model.addAttribute("postsMeta", postsMeta);
        model.addAttribute("communities", communities);
        model.addAttribute("communitiesError", communitiesError);
        model.addAttribute("communitiesMeta", communitiesMeta);
        model.addAttribute("topics", topics);
        model.addAttribute("topicsError", topicsError);
        model.addAttribute("topicsMeta", topicsMeta);
        model.addAttribute("people", people);
        model.addAttribute("peopleError", peopleError);
        model.addAttribute("users", people);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeNav", "search");
        model.addAttribute("activeTab", "search");

        return "search/index";
    }
}
