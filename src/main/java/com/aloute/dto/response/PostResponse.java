package com.aloute.dto.response;

import com.aloute.entity.PostModerationStatus;
import com.aloute.entity.PostVisibility;
import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id,
        UserSummary author,
        String content,
        Long parentPostId,
        Long quotedPostId,
        Long communityId,
        PostVisibility visibility,
        boolean allowReply,
        boolean allowQuote,
        List<MediaResponse> media,
        List<TopicResponse> topics,
        Object poll,
        long likeCount,
        long replyCount,
        long repostCount,
        long quoteCount,
        long viewCount,
        PostViewerStateResponse viewerState,
        PostCapabilitiesResponse capabilities,
        Instant createdAt,
        Instant updatedAt,
        CommunitySummaryResponse community,
        PostModerationStatus moderationStatus,
        String moderationReason
) {
    /** Compatibility constructor for pre-moderation call sites and tests. */
    public PostResponse(Long id, UserSummary author, String content, Long parentPostId, Long quotedPostId,
                        Long communityId, PostVisibility visibility, boolean allowReply, boolean allowQuote,
                        List<MediaResponse> media, List<TopicResponse> topics, Object poll,
                        long likeCount, long replyCount, long repostCount, long quoteCount, long viewCount,
                        PostViewerStateResponse viewerState, PostCapabilitiesResponse capabilities,
                        Instant createdAt, Instant updatedAt) {
        this(id, author, content, parentPostId, quotedPostId, communityId, visibility, allowReply, allowQuote,
                media, topics, poll, likeCount, replyCount, repostCount, quoteCount, viewCount,
                viewerState, capabilities, createdAt, updatedAt, null, null, null);
    }
}
