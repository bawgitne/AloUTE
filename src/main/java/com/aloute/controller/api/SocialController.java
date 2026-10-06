package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.SocialRelationResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.SocialService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
@Validated
public class SocialController {
    private final SocialService socialService;
    private final CurrentUserProvider currentUserProvider;
    public SocialController(SocialService socialService, CurrentUserProvider currentUserProvider) { this.socialService=socialService; this.currentUserProvider=currentUserProvider; }
    @PutMapping("/follows/{userId}") public ApiResponse<SocialRelationResponse> follow(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.follow(currentUserProvider.requireUserId(),userId));
    }
    @DeleteMapping("/follows/{userId}") public ResponseEntity<Void> unfollow(@PathVariable @Positive Long userId) {
        socialService.unfollow(currentUserProvider.requireUserId(),userId); return ResponseEntity.noContent().build();
    }
}
