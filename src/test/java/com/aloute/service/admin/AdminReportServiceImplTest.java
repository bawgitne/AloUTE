package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.ReportDecisionResponse;
import com.aloute.dto.response.admin.ReportReviewResponse;
import com.aloute.entity.Report;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import com.aloute.entity.User;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.admin.impl.AdminReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminReportServiceImplTest {

    private ReportRepository reportRepository;
    private UserRepository userRepository;
    private AdminModerationService adminModerationService;
    private AdminMappingHelper adminMappingHelper;
    private AdminReportServiceImpl service;

    private User admin;
    private Report report;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        userRepository = mock(UserRepository.class);
        adminModerationService = mock(AdminModerationService.class);
        adminMappingHelper = mock(AdminMappingHelper.class);

        service = new AdminReportServiceImpl(
                reportRepository,
                userRepository,
                adminModerationService,
                adminMappingHelper
        );

        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");

        report = new Report();
        report.setId(50L);
        report.setReportedPostId(10L);
        report.setReason(ReportReason.HARASSMENT);
        report.setDescription("Reported for offensive content");
        report.setStatus(ReportStatus.PENDING);
        report.setVersion(0L);
    }

    @Test
    @DisplayName("startReview transitions report from PENDING to REVIEWED successfully")
    void startReviewSuccess() {
        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminStartReportReviewRequest request = new AdminStartReportReviewRequest(0L);

        ReportReviewResponse response = service.startReview(1L, 50L, request);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewedBy()).isEqualTo(1L);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(report.getReviewedBy()).isEqualTo(admin);
        assertThat(report.getReviewedAt()).isNotNull();

        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("startReview detects optimistic locking version conflict (Invariant 3)")
    void startReviewVersionConflict() {
        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));

        AdminStartReportReviewRequest request = new AdminStartReportReviewRequest(999L);

        assertThatThrownBy(() -> service.startReview(1L, 50L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.REPORT_VERSION_CONFLICT));

        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    @DisplayName("decideReport with RESOLVE and POST_REMOVE resolves report and removes post atomically")
    void decideReportResolveWithPostRemovalSuccess() {
        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedBy(admin);
        report.setReviewedAt(Instant.now());

        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostRemovalActionResponse removalResponse = new PostRemovalActionResponse(
                88L, 10L, "POST_REMOVE", "ADMIN_REMOVED", 99L, Instant.now()
        );
        when(adminModerationService.removePost(eq(1L), eq(10L), any())).thenReturn(removalResponse);

        AdminReportDecisionRequest.ResolutionAction action = new AdminReportDecisionRequest.ResolutionAction(
                "POST_REMOVE",
                ReportReason.HARASSMENT,
                "Content confirmed as severe harassment",
                "Violating post has been removed"
        );

        AdminReportDecisionRequest request = new AdminReportDecisionRequest(
                0L,
                "RESOLVE",
                "Violations confirmed and appropriate post removal applied",
                action
        );

        ReportDecisionResponse response = service.decideReport(1L, 50L, request);

        assertThat(response.reportId()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolutionActionId()).isEqualTo(88L);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getClosedAt()).isNotNull();

        verify(adminModerationService).removePost(eq(1L), eq(10L), any());
        verify(reportRepository).save(report);
    }
}
