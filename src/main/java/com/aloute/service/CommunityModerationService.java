package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.CommunityManageStatsResponse;
import com.aloute.dto.response.CommunityModerationActionResponse;
import com.aloute.dto.response.PostResponse;

import java.util.List;

/**
 * Community-scoped post moderation (approve / reject / remove) and the
 * management dashboard data feeds. Every method enforces that the actor is
 * the community owner or an ADMIN-role member of that specific community.
 */
public interface CommunityModerationService {

    /** Publish a PENDING (or previously REJECTED) community post. */
    PostResponse approvePost(Long actorId, Long communityId, Long postId);

    /** Reject a PENDING submission with an optional reason shown to the author. */
    PostResponse rejectPost(Long actorId, Long communityId, Long postId, String reason);

    /**
     * Soft-remove a published community post (audit trail preserved; the post,
     * its media, replies and other data are never hard-deleted).
     */
    void removePost(Long actorId, Long communityId, Long postId, String reason);

    /** PENDING submissions of the community, newest first. */
    ApiResponse<List<PostResponse>> pendingPosts(Long actorId, Long communityId, int page, int size);

    /** Published (APPROVED + legacy) community posts, optionally filtered by keyword. */
    ApiResponse<List<PostResponse>> publishedPosts(Long actorId, Long communityId, String q, int page, int size);

    /** Dashboard overview counters (members / published / pending / removed). */
    CommunityManageStatsResponse stats(Long actorId, Long communityId);

    /** Most recent community-scoped moderation audit entries. */
    List<CommunityModerationActionResponse> recentActions(Long actorId, Long communityId);
}
