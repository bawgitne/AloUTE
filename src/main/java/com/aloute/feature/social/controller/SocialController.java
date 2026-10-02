package com.aloute.feature.social.controller;

import com.aloute.common.api.ApiResponse;
import com.aloute.feature.social.dto.response.SocialRelationResponse;
import com.aloute.feature.social.service.FollowService;
import com.aloute.security.currentuser.CurrentUserProvider;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
@Validated
public class SocialController {

    private final FollowService followService;
    private final CurrentUserProvider currentUserProvider;

    public SocialController(FollowService followService,
                            CurrentUserProvider currentUserProvider) {
        this.followService = followService;
        this.currentUserProvider = currentUserProvider;
    }

    @PutMapping("/follows/{userId}")
    public ApiResponse<SocialRelationResponse> follow(
            @PathVariable @Positive Long userId) {
        return ApiResponse.success(
                followService.follow(currentUserProvider.requireUserId(), userId));
    }

    @DeleteMapping("/follows/{userId}")
    public ResponseEntity<Void> unfollow(@PathVariable @Positive Long userId) {
        followService.unfollow(currentUserProvider.requireUserId(), userId);
        return ResponseEntity.noContent().build();
    }
}
