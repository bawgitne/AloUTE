package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.PostResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.FeedService;
import com.aloute.service.PostService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Validated
public class FeedController {

    private final FeedService feedService;
    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"", "/", "/home"})
    public ApiResponse<List<PostResponse>> getHomeFeed(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        Page<PostResponse> page = feedService.getHomeFeed(pageable, currentUserId);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }

    @GetMapping("/following")
    public ApiResponse<List<PostResponse>> getFollowingFeed(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        Page<PostResponse> page = feedService.getFollowingFeed(pageable, currentUserId);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }

    @GetMapping("/explore")
    public ApiResponse<List<PostResponse>> getExploreFeed(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        Page<PostResponse> page = feedService.getExploreFeed(pageable, currentUserId);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<PostResponse>> getRecommendedFeed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long currentUserId = currentUserProvider.getCurrentUserId().orElse(null);
        return postService.getRecommendedFeed(currentUserId, page, size);
    }
}
