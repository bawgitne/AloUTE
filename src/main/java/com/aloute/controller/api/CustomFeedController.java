package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.PostResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.FeedService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class CustomFeedController {

    private final FeedService feedService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<Object>> listCustomFeeds(@PageableDefault(size = 20) Pageable pageable) {
        Page<Object> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        return ApiResponse.page(emptyPage.getContent(), PageMeta.from(emptyPage));
    }

    @GetMapping("/{feedId}/posts")
    public ApiResponse<List<PostResponse>> getFeedPosts(
            @PathVariable Long feedId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        Page<PostResponse> page = feedService.getExploreFeed(pageable, currentUserId);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }
}
