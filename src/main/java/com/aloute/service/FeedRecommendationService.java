package com.aloute.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service hiện thực hóa thuật toán chấm điểm và xếp hạng bài viết cho bảng tin Feed
 * theo nghiên cứu đề xuất tại docs/bang/FEED_RECOMMENDATION_ALGORITHM.md (Ngô Anh Bằng phụ trách).
 */
@Slf4j
@Service
public class FeedRecommendationService {

    // Trọng số chuẩn hóa theo nghiên cứu
    public static final double WEIGHT_RECENCY = 0.25;
    public static final double WEIGHT_SOCIAL = 0.35;
    public static final double WEIGHT_TOPIC = 0.25;
    public static final double WEIGHT_ENGAGEMENT = 0.15;

    // Chu kỳ bán rã (half-life): 12 giờ
    public static final double HALF_LIFE_HOURS = 12.0;
    private static final double LAMBDA = Math.log(2) / HALF_LIFE_HOURS;

    @Data
    @Builder
    public static class PostCandidate {
        private Long postId;
        private Long authorId;
        private Instant createdAt;
        private boolean isFollowingAuthor;
        private boolean isMutualConnection;
        private boolean isSameCommunity;
        private double topicSimilarity; // 0.0 -> 1.0 (Cosine similarity)
        private int likeCount;
        private int replyCount;
        private int repostCount;
        private int bookmarkCount;

        // Điểm tính toán
        private double score;
    }

    /**
     * Tính điểm độ mới theo thời gian dựa trên hàm phân rã mũ (Exponential Decay).
     */
    public double calculateRecencyScore(Instant createdAt, Instant now) {
        if (createdAt == null) return 0.0;
        double diffHours = Math.max(0.0, Duration.between(createdAt, now).toMinutes() / 60.0);
        return Math.exp(-LAMBDA * diffHours);
    }

    /**
     * Tính điểm quan hệ mạng xã hội giữa người xem và tác giả bài viết.
     */
    public double calculateSocialScore(boolean isFollowing, boolean isMutual, boolean isSameCommunity) {
        double score = 0.0;
        if (isFollowing) score += 1.0;
        if (isMutual) score += 0.5;
        if (isSameCommunity) score += 0.3;
        return Math.min(1.0, score / 1.5); // Chuẩn hóa về [0, 1]
    }

    /**
     * Tính điểm tương tác cộng đồng theo hàm logarit.
     */
    public double calculateEngagementScore(int likes, int replies, int reposts, int bookmarks) {
        double rawEngagement = 1.0 * Math.max(0, likes)
                + 2.0 * Math.max(0, replies)
                + 3.0 * Math.max(0, reposts)
                + 2.0 * Math.max(0, bookmarks);
        // Logarithmic scaling, chuẩn hóa với cơ số 10
        double logScore = Math.log10(1.0 + rawEngagement);
        return Math.min(1.0, logScore / 3.0); // 1000 tương tác quy đổi đạt max 1.0
    }

    /**
     * Tính toán tổng điểm phù hợp (Relevance Score) cho một bài viết ứng viên.
     */
    public double calculateRelevanceScore(PostCandidate candidate, Instant now) {
        double recency = calculateRecencyScore(candidate.getCreatedAt(), now);
        double social = calculateSocialScore(candidate.isFollowingAuthor(), candidate.isMutualConnection(), candidate.isSameCommunity());
        double topic = Math.max(0.0, Math.min(1.0, candidate.getTopicSimilarity()));
        double engagement = calculateEngagementScore(
                candidate.getLikeCount(),
                candidate.getReplyCount(),
                candidate.getRepostCount(),
                candidate.getBookmarkCount()
        );

        double totalScore = (WEIGHT_RECENCY * recency)
                + (WEIGHT_SOCIAL * social)
                + (WEIGHT_TOPIC * topic)
                + (WEIGHT_ENGAGEMENT * engagement);

        candidate.setScore(totalScore);
        return totalScore;
    }

    /**
     * Xếp hạng danh sách ứng viên bài viết theo thứ tự điểm giảm dần.
     */
    public List<PostCandidate> rankCandidates(List<PostCandidate> candidates, Instant now) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        for (PostCandidate c : candidates) {
            calculateRelevanceScore(c, now);
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(PostCandidate::getScore).reversed())
                .collect(Collectors.toList());
    }
}
