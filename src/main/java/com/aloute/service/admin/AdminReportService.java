package com.aloute.service.admin;

import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.admin.AdminReportDetailResponse;
import com.aloute.dto.response.admin.AdminReportSummaryResponse;
import com.aloute.dto.response.admin.ReportDecisionResponse;
import com.aloute.dto.response.admin.ReportReviewResponse;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {

    Page<AdminReportSummaryResponse> getReports(ReportStatus status, String targetType, ReportReason reason, Long reviewedBy, Pageable pageable);

    AdminReportDetailResponse getReportDetail(Long reportId);

    ReportReviewResponse startReview(Long actorAdminId, Long reportId, AdminStartReportReviewRequest request);

    ReportDecisionResponse decideReport(Long actorAdminId, Long reportId, AdminReportDecisionRequest request);
}
