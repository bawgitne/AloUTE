package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.admin.*;
import com.aloute.entity.Report;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import com.aloute.entity.User;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.admin.AdminMappingHelper;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminReportService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportServiceImpl implements AdminReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AdminModerationService adminModerationService;
    private final AdminMappingHelper adminMappingHelper;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReportSummaryResponse> getReports(ReportStatus status, String targetType, ReportReason reason, Long reviewedBy, Pageable pageable) {
        Specification<Report> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (reviewedBy != null) {
                predicates.add(cb.equal(root.get("reviewedBy").get("id"), reviewedBy));
            }
            if (targetType != null && !targetType.isBlank()) {
                if ("POST".equalsIgnoreCase(targetType)) {
                    predicates.add(cb.isNotNull(root.get("reportedPostId")));
                } else if ("USER".equalsIgnoreCase(targetType)) {
                    predicates.add(cb.isNotNull(root.get("reportedUser")));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return reportRepository.findAll(spec, pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailResponse getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND, "Report not found"));

        String type;
        Long targetId;
        if (report.getReportedPostId() != null) {
            type = "POST";
            targetId = report.getReportedPostId();
        } else if (report.getReportedUser() != null) {
            type = "USER";
            targetId = report.getReportedUser().getId();
        } else {
            type = "UNKNOWN";
            targetId = 0L;
        }

        AdminReportDetailResponse.ReportTarget target = new AdminReportDetailResponse.ReportTarget(type, targetId);
        boolean canStartReview = report.getStatus() == ReportStatus.PENDING;
        boolean canDecide = report.getStatus() == ReportStatus.REVIEWED;
        AdminReportDetailResponse.ReportCapabilities capabilities = new AdminReportDetailResponse.ReportCapabilities(canStartReview, canDecide);

        Long reviewerId = report.getReviewedBy() != null ? report.getReviewedBy().getId() : null;

        return new AdminReportDetailResponse(
                report.getId(),
                target,
                adminMappingHelper.toUserSummary(report.getReporter()),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                reviewerId,
                report.getReviewedAt(),
                report.getClosedAt(),
                report.getResolutionNote(),
                report.getCreatedAt(),
                report.getVersion(),
                capabilities
        );
    }

    @Override
    @Transactional
    public ReportReviewResponse startReview(Long actorAdminId, Long reportId, AdminStartReportReviewRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND, "Report not found"));

        if (!report.getVersion().equals(request.expectedVersion())) {
            throw new ApiException(ErrorCode.REPORT_VERSION_CONFLICT, "Report data was modified concurrently");
        }

        if (report.getStatus() != ReportStatus.PENDING) {
            if (report.getStatus() == ReportStatus.REVIEWED) {
                if (report.getReviewedBy() != null && !report.getReviewedBy().getId().equals(actorAdminId)) {
                    throw new ApiException(ErrorCode.REPORT_ALREADY_IN_REVIEW, "Report is already in review by another administrator");
                }
            } else {
                throw new ApiException(ErrorCode.REPORT_INVALID_TRANSITION, "Cannot start review on a closed report");
            }
        }

        User reviewer = userRepository.findById(actorAdminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Admin user not found"));

        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedBy(reviewer);
        report.setReviewedAt(Instant.now());

        Report savedReport = reportRepository.save(report);

        return new ReportReviewResponse(
                savedReport.getId(),
                savedReport.getStatus().name(),
                reviewer.getId(),
                savedReport.getReviewedAt(),
                savedReport.getVersion()
        );
    }

    @Override
    @Transactional
    public ReportDecisionResponse decideReport(Long actorAdminId, Long reportId, AdminReportDecisionRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND, "Report not found"));

        if (!report.getVersion().equals(request.expectedVersion())) {
            throw new ApiException(ErrorCode.REPORT_VERSION_CONFLICT, "Report data was modified concurrently");
        }

        if (report.getStatus() != ReportStatus.REVIEWED) {
            throw new ApiException(ErrorCode.REPORT_INVALID_TRANSITION, "Report must be in REVIEWED status before making a decision");
        }

        if (report.getReviewedBy() == null || !report.getReviewedBy().getId().equals(actorAdminId)) {
            throw new ApiException(ErrorCode.REPORT_REVIEWER_REQUIRED, "Only the assigned reviewer can decide this report");
        }

        Instant now = Instant.now();
        Long resolutionActionId = null;
        String decision = request.decision();

        if ("RESOLVE".equalsIgnoreCase(decision)) {
            report.setStatus(ReportStatus.RESOLVED);
            report.setClosedAt(now);
            report.setResolutionNote(request.resolutionNote());

            if (request.resolutionAction() != null) {
                AdminReportDecisionRequest.ResolutionAction action = request.resolutionAction();
                if ("POST_REMOVE".equalsIgnoreCase(action.type())) {
                    if (report.getReportedPostId() == null) {
                        throw new ApiException(ErrorCode.BAD_REQUEST, "Report does not target a post for POST_REMOVE action");
                    }
                    PostRemovalActionResponse removal = adminModerationService.removePost(
                            actorAdminId,
                            report.getReportedPostId(),
                            new AdminRemovePostRequest(
                                    action.reasonCode() != null ? action.reasonCode() : report.getReason(),
                                    action.reasonDetail() != null ? action.reasonDetail() : request.resolutionNote(),
                                    report.getId(),
                                    action.publicNotice()
                            )
                    );
                    resolutionActionId = removal.actionId();
                }
            }
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            report.setStatus(ReportStatus.REJECTED);
            report.setClosedAt(now);
            report.setResolutionNote(request.resolutionNote());
        } else {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid decision: " + decision + ". Expected RESOLVE or REJECT");
        }

        Report savedReport = reportRepository.save(report);

        return new ReportDecisionResponse(
                savedReport.getId(),
                savedReport.getStatus().name(),
                resolutionActionId,
                now,
                savedReport.getVersion()
        );
    }

    private AdminReportSummaryResponse toSummaryResponse(Report report) {
        String targetType;
        Long targetId;
        if (report.getReportedPostId() != null) {
            targetType = "POST";
            targetId = report.getReportedPostId();
        } else if (report.getReportedUser() != null) {
            targetType = "USER";
            targetId = report.getReportedUser().getId();
        } else {
            targetType = "UNKNOWN";
            targetId = 0L;
        }

        String excerpt = report.getDescription() != null
                ? (report.getDescription().length() > 100 ? report.getDescription().substring(0, 100) + "..." : report.getDescription())
                : "";

        Long reviewerId = report.getReviewedBy() != null ? report.getReviewedBy().getId() : null;

        return new AdminReportSummaryResponse(
                report.getId(),
                targetType,
                targetId,
                adminMappingHelper.toUserSummary(report.getReporter()),
                report.getReason(),
                excerpt,
                report.getStatus(),
                reviewerId,
                report.getCreatedAt(),
                report.getVersion()
        );
    }
}
