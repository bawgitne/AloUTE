package com.aloute.service.admin.impl;

import com.aloute.dto.response.admin.AdminOverviewResponse;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.Report;
import com.aloute.entity.ReportStatus;
import com.aloute.repository.*;
import com.aloute.service.admin.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewServiceImpl implements AdminOverviewService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final CommunityRepository communityRepository;
    private final ModerationActionRepository moderationActionRepository;

    @Override
    public AdminOverviewResponse getOverview() {
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActive(true);
        long totalPosts = postRepository.count();
        long postsCreatedToday = postRepository.countByCreatedAtBetween(todayStart, now);
        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);
        long reviewedReports = reportRepository.countByStatus(ReportStatus.REVIEWED);
        long totalCommunities = communityRepository.count();

        AdminOverviewResponse.OverviewMetrics metrics = new AdminOverviewResponse.OverviewMetrics(
                totalUsers,
                activeUsers,
                totalPosts,
                postsCreatedToday,
                pendingReports,
                reviewedReports,
                totalCommunities
        );

        List<AdminOverviewResponse.DailyActivity> dailyActivity = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            long newUsers = userRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long newPosts = postRepository.countByCreatedAtBetween(dayStart, dayEnd);
            dailyActivity.add(new AdminOverviewResponse.DailyActivity(day.toString(), newUsers, newPosts));
        }

        List<Long> recentReportIds = reportRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(Report::getId)
                .toList();

        List<Long> recentModerationActionIds = moderationActionRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(ModerationAction::getId)
                .toList();

        return new AdminOverviewResponse(
                now,
                "7d",
                metrics,
                dailyActivity,
                recentReportIds,
                recentModerationActionIds
        );
    }
}
