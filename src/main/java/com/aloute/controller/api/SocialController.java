package com.aloute.controller.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.SocialRelationResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.SocialService;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Validated
public class SocialController {
    private final SocialService socialService;
    private final CurrentUserProvider currentUserProvider;
    @PutMapping("/follows/{userId}")
    public ApiResponse<SocialRelationResponse> follow(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.follow(currentUserProvider.requireUserId(), userId));
    }

    @DeleteMapping("/follows/{userId}")
    public ResponseEntity<Void> unfollow(@PathVariable @Positive Long userId) {
        socialService.unfollow(currentUserProvider.requireUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    /** BL-05 (issue #92): the current user's pending private-account follow-request inbox. */
    @GetMapping("/follow-requests")
    public ApiResponse<List<com.aloute.dto.response.FollowRequestSummaryResponse>> pendingFollowRequests(
            @PageableDefault(size = 20) Pageable pageable) {
        return socialService.pendingFollowRequests(currentUserProvider.requireUserId(), pageable);
    }

    /** BL-05 (issue #92): approve a pending request (target only). */
    @PostMapping("/follow-requests/{requestId}/approve")
    public ApiResponse<com.aloute.dto.response.SocialRelationResponse> approveFollowRequest(
            @PathVariable @Positive Long requestId) {
        return ApiResponse.success(socialService.approveFollowRequest(currentUserProvider.requireUserId(), requestId));
    }

    /** BL-05 (issue #92): reject a pending request (target only). */
    @PostMapping("/follow-requests/{requestId}/reject")
    public ApiResponse<Void> rejectFollowRequest(
            @PathVariable @Positive Long requestId) {
        socialService.rejectFollowRequest(currentUserProvider.requireUserId(), requestId);
        return ApiResponse.success(null);
    }

    @GetMapping("/blocks")
    public ApiResponse<java.util.List<com.aloute.dto.response.UserSummary>> getBlockedUsers(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return socialService.blockedUsers(currentUserProvider.requireUserId(), pageable);
    }

    @PostMapping("/blocks/by-username/{username}")
    public ApiResponse<SocialRelationResponse> blockByUsername(@PathVariable String username) {
        return ApiResponse.success(socialService.blockByUsername(currentUserProvider.requireUserId(), username));
    }

    @PutMapping("/blocks/{userId}")
    public ApiResponse<SocialRelationResponse> block(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.block(currentUserProvider.requireUserId(), userId));
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable @Positive Long userId) {
        socialService.unblock(currentUserProvider.requireUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mutes")
    public ApiResponse<List<UserSummary>> mutedUsers(@PageableDefault(size = 50) Pageable pageable) {
        return socialService.mutedUsers(currentUserProvider.requireUserId(), pageable);
    }

    @PostMapping("/mutes/by-username/{username}")
    public ApiResponse<SocialRelationResponse> muteByUsername(@PathVariable String username) {
        return ApiResponse.success(socialService.muteByUsername(currentUserProvider.requireUserId(), username));
    }

    @PutMapping("/mutes/{userId}")
    public ApiResponse<SocialRelationResponse> mute(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.mute(currentUserProvider.requireUserId(), userId));
    }

    @DeleteMapping("/mutes/{userId}")
    public ResponseEntity<Void> unmute(@PathVariable @Positive Long userId) {
        socialService.unmute(currentUserProvider.requireUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/restrictions/{userId}")
    public ApiResponse<SocialRelationResponse> restrict(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.restrict(currentUserProvider.requireUserId(), userId));
    }

    @DeleteMapping("/restrictions/{userId}")
    public ResponseEntity<Void> unrestrict(@PathVariable @Positive Long userId) {
        socialService.unrestrict(currentUserProvider.requireUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/relations/{userId}")
    public ApiResponse<com.aloute.dto.response.UserRelationStatusResponse> getRelationStatus(@PathVariable @Positive Long userId) {
        return ApiResponse.success(socialService.getRelationStatus(currentUserProvider.requireUserId(), userId));
    }
}
