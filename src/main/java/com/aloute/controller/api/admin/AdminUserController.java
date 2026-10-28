package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminPostService;
import com.aloute.service.admin.AdminUserService;
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
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminPostService adminPostService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/users")
    public ApiResponse<List<AdminUserSummaryResponse>> getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = com.aloute.service.admin.AdminSortHelper.createPageable(
            page, size, sort, com.aloute.service.admin.AdminSortHelper.USER_SORT_FIELDS, "createdAt"
        );

        Page<AdminUserSummaryResponse> userPage = adminUserService.getUsers(q, role, active, pageable);
        return ApiResponse.page(userPage.getContent(), PageMeta.from(userPage));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUserDetail(
            @PathVariable @Positive Long userId
    ) {
        return ApiResponse.success(adminUserService.getUserDetail(userId));
    }

    @PostMapping("/users/{userId}/suspend")
    public ApiResponse<AdminUserModerationActionResponse> suspendUser(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AdminUserModerationRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminUserService.suspendUser(adminId, userId, request));
    }

    @PostMapping("/users/{userId}/reactivate")
    public ApiResponse<AdminUserModerationActionResponse> reactivateUser(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AdminUserModerationRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminUserService.reactivateUser(adminId, userId, request));
    }

    @PostMapping("/users/{userId}/warn")
    public ApiResponse<AdminUserModerationActionResponse> warnUser(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AdminUserModerationRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminUserService.warnUser(adminId, userId, request));
    }

    @PostMapping("/users/{userId}/reset-profile")
    public ApiResponse<AdminUserModerationActionResponse> resetUserProfile(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AdminUserModerationRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminUserService.resetUserProfile(adminId, userId, request));
    }

    @PostMapping("/users/{userId}/role")
    public ApiResponse<AdminUserModerationActionResponse> changeGlobalRole(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AdminUserRoleChangeRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminUserService.changeGlobalRole(adminId, userId, request));
    }

    @GetMapping("/users/{userId}/posts")
    public ApiResponse<List<AdminPostSummaryResponse>> getUserPosts(
            @PathVariable @Positive Long userId,
            @RequestParam(required = false) String contentState,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long viewerId = currentUserProvider.requireUserId();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AdminPostSummaryResponse> postPage = adminPostService.getUserPosts(userId, contentState, pageable, viewerId);
        return ApiResponse.page(postPage.getContent(), PageMeta.from(postPage));
    }
}
