package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.admin.ModerationActionDetailResponse;
import com.aloute.dto.response.admin.ModerationActionResponse;
import com.aloute.entity.ModerationTargetType;
import com.aloute.service.admin.AdminModerationService;
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
public class AdminModerationActionController {

    private final AdminModerationService adminModerationService;

    @GetMapping("/moderation-actions")
    public ApiResponse<List<ModerationActionResponse>> getModerationActions(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) ModerationTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = sortParts[0].trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, property));

        Page<ModerationActionResponse> actionPage = adminModerationService.getModerationActions(actorId, targetType, targetId, actionType, pageable);
        return ApiResponse.page(actionPage.getContent(), PageMeta.from(actionPage));
    }

    @GetMapping("/moderation-actions/{actionId}")
    public ApiResponse<ModerationActionDetailResponse> getModerationActionDetail(
            @PathVariable @Positive Long actionId
    ) {
        return ApiResponse.success(adminModerationService.getModerationActionDetail(actionId));
    }
}
