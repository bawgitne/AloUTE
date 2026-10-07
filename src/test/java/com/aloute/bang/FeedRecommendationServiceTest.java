package com.aloute.bang;

import com.aloute.service.FeedRecommendationService;
import com.aloute.service.FeedRecommendationService.PostCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Kiểm thử đơn vị cho thuật toán gợi ý & xếp hạng bảng tin Feed
 * Người thực hiện: Ngô Anh Bằng (MSSV: 24110166) - Tuần 3
 */
class FeedRecommendationServiceTest {

    private FeedRecommendationService feedRecommendationService;
    private Instant now;

    @BeforeEach
    void setUp() {
        feedRecommendationService = new FeedRecommendationService();
        now = Instant.parse("2026-10-07T10:00:00Z");
    }

    @Test
    @DisplayName("TC-W3-01: Recency Score - Bài viết vừa tạo (t=0) đạt điểm độ mới tối đa xấp xỉ 1.0")
    void calculateRecencyScore_newlyCreated_shouldReturnMaxScore() {
        double score = feedRecommendationService.calculateRecencyScore(now, now);
        assertThat(score).isCloseTo(1.0, offset(0.001));
    }

    @Test
    @DisplayName("TC-W3-02: Recency Score - Bài viết sau 12 giờ (chu kỳ bán rã) giảm còn đúng 0.5")
    void calculateRecencyScore_afterTwelveHours_shouldHalveScore() {
        Instant twelveHoursAgo = now.minus(Duration.ofHours(12));
        double score = feedRecommendationService.calculateRecencyScore(twelveHoursAgo, now);
        assertThat(score).isCloseTo(0.5, offset(0.01));
    }

    @Test
    @DisplayName("TC-W3-03: Recency Score - Bài viết sau 24 giờ giảm còn 0.25")
    void calculateRecencyScore_afterTwentyFourHours_shouldQuarterScore() {
        Instant twentyFourHoursAgo = now.minus(Duration.ofHours(24));
        double score = feedRecommendationService.calculateRecencyScore(twentyFourHoursAgo, now);
        assertThat(score).isCloseTo(0.25, offset(0.01));
    }

    @Test
    @DisplayName("TC-W3-03b: Recency Score - Thời gian null trả về 0.0")
    void calculateRecencyScore_nullCreatedAt_shouldReturnZero() {
        double score = feedRecommendationService.calculateRecencyScore(null, now);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-W3-04: Social Score - Không có quan hệ nào trả về 0.0")
    void calculateSocialScore_noConnection_shouldReturnZero() {
        double score = feedRecommendationService.calculateSocialScore(false, false, false);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-W3-05: Social Score - Đang follow tác giả trả về tỷ lệ tương ứng")
    void calculateSocialScore_followingOnly_shouldReturnNormalized() {
        double score = feedRecommendationService.calculateSocialScore(true, false, false);
        assertThat(score).isCloseTo(1.0 / 1.5, offset(0.001));
    }

    @Test
    @DisplayName("TC-W3-06: Social Score - Quan hệ tối đa (Follow + Mutual + Same Community) bị chặn trên ở 1.0")
    void calculateSocialScore_allConnections_shouldCapAtOne() {
        double score = feedRecommendationService.calculateSocialScore(true, true, true);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TC-W3-07: Engagement Score - Tương tác bằng 0 trả về điểm 0.0")
    void calculateEngagementScore_zeroEngagement_shouldReturnZero() {
        double score = feedRecommendationService.calculateEngagementScore(0, 0, 0, 0);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-W3-08: Engagement Score - Nhiều tương tác tăng trưởng theo hàm logarit mượt mà")
    void calculateEngagementScore_highEngagement_shouldScaleLogarithmically() {
        double score = feedRecommendationService.calculateEngagementScore(100, 20, 10, 5);
        // raw = 100*1 + 20*2 + 10*3 + 5*2 = 100 + 40 + 30 + 10 = 180
        // log10(181) / 3.0 ≈ 2.2576 / 3.0 ≈ 0.752
        assertThat(score).isGreaterThan(0.7).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("TC-W3-09: Relevance Score - Tổng hợp điểm theo 4 thành phần đúng trọng số")
    void calculateRelevanceScore_validCandidate_shouldComputeWeightedTotal() {
        PostCandidate candidate = PostCandidate.builder()
                .postId(101L)
                .authorId(1L)
                .createdAt(now.minus(Duration.ofHours(6)))
                .isFollowingAuthor(true)
                .isMutualConnection(false)
                .isSameCommunity(true)
                .topicSimilarity(0.8)
                .likeCount(50)
                .replyCount(10)
                .repostCount(5)
                .bookmarkCount(2)
                .build();

        double totalScore = feedRecommendationService.calculateRelevanceScore(candidate, now);

        assertThat(totalScore).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(candidate.getScore()).isEqualTo(totalScore);
    }

    @Test
    @DisplayName("TC-W3-10: Ranking - Xếp hạng danh sách ứng viên theo thứ tự điểm giảm dần")
    void rankCandidates_multipleCandidates_shouldSortDescendingByScore() {
        // Candidate High: Mới tạo, theo dõi, topic phù hợp cao
        PostCandidate cHigh = PostCandidate.builder()
                .postId(1L)
                .authorId(10L)
                .createdAt(now.minus(Duration.ofMinutes(30)))
                .isFollowingAuthor(true)
                .isMutualConnection(true)
                .isSameCommunity(true)
                .topicSimilarity(0.95)
                .likeCount(200)
                .replyCount(50)
                .repostCount(20)
                .bookmarkCount(15)
                .build();

        // Candidate Medium: Đăng cách 12 giờ, không theo dõi nhưng chung nhóm
        PostCandidate cMed = PostCandidate.builder()
                .postId(2L)
                .authorId(20L)
                .createdAt(now.minus(Duration.ofHours(12)))
                .isFollowingAuthor(false)
                .isMutualConnection(false)
                .isSameCommunity(true)
                .topicSimilarity(0.5)
                .likeCount(20)
                .replyCount(5)
                .repostCount(1)
                .bookmarkCount(2)
                .build();

        // Candidate Low: Đăng 3 ngày trước, người lạ, tương tác 0
        PostCandidate cLow = PostCandidate.builder()
                .postId(3L)
                .authorId(30L)
                .createdAt(now.minus(Duration.ofDays(3)))
                .isFollowingAuthor(false)
                .isMutualConnection(false)
                .isSameCommunity(false)
                .topicSimilarity(0.1)
                .likeCount(0)
                .replyCount(0)
                .repostCount(0)
                .bookmarkCount(0)
                .build();

        List<PostCandidate> ranked = feedRecommendationService.rankCandidates(List.of(cLow, cHigh, cMed), now);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).getPostId()).isEqualTo(1L);
        assertThat(ranked.get(1).getPostId()).isEqualTo(2L);
        assertThat(ranked.get(2).getPostId()).isEqualTo(3L);

        assertThat(ranked.get(0).getScore()).isGreaterThan(ranked.get(1).getScore());
        assertThat(ranked.get(1).getScore()).isGreaterThan(ranked.get(2).getScore());
    }

    @Test
    @DisplayName("TC-W3-10b: Ranking - Danh sách rỗng hoặc null xử lý an toàn")
    void rankCandidates_emptyOrNull_shouldReturnEmptyList() {
        assertThat(feedRecommendationService.rankCandidates(List.of(), now)).isEmpty();
        assertThat(feedRecommendationService.rankCandidates(null, now)).isEmpty();
    }
}
