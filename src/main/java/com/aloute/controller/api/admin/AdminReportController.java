package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.admin.AdminReportDetailResponse;
import com.aloute.dto.response.admin.AdminReportSummaryResponse;
import com.aloute.dto.response.admin.ReportDecisionResponse;
import com.aloute.dto.response.admin.ReportReviewResponse;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminReportService;
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
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/reports")
    public ApiResponse<List<AdminReportSummaryResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) Long reviewedBy,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = com.aloute.service.admin.AdminSortHelper.createPageable(
                page, size, sort, com.aloute.service.admin.AdminSortHelper.REPORT_SORT_FIELDS, "createdAt"
        );

        Page<AdminReportSummaryResponse> reportPage = adminReportService.getReports(status, targetType, reason, reviewedBy, pageable);
        return ApiResponse.page(reportPage.getContent(), PageMeta.from(reportPage));
    }

    @GetMapping("/reports/{reportId}")
    public ApiResponse<AdminReportDetailResponse> getReportDetail(
            @PathVariable @Positive Long reportId
    ) {
        return ApiResponse.success(adminReportService.getReportDetail(reportId));
    }

    @PostMapping("/reports/{reportId}/start-review")
    public ApiResponse<ReportReviewResponse> startReview(
            @PathVariable @Positive Long reportId,
            @Valid @RequestBody AdminStartReportReviewRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminReportService.startReview(adminId, reportId, request));
    }

    @PostMapping("/reports/{reportId}/decisions")
    public ApiResponse<ReportDecisionResponse> decideReport(
            @PathVariable @Positive Long reportId,
            @Valid @RequestBody AdminReportDecisionRequest request
    ) {
        Long adminId = currentUserProvider.requireUserId();
        return ApiResponse.success(adminReportService.decideReport(adminId, reportId, request));
    }
}
