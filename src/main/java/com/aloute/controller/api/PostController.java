package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.UpdatePostRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
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
@RequestMapping("/api")
@Validated
public class PostController {
    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;

    public PostController(PostService postService, CurrentUserProvider currentUserProvider) {
        this.postService = postService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/feed/explore")
    public ApiResponse<List<PostResponse>> explore(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return postService.listPublic(page, size, null);
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.create(currentUserProvider.requireUserId(), request, null, null, null)));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> get(@PathVariable @Positive Long postId) {
        return ApiResponse.success(postService.get(postId, null));
    }

    @PatchMapping("/posts/{postId}")
    public ApiResponse<PostResponse> update(@PathVariable @Positive Long postId,
                                            @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.success(postService.update(currentUserProvider.requireUserId(), postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long postId) {
        postService.delete(currentUserProvider.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{postId}/replies")
    public ApiResponse<List<PostResponse>> replies(@PathVariable @Positive Long postId,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return postService.replies(postId, page, size, null);
    }

    @PostMapping("/posts/{postId}/replies")
    public ResponseEntity<ApiResponse<PostResponse>> reply(@PathVariable @Positive Long postId,
                                                          @Valid @RequestBody CreatePostRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.create(userId, request, null, postId, null)));
    }

    @PostMapping("/posts/{postId}/quotes")
    public ResponseEntity<ApiResponse<PostResponse>> quote(@PathVariable @Positive Long postId,
                                                          @Valid @RequestBody CreatePostRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.create(userId, request, null, null, postId)));
    }

    @PutMapping("/posts/{postId}/likes/me")
    public ApiResponse<PostResponse> like(@PathVariable @Positive Long postId) {
        return ApiResponse.success(postService.like(currentUserProvider.requireUserId(), postId));
    }

    @DeleteMapping("/posts/{postId}/likes/me")
    public ResponseEntity<Void> unlike(@PathVariable @Positive Long postId) {
        postService.unlike(currentUserProvider.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{postId}/bookmarks/me")
    public ApiResponse<PostResponse> bookmark(@PathVariable @Positive Long postId) {
        return ApiResponse.success(postService.bookmark(currentUserProvider.requireUserId(), postId));
    }

    @DeleteMapping("/posts/{postId}/bookmarks/me")
    public ResponseEntity<Void> removeBookmark(@PathVariable @Positive Long postId) {
        postService.removeBookmark(currentUserProvider.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{postId}/reposts/me")
    public ApiResponse<PostResponse> repost(@PathVariable @Positive Long postId) {
        return ApiResponse.success(postService.repost(currentUserProvider.requireUserId(), postId));
    }

    @DeleteMapping("/posts/{postId}/reposts/me")
    public ResponseEntity<Void> removeRepost(@PathVariable @Positive Long postId) {
        postService.removeRepost(currentUserProvider.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }
}
