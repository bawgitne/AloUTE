package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreateCommunityRequest;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.UpdateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRoleRequest;
import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
@Validated
public class CommunityController {
    private final CommunityService communityService;
    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<CommunityResponse>> list(@RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return communityService.list(q, page, size, viewerId);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityResponse>> create(@Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(communityService.create(currentUserProvider.requireUserId(), request)));
    }

    @GetMapping("/{communityId}")
    public ApiResponse<CommunityResponse> get(@PathVariable @Positive Long communityId) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return ApiResponse.success(communityService.get(communityId, viewerId));
    }

    @PatchMapping("/{communityId}")
    public ApiResponse<CommunityResponse> update(@PathVariable @Positive Long communityId,
                                                 @Valid @RequestBody UpdateCommunityRequest request) {
        return ApiResponse.success(communityService.update(currentUserProvider.requireUserId(), communityId, request));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long communityId) {
        communityService.delete(currentUserProvider.requireUserId(), communityId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/join")
    public ApiResponse<CommunityMembershipResponse> join(@PathVariable @Positive Long communityId) {
        return ApiResponse.success(communityService.join(currentUserProvider.requireUserId(), communityId));
    }

    @GetMapping("/{communityId}/members")
    public ApiResponse<List<CommunityMembershipResponse>> members(@PathVariable @Positive Long communityId,
                                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return communityService.members(communityId, page, size, currentUserProvider.getCurrentUserId().orElse(null));
    }

    @DeleteMapping("/{communityId}/members/me")
    public ResponseEntity<Void> leave(@PathVariable @Positive Long communityId) {
        communityService.leave(currentUserProvider.requireUserId(), communityId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{communityId}/members/{userId}/role")
    public ApiResponse<CommunityMembershipResponse> updateRole(@PathVariable @Positive Long communityId,
                                                               @PathVariable @Positive Long userId,
                                                               @Valid @RequestBody UpdateCommunityRoleRequest request) {
        return ApiResponse.success(communityService.updateRole(currentUserProvider.requireUserId(), communityId, userId, request));
    }

    @GetMapping("/{communityId}/posts")
    public ApiResponse<List<PostResponse>> posts(@PathVariable @Positive Long communityId,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return postService.communityPosts(communityId, page, size, viewerId);
    }

    @PostMapping("/{communityId}/posts")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@PathVariable @Positive Long communityId,
                                                               @Valid @RequestBody CreatePostRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.create(userId, request, communityId, null, null)));
    }
}
