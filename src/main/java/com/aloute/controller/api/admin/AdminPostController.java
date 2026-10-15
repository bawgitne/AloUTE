package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.dto.response.admin.ModerationActionResponse;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminPostController {

    private final AdminPostService adminPostService;
    private final AdminModerationService adminModerationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/posts")
    public ApiResponse<List<AdminPostSummaryResponse>> getPosts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) String contentState,
            @RequestParam(required = false) Boolean reportedOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = com.aloute.service.admin.AdminSortHelper.createPageable(
                page, size, sort, com.aloute.service.admin.AdminSortHelper.POST_SORT_FIELDS, "createdAt"
        );

        Page<AdminPostSummaryResponse> postPage = adminPostService.getPosts(q, authorId, communityId, contentState, reportedOnly, pageable);
        return ApiResponse.page(postPage.getContent(), PageMeta.from(postPage));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<AdminPostDetailResponse> getPostDetail(
            @PathVariable @Positive Long postId
    ) {
        return ApiResponse.success(adminPostService.getPostDetail(postId));
    }

    @PostMapping("/posts/{postId}/remove")
    public ApiResponse<PostRemovalActionResponse> removePost(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody AdminRemovePostRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminModerationService.removePost(adminId, postId, request));
    }

    @PostMapping("/posts/{postId}/restore")
    public ApiResponse<PostRestoreActionResponse> restorePost(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody AdminRestorePostRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminModerationService.restorePost(adminId, postId, request));
    }

    @GetMapping("/posts/{postId}/moderation-actions")
    public ApiResponse<List<ModerationActionResponse>> getPostModerationActions(
            @PathVariable @Positive Long postId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = com.aloute.service.admin.AdminSortHelper.createPageable(
                page, size, sort, com.aloute.service.admin.AdminSortHelper.AUDIT_SORT_FIELDS, "createdAt"
        );

        Page<ModerationActionResponse> actionPage = adminModerationService.getPostModerationActions(postId, pageable);
        return ApiResponse.page(actionPage.getContent(), PageMeta.from(actionPage));
    }
}
