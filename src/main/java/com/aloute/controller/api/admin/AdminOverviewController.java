package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.admin.AdminOverviewResponse;
import com.aloute.service.admin.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> getOverview() {
        return ApiResponse.success(adminOverviewService.getOverview());
    }
}
