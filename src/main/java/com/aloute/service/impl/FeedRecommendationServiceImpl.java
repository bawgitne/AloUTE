package com.aloute.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.aloute.service.FeedRecommendationService;
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
public class FeedRecommendationServiceImpl implements FeedRecommendationService {

    private static final double LAMBDA = Math.log(2) / HALF_LIFE_HOURS;

    @Override
    public double calculateRecencyScore(Instant createdAt, Instant now) {
        if (createdAt == null) return 0.0;
        double diffHours = Math.max(0.0, Duration.between(createdAt, now).toMinutes() / 60.0);
        return Math.exp(-LAMBDA * diffHours);
    }

    @Override
    public double calculateSocialScore(boolean isFollowing, boolean isMutual, boolean isSameCommunity) {
        double score = 0.0;
        if (isFollowing) score += 1.0;
        if (isMutual) score += 0.5;
        if (isSameCommunity) score += 0.3;
        return Math.min(1.0, score / 1.5);
    }

    @Override
    public double calculateEngagementScore(long likes, long replies, long reposts, long quotes) {
        double rawEngagement = 1.0 * Math.max(0, likes)
                + 2.0 * Math.max(0, replies)
                + 3.0 * Math.max(0, reposts)
                + 2.0 * Math.max(0, quotes);
        double logScore = Math.log10(1.0 + rawEngagement);
        return Math.min(1.0, logScore / 3.0);
    }

    @Override
    public double calculateRelevanceScore(PostCandidate candidate, Instant now) {
        double recency = calculateRecencyScore(candidate.getCreatedAt(), now);
        double social = calculateSocialScore(candidate.isFollowingAuthor(), candidate.isMutualConnection(), candidate.isSameCommunity());
        double topic = Math.max(0.0, Math.min(1.0, candidate.getTopicSimilarity()));
        double engagement = calculateEngagementScore(
                candidate.getLikeCount(),
                candidate.getReplyCount(),
                candidate.getRepostCount(),
                candidate.getQuoteCount()
        );

        double totalScore = (WEIGHT_RECENCY * recency)
                + (WEIGHT_SOCIAL * social)
                + (WEIGHT_TOPIC * topic)
                + (WEIGHT_ENGAGEMENT * engagement);

        candidate.setScore(totalScore);
        return totalScore;
    }

    @Override
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
