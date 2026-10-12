package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.TopicService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/topics")
@RequiredArgsConstructor
@Validated
public class UserTopicController {
    private final TopicService topicService;
    private final CurrentUserProvider currentUserProvider;

    @PutMapping("/{topicId}")
    public ApiResponse<TopicResponse> follow(@PathVariable @Positive Long topicId) {
        return ApiResponse.success(topicService.follow(currentUserProvider.requireUserId(), topicId));
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> unfollow(@PathVariable @Positive Long topicId) {
        topicService.unfollow(currentUserProvider.requireUserId(), topicId);
        return ResponseEntity.noContent().build();
    }
}
