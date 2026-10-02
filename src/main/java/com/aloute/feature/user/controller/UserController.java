package com.aloute.feature.user.controller;

import com.aloute.common.api.ApiResponse;
import com.aloute.feature.social.service.FollowService;
import com.aloute.feature.user.dto.request.UpdateProfileRequest;
import com.aloute.feature.user.dto.response.ProfileResponse;
import com.aloute.feature.user.dto.response.UserSummary;
import com.aloute.feature.user.service.UserProfileService;
import com.aloute.security.currentuser.CurrentUserProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserProfileService userProfileService;
    private final FollowService followService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(
            UserProfileService userProfileService,
            FollowService followService,
            CurrentUserProvider currentUserProvider) {
        this.userProfileService = userProfileService;
        this.followService = followService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable @Positive Long userId) {
        return ApiResponse.success(userProfileService.getProfile(userId));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<ProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(
                userProfileService.updateCurrentProfile(
                        currentUserProvider.requireUserId(), request));
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserSummary>> followers(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return followService.followers(userId, PageRequest.of(page, size));
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserSummary>> following(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return followService.following(userId, PageRequest.of(page, size));
    }
}
