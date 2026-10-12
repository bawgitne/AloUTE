package com.aloute.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public interface FeedRecommendationService {

    double WEIGHT_RECENCY = 0.25;
    double WEIGHT_SOCIAL = 0.35;
    double WEIGHT_TOPIC = 0.25;
    double WEIGHT_ENGAGEMENT = 0.15;
    double HALF_LIFE_HOURS = 12.0;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PostCandidate {
        private Long postId;
        private Long authorId;
        private Instant createdAt;
        private boolean isFollowingAuthor;
        private boolean isMutualConnection;
        private boolean isSameCommunity;
        private double topicSimilarity;
        private long likeCount;
        private long replyCount;
        private long repostCount;
        private long quoteCount;
        private long bookmarkCount;
        private double score;
    }

    double calculateRecencyScore(Instant createdAt, Instant now);
    double calculateSocialScore(boolean isFollowing, boolean isMutual, boolean isSameCommunity);
    double calculateEngagementScore(long likes, long replies, long reposts, long quotes);
    double calculateRelevanceScore(PostCandidate candidate, Instant now);
    List<PostCandidate> rankCandidates(List<PostCandidate> candidates, Instant now);
}
