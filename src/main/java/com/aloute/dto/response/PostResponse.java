package com.aloute.dto.response;

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
        Instant updatedAt
) {}
