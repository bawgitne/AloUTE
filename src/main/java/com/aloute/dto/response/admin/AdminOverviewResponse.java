package com.aloute.dto.response.admin;

import java.time.Instant;
import java.util.List;

public record AdminOverviewResponse(
        Instant asOf,
        String period,
        OverviewMetrics metrics,
        List<DailyActivity> dailyActivity,
        List<Long> recentReportIds,
        List<Long> recentModerationActionIds
) {
    public record OverviewMetrics(
            long totalUsers,
            long activeUsers,
            long totalPosts,
            long postsCreatedToday,
            long pendingReports,
            long reviewedReports,
            long totalCommunities
    ) {}

    public record DailyActivity(
            String date,
            long newUsers,
            long newPosts
    ) {}
}
