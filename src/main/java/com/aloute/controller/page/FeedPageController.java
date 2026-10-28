package com.aloute.controller.page;

import com.aloute.common.api.PageMeta;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FeedPageController {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_SAFE_PAGE = Integer.MAX_VALUE / DEFAULT_PAGE_SIZE; // 107374182

    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final PostService postService;

    @GetMapping("/feed")
    public String feed(
            @RequestParam(name = "page", required = false, defaultValue = "0") String page,
            Model model
    ) {
        Long userId = currentUserProvider.getCurrentUserId().orElse(null);
        if (userId != null) {
            try {
                model.addAttribute("currentUser", userService.getProfile(userId));
            } catch (Exception ignored) {}
        }
        model.addAttribute("activeNav", "feed");
        model.addAttribute("isFeedPage", true);

        int validatedPage = parsePage(page);

        try {
            var postPage = postService.getRecommendedFeed(userId, validatedPage, DEFAULT_PAGE_SIZE);
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

    @GetMapping("/feed/posts")
    public String feedPosts(
            @RequestParam(name = "page", required = false, defaultValue = "0") String page,
            Model model,
            HttpServletResponse response
    ) {
        Long userId = currentUserProvider.getCurrentUserId().orElse(null);
        int validatedPage = parsePage(page);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Cookie");

        try {
            var postPage = postService.getRecommendedFeed(userId, validatedPage, DEFAULT_PAGE_SIZE);
            PageMeta meta = postPage != null && postPage.meta() instanceof PageMeta pm ? pm : null;
            model.addAttribute("posts", postPage != null ? postPage.getData() : List.of());
            model.addAttribute("pageMeta", meta);
            writeFeedHeaders(response, meta, validatedPage);
        } catch (Exception ex) {
            log.error("Failed to load feed fragment for userId {} page {}", userId, validatedPage, ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setHeader("X-Feed-Has-Next", "false");
            model.addAttribute("posts", List.of());
        }
        return "feed/_feed-posts :: postList";
    }

    private void writeFeedHeaders(HttpServletResponse response, PageMeta meta, int requestedPage) {
        if (meta == null) {
            response.setHeader("X-Feed-Page", Integer.toString(requestedPage));
            response.setHeader("X-Feed-Next-Page", Integer.toString(requestedPage));
            response.setHeader("X-Feed-Has-Next", "false");
            response.setHeader("X-Feed-Total-Pages", "0");
            response.setHeader("X-Feed-Total-Elements", "0");
            return;
        }
        response.setHeader("X-Feed-Page", Integer.toString(meta.page()));
        response.setHeader("X-Feed-Next-Page", Integer.toString(meta.page() + 1));
        response.setHeader("X-Feed-Has-Next", Boolean.toString(meta.hasNext()));
        response.setHeader("X-Feed-Total-Pages", Integer.toString(meta.totalPages()));
        response.setHeader("X-Feed-Total-Elements", Long.toString(meta.totalElements()));
    }

    private int parsePage(String pageStr) {
        if (pageStr == null || pageStr.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(pageStr.trim());
            if (parsed < 0 || parsed > MAX_SAFE_PAGE) {
                log.warn("Page query parameter '{}' out of safe range [0, {}], normalizing to 0", pageStr, MAX_SAFE_PAGE);
                return 0;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("Invalid or overflowing page query parameter '{}', normalizing to 0", pageStr);
            return 0;
        }
    }
}
