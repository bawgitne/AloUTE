package com.aloute.service.impl;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.CommunityManageStatsResponse;
import com.aloute.dto.response.CommunityModerationActionResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.*;
import com.aloute.event.NotificationEvent;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.service.CommunityModerationService;
import com.aloute.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommunityModerationServiceImpl implements CommunityModerationService {

    public static final String ACTION_APPROVE = "COMMUNITY_POST_APPROVE";
    public static final String ACTION_REJECT = "COMMUNITY_POST_REJECT";
    public static final String ACTION_REMOVE = "COMMUNITY_POST_REMOVE";

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostRepository postRepository;
    private final PostModerationRemovalRepository postModerationRemovalRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ProfileRepository profileRepository;
    private final PostService postService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PostResponse approvePost(Long actorId, Long communityId, Long postId) {
        Community community = requireManageableCommunity(actorId, communityId);
        Post post = requireCommunityPostForUpdate(community, postId);

        PostModerationStatus status = post.getModerationStatus();
        if (status != PostModerationStatus.PENDING && status != PostModerationStatus.REJECTED) {
            throw new ApiException(ErrorCode.POST_MODERATION_STATE_INVALID,
                    "Only PENDING or REJECTED submissions can be approved");
        }

        User moderator = actor(community, actorId);
        post.setModerationStatus(PostModerationStatus.APPROVED);
        post.setModerationReason(null);
        postRepository.save(post);

        recordAction(moderator, community, postId, ACTION_APPROVE, null);

        notifyAuthor(post, moderator, "COMMUNITY_POST_APPROVED", post.getContent());
        return postService.get(post.getId(), actorId);
    }

    @Override
    @Transactional
    public PostResponse rejectPost(Long actorId, Long communityId, Long postId, String reason) {
        Community community = requireManageableCommunity(actorId, communityId);
        Post post = requireCommunityPostForUpdate(community, postId);

        if (post.getModerationStatus() != PostModerationStatus.PENDING) {
            throw new ApiException(ErrorCode.POST_MODERATION_STATE_INVALID,
                    "Only PENDING submissions can be rejected; remove published posts instead");
        }

        String normalizedReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        if (normalizedReason != null && normalizedReason.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Reason must not exceed 500 characters");
        }

        User moderator = actor(community, actorId);
        post.setModerationStatus(PostModerationStatus.REJECTED);
        post.setModerationReason(normalizedReason);
        postRepository.save(post);

        recordAction(moderator, community, postId, ACTION_REJECT, normalizedReason);

        notifyAuthor(post, moderator, "COMMUNITY_POST_REJECTED",
                normalizedReason != null ? "Lý do: " + normalizedReason : post.getContent());
        return postService.get(post.getId(), actorId);
    }

    @Override
    @Transactional
    public void removePost(Long actorId, Long communityId, Long postId, String reason) {
        Community community = requireManageableCommunity(actorId, communityId);
        Post post = requireCommunityPostForUpdate(community, postId);

        if (post.getModerationStatus() != null && post.getModerationStatus() != PostModerationStatus.APPROVED) {
            throw new ApiException(ErrorCode.POST_MODERATION_STATE_INVALID,
                    "Only published posts can be removed; reject PENDING submissions instead");
        }

        String normalizedReason = reason != null && !reason.isBlank() ? reason.trim() : null;

        User moderator = actor(community, actorId);
        Instant now = Instant.now();
        post.setDeletedAt(now);
        postRepository.save(post);

        ModerationAction action = recordAction(moderator, community, postId, ACTION_REMOVE, normalizedReason);

        // Same soft-removal provenance pattern as system-admin removal: keeps the
        // restore pathway (and audit chain) intact without destroying any data.
        PostModerationRemoval removal = new PostModerationRemoval();
        removal.setPost(post);
        removal.setRemoveAction(action);
        removal.setRemovedAt(now);
        postModerationRemovalRepository.save(removal);

        notifyAuthor(post, moderator, "COMMUNITY_POST_REMOVED",
                normalizedReason != null ? "Lý do: " + normalizedReason : post.getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> pendingPosts(Long actorId, Long communityId, int page, int size) {
        requireManageableCommunity(actorId, communityId);
        Page<Post> postPage = postRepository.findAll(
                communityPostSpec(communityId, PostModerationStatus.PENDING, null),
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.page(postService.toResponses(postPage.getContent(), actorId), PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> publishedPosts(Long actorId, Long communityId, String q, int page, int size) {
        requireManageableCommunity(actorId, communityId);
        Page<Post> postPage = postRepository.findAll(
                communityPostSpec(communityId, null, q),
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.page(postService.toResponses(postPage.getContent(), actorId), PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityManageStatsResponse stats(Long actorId, Long communityId) {
        Community community = requireManageableCommunity(actorId, communityId);
        return new CommunityManageStatsResponse(
                community.getMemberCount(),
                postRepository.countPublishedByCommunityId(communityId),
                postRepository.countPendingByCommunityId(communityId),
                postModerationRemovalRepository.countActiveByCommunityId(communityId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityModerationActionResponse> recentActions(Long actorId, Long communityId) {
        requireManageableCommunity(actorId, communityId);
        return moderationActionRepository
                .findRecentByCommunityId(communityId, PageRequest.of(0, 10))
                .stream()
                .map(action -> new CommunityModerationActionResponse(
                        action.getId(),
                        toUserSummary(action.getActor()),
                        action.getTargetId(),
                        action.getActionType(),
                        blankToNull(action.getReasonDetail()),
                        action.getCreatedAt()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Authorization & shared helpers
    // ---------------------------------------------------------------------

    /**
     * Loads a live community, preserves strict private-community concealment
     * (unauthorized actors receive 404, not 403), then requires the actor to be
     * the owner or an ADMIN-role member of THIS community.
     */
    private Community requireManageableCommunity(Long actorId, Long communityId) {
        Community community = communityRepository.findByIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMUNITY_NOT_FOUND));

        boolean isOwner = community.getOwner() != null && Objects.equals(community.getOwner().getId(), actorId);
        Optional<CommunityMember> actorMember = actorId == null
                ? Optional.empty()
                : communityMemberRepository.findByCommunityIdAndUserId(communityId, actorId);

        if (community.isPrivateCommunity() && !isOwner && actorMember.isEmpty()) {
            // Concealment: same not-found response as a missing community.
            throw new ApiException(ErrorCode.COMMUNITY_NOT_FOUND);
        }

        if (!isOwner && actorMember.map(m -> m.getRole() != CommunityRole.ADMIN).orElse(true)) {
            throw new ApiException(ErrorCode.COMMUNITY_ADMIN_REQUIRED);
        }
        return community;
    }

    private User actor(Community community, Long actorId) {
        User actor = community.getOwner() != null && Objects.equals(community.getOwner().getId(), actorId)
                ? community.getOwner()
                : null;
        if (actor == null) {
            // ADMIN-role actor: resolve through membership (user is fetched lazily in tx)
            actor = communityMemberRepository.findByCommunityIdAndUserId(community.getId(), actorId)
                    .map(CommunityMember::getUser)
                    .orElseThrow(() -> new ApiException(ErrorCode.COMMUNITY_ADMIN_REQUIRED));
        }
        return actor;
    }

    /** Post lookup with pessimistic lock: prevents duplicate/concurrent decisions and lost updates. */
    private Post requireCommunityPostForUpdate(Community community, Long postId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (post.getCommunity() == null || !Objects.equals(post.getCommunity().getId(), community.getId())) {
            // Post does not belong to this community — conceal rather than disclose.
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.POST_ALREADY_DELETED);
        }
        return post;
    }

    private ModerationAction recordAction(User moderator, Community community, Long postId, String actionType, String reason) {
        ModerationAction action = new ModerationAction();
        action.setActor(moderator);
        action.setTargetType(ModerationTargetType.POST);
        action.setTargetId(postId);
        action.setActionType(actionType);
        action.setReasonCode(ReportReason.OTHER);
        action.setReasonDetail(reason != null ? reason : "");
        action.setCommunityId(community.getId());
        return moderationActionRepository.save(action);
    }

    private void notifyAuthor(Post post, User moderator, String type, String preview) {
        if (post.getUser() == null || moderator == null
                || Objects.equals(post.getUser().getId(), moderator.getId())) {
            return;
        }
        eventPublisher.publishEvent(NotificationEvent.of(
                post.getUser().getId(),
                moderator.getId(),
                type,
                "POST",
                post.getId(),
                preview));
    }

    private Specification<Post> communityPostSpec(Long communityId, PostModerationStatus status, String q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("community").get("id"), communityId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.isNull(root.get("parentPost")));
            if (status != null) {
                predicates.add(cb.equal(root.get("moderationStatus"), status));
            } else {
                predicates.add(cb.or(
                        cb.isNull(root.get("moderationStatus")),
                        cb.equal(root.get("moderationStatus"), PostModerationStatus.APPROVED)));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("content")),
                        "%" + q.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private UserSummary toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        Profile profile = profileRepository.findById(user.getId()).orElse(null);
        return new UserSummary(user.getId(), user.getUsername(),
                profile != null ? profile.getDisplayName() : user.getUsername(),
                profile != null ? profile.getAvatarUrl() : null,
                user.isVerified());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
