package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.UpdateProfileRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.SocialService;
import com.aloute.service.UserService;
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
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final PostService postService;
    private final SocialService socialService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable @Positive Long userId) {
        return ApiResponse.success(userService.getProfile(userId));
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<ProfileResponse> byUsername(@PathVariable String username) {
        return ApiResponse.success(userService.getProfileByUsername(username));
    }

    @GetMapping({"/me/profile", "/profile"})
    public ApiResponse<ProfileResponse> getMyProfile() {
        Long currentUserId = currentUserProvider.requireUserId();
        return ApiResponse.success(userService.getProfile(currentUserId));
    }

    @RequestMapping(value = {"/me/profile", "/profile"}, method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<ProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateCurrentProfile(currentUserProvider.requireUserId(), request));
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<List<PostResponse>> posts(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return postService.userPosts(userId, page, size, viewerId);
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserSummary>> followers(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return socialService.followers(userId, PageRequest.of(page, size));
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserSummary>> following(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return socialService.following(userId, PageRequest.of(page, size));
    }
}
