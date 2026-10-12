package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.TopicService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@Validated
public class TopicController {
    private final TopicService topicService;
    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<TopicResponse>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return topicService.list(q, page, size);
    }

    @GetMapping("/{topicId}")
    public ApiResponse<TopicResponse> get(@PathVariable @Positive Long topicId) {
        return ApiResponse.success(topicService.get(topicId));
    }

    @GetMapping("/{topicId}/posts")
    public ApiResponse<List<PostResponse>> posts(@PathVariable @Positive Long topicId,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return postService.topicPosts(topicId, page, size, viewerId);
    }
}
