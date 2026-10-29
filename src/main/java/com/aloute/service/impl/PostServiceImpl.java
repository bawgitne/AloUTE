package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.PollCreateInput;
import com.aloute.dto.request.UpdatePostRequest;
import com.aloute.dto.response.*;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.event.NotificationEvent;
import com.aloute.service.FeedRecommendationService;
import com.aloute.service.PostService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PostServiceImpl implements PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostTopicRepository postTopicRepository;
    private final TopicRepository topicRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityRepository communityRepository;
    private final PostLikeRepository postLikeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RepostRepository repostRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;

    @Autowired(required = false)
    private PollRepository pollRepository;

    @Autowired(required = false)
    private PollOptionRepository pollOptionRepository;

    @Autowired(required = false)
    private PollVoteRepository pollVoteRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private FeedRecommendationService feedRecommendationService;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> listPublic(int page, int size, Long viewerId) {
        Specification<Post> spec = (root, query, cb) -> cb.isNull(root.get("parentPost"));
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = toResponses(postPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> getRecommendedFeed(Long viewerId, int page, int size) {
        Specification<Post> spec = (root, query, cb) -> cb.isNull(root.get("parentPost"));
        spec = spec.and(buildVisibilitySpec(viewerId))
                .and(PostSpecifications.excludeMutedAuthors(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<Post> posts = postPage.getContent();
        if (viewerId == null || feedRecommendationService == null) {
            List<PostResponse> list = toResponses(posts, viewerId);
            return ApiResponse.page(list, PageMeta.from(postPage));
        }

        // One query for the viewer's whole follow graph; the previous per-post
        // existsById probe was a page-size N+1 in the main feed path.
        Set<Long> followingIds = viewerId == null || followRepository == null ? Set.of()
                : followRepository.findByFollower_Id(viewerId).stream()
                        .map(f -> f.getFollowing().getId())
                        .collect(Collectors.toSet());

        List<FeedRecommendationService.PostCandidate> candidates = posts.stream().map(p -> {
            boolean isFollowing = p.getUser() != null && followingIds.contains(p.getUser().getId());
            return FeedRecommendationService.PostCandidate.builder()
                    .postId(p.getId())
                    .authorId(p.getUser() != null ? p.getUser().getId() : null)
                    .createdAt(p.getCreatedAt())
                    .likeCount(p.getLikeCount())
                    .replyCount(p.getReplyCount())
                    .repostCount(p.getRepostCount())
                    .quoteCount(p.getQuoteCount())
                    .isFollowingAuthor(isFollowing)
                    .build();
        }).collect(Collectors.toList());

        List<FeedRecommendationService.PostCandidate> ranked = feedRecommendationService.rankCandidates(candidates, Instant.now());
        Map<Long, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
        List<PostResponse> responses = toResponses(
                ranked.stream().map(c -> postMap.get(c.getPostId())).filter(Objects::nonNull).toList(), viewerId);

        return ApiResponse.page(responses, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse get(Long postId, Long viewerId) {
        return toResponse(requireVisiblePost(postId, viewerId), viewerId);
    }

    @Override
    @Transactional
    public PostResponse create(Long userId, CreatePostRequest request, Long communityId, Long parentPostId, Long quotedPostId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        Post parentPost = null;
        Post quotedPost = null;
        if (parentPostId != null && quotedPostId != null) {
            if (parentPostId.equals(quotedPostId)) {
                parentPost = requireVisiblePostForUpdate(parentPostId, userId);
                quotedPost = parentPost;
                if (!parentPost.isAllowReply()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
                }
                if (!quotedPost.isAllowQuote()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "QUOTE_NOT_ALLOWED", "Quote is disabled for this post");
                }
            } else if (parentPostId < quotedPostId) {
                parentPost = requireVisiblePostForUpdate(parentPostId, userId);
                if (!parentPost.isAllowReply()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
                }
                quotedPost = requireVisiblePostForUpdate(quotedPostId, userId);
                if (!quotedPost.isAllowQuote()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "QUOTE_NOT_ALLOWED", "Quote is disabled for this post");
                }
            } else {
                quotedPost = requireVisiblePostForUpdate(quotedPostId, userId);
                if (!quotedPost.isAllowQuote()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "QUOTE_NOT_ALLOWED", "Quote is disabled for this post");
                }
                parentPost = requireVisiblePostForUpdate(parentPostId, userId);
                if (!parentPost.isAllowReply()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
                }
            }
        } else if (parentPostId != null) {
            parentPost = requireVisiblePostForUpdate(parentPostId, userId);
            if (!parentPost.isAllowReply()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
            }
        } else if (quotedPostId != null) {
            quotedPost = requireVisiblePostForUpdate(quotedPostId, userId);
            if (!quotedPost.isAllowQuote()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "QUOTE_NOT_ALLOWED", "Quote is disabled for this post");
            }
        }

        Community community = null;
        if (communityId != null) {
            community = communityRepository.findByIdAndDeletedAtIsNull(communityId)
                    .orElseThrow(() -> notFound("COMMUNITY_NOT_FOUND", "Community not found"));
            if (community.isPrivateCommunity()) {
                boolean canDiscover = (community.getOwner() != null && Objects.equals(community.getOwner().getId(), userId))
                        || communityMemberRepository.existsByCommunityIdAndUserId(communityId, userId);
                if (!canDiscover) {
                    throw notFound("COMMUNITY_NOT_FOUND", "Community not found");
                }
            }
            if (!communityMemberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "COMMUNITY_MEMBERSHIP_REQUIRED", "Only community members can post there");
            }
        }

        Post post = new Post();
        post.setUser(user);
        post.setContent(request.content());
        post.setVisibility(request.visibility() != null ? request.visibility() : PostVisibility.PUBLIC);
        post.setAllowReply(request.allowReply() == null || request.allowReply());
        post.setAllowQuote(request.allowQuote() == null || request.allowQuote());
        post.setParentPost(parentPost);
        post.setQuotedPost(quotedPost);
        post.setCommunity(community);
        // Community post approval lifecycle: ordinary member submissions in an
        // approval-required community enter PENDING; every other community post
        // (approval disabled, or author is owner/admin) publishes immediately.
        if (community != null) {
            post.setModerationStatus(community.isRequirePostApproval() && !isCommunityModerator(community, userId)
                    ? PostModerationStatus.PENDING
                    : PostModerationStatus.APPROVED);
        }

        Post savedPost = postRepository.save(post);

        // Save media attachments: only the current user's own, non-deleted media may join a post,
        // and attaching marks it so MediaServiceImpl.delete() can no longer remove it from storage.
        if (request.mediaIds() != null && !request.mediaIds().isEmpty()) {
            int pos = 0;
            for (Long mediaId : request.mediaIds()) {
                MediaAsset media = mediaAssetRepository.findByIdAndOwner_IdAndDeletedAtIsNull(mediaId, userId)
                        .orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "Media not found"));
                postMediaRepository.save(new PostMedia(savedPost, media, pos++));
                media.setAttachedAt(Instant.now());
            }
        }

        // Save topic tags. topicIds is client-supplied, so it may repeat an id or carry
        // nulls; PostTopic has a unique (post_id, topic_id) index, so duplicates must be
        // collapsed here or the second insert fails the whole create with a 500.
        if (request.topicIds() != null && !request.topicIds().isEmpty()) {
            for (Long topicId : new LinkedHashSet<>(request.topicIds())) {
                if (topicId == null) {
                    continue;
                }
                Topic topic = topicRepository.findById(topicId).orElse(null);
                if (topic != null) {
                    postTopicRepository.save(new PostTopic(savedPost, topic));
                }
            }
        }

        // Persist the optional poll atomically with the post (domain §17: max one poll per post)
        if (request.poll() != null) {
            createPollForPost(savedPost, request.poll());
        }

        // Update counters and publish events
        if (parentPost != null) {
            parentPost.setReplyCount(parentPost.getReplyCount() + 1);
            postRepository.save(parentPost);
            if (parentPost.getUser() != null) {
                Long recipientId = parentPost.getUser().getId();
                String preview = isPostVisibleTo(savedPost, recipientId) ? request.content() : null;
                eventPublisher.publishEvent(NotificationEvent.of(
                        recipientId,
                        userId,
                        "POST_REPLY",
                        "POST",
                        savedPost.getId(),
                        preview
                ));
            }
        }

        if (quotedPost != null) {
            quotedPost.setQuoteCount(quotedPost.getQuoteCount() + 1);
            postRepository.save(quotedPost);
            if (quotedPost.getUser() != null) {
                Long recipientId = quotedPost.getUser().getId();
                String preview = isPostVisibleTo(savedPost, recipientId) ? request.content() : null;
                eventPublisher.publishEvent(NotificationEvent.of(
                        recipientId,
                        userId,
                        "POST_QUOTE",
                        "POST",
                        savedPost.getId(),
                        preview
                ));
            }
        }

        // BL-15: lifetime counters advance once per successfully persisted ROOT post
        // (quotes and community posts are root posts; replies are not). Soft delete
        // never decrements them — the displayed tabs recompute from live rows, so the
        // counters intentionally read >= the tab counts once posts are deleted.
        // Atomic bulk UPDATEs keep concurrent creations from losing increments.
        if (parentPost == null) {
            profileRepository.incrementPostsCount(userId);
            if (community != null) {
                communityRepository.incrementPostCount(community.getId());
            }
        }

        return get(savedPost.getId(), userId);
    }

    @Override
    @Transactional
    public PostResponse update(Long userId, Long postId, UpdatePostRequest request) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
        if (post.getDeletedAt() != null || !isPostVisibleTo(post, userId)) {
            throw notFound("POST_NOT_FOUND", "Post not found");
        }
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (communityMemberRepository == null
                    || !communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), userId)) {
                throw notFound("POST_NOT_FOUND", "Post not found");
            }
        }
        if (!Objects.equals(post.getUser().getId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only post owner can edit the post");
        }

        boolean contentChanged = request.content() != null && !request.content().equals(post.getContent());

        if (request.content() != null) post.setContent(request.content());
        if (request.visibility() != null) post.setVisibility(request.visibility());
        if (request.allowReply() != null) post.setAllowReply(request.allowReply());
        if (request.allowQuote() != null) post.setAllowQuote(request.allowQuote());

        if (contentChanged && post.getCommunity() != null) {
            applyCommunityEditModerationPolicy(post, userId);
        }

        return get(postRepository.save(post).getId(), userId);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
        if (post.getDeletedAt() != null || !isPostVisibleTo(post, userId)) {
            throw notFound("POST_NOT_FOUND", "Post not found");
        }
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (communityMemberRepository == null
                    || !communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), userId)) {
                throw notFound("POST_NOT_FOUND", "Post not found");
            }
        }
        if (!Objects.equals(post.getUser().getId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only post owner can delete the post");
        }
        post.setDeletedAt(Instant.now());
        postRepository.save(post);

        if (post.getParentPost() != null) {
            Long parentId = post.getParentPost().getId();
            postRepository.findByIdForUpdate(parentId).ifPresent(parent -> {
                parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
                postRepository.save(parent);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> replies(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId, viewerId);
        Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("parentPost").get("id"), postId);
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> repliesPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.ASC, "createdAt"))
        );
        List<PostResponse> list = toResponses(repliesPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(repliesPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> communityPosts(Long communityId, int page, int size, Long viewerId) {
        Community community = communityRepository.findByIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> notFound("COMMUNITY_NOT_FOUND", "Community not found"));
        boolean isOwner = viewerId != null && community.getOwner() != null
                && Objects.equals(community.getOwner().getId(), viewerId);
        boolean isMember = viewerId != null && communityMemberRepository != null
                && communityMemberRepository.existsByCommunityIdAndUserId(communityId, viewerId);
        if (community.isPrivateCommunity()) {
            if (!isOwner && !isMember) {
                throw notFound("COMMUNITY_NOT_FOUND", "Community not found");
            }
        }
        Specification<Post> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("community").get("id"), communityId),
                cb.isNull(root.get("parentPost"))
        );
        spec = spec.and(buildVisibilitySpec(viewerId, isOwner ? communityId : null))
                .and(PostSpecifications.excludeMutedAuthors(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = toResponses(postPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> topicPosts(Long topicId, int page, int size, Long viewerId) {
        topicRepository.findById(topicId).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Topic not found"));
        Specification<Post> spec = (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<PostTopic> ptRoot = subquery.from(PostTopic.class);
            subquery.select(ptRoot.get("post").get("id"))
                    .where(cb.equal(ptRoot.get("topic").get("id"), topicId));
            return cb.and(
                    root.get("id").in(subquery),
                    cb.isNull(root.get("parentPost"))
            );
        };
        spec = spec.and(buildVisibilitySpec(viewerId))
                .and(PostSpecifications.excludeMutedAuthors(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = toResponses(postPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> userPosts(Long userId, int page, int size, Long viewerId) {
        userRepository.findById(userId).orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));
        Specification<Post> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.isNull(root.get("parentPost"))
        );
        // Own timeline keeps the author's PENDING/REJECTED community submissions visible
        // to the author themselves (with status badges); other viewers never see them.
        boolean ownTimeline = viewerId != null && viewerId.equals(userId);
        spec = spec.and(buildVisibilitySpec(viewerId, null, ownTimeline));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = toResponses(postPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> searchPosts(String query, int page, int size, Long viewerId) {
        Specification<Post> spec = (root, q, cb) -> {
            Predicate p = cb.isNull(root.get("parentPost"));
            if (query != null && !query.isBlank()) {
                p = cb.and(p, cb.like(cb.lower(root.get("content")), "%" + query.trim().toLowerCase() + "%"));
            }
            return p;
        };
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = toResponses(postPage.getContent(), viewerId);
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> bookmarkedPosts(Long userId, int page, int size) {
        userRepository.findById(userId).orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        List<Long> blockedUserIds = new ArrayList<>();
        List<Long> followingUserIds = new ArrayList<>();
        List<Long> userCommunityIds = new ArrayList<>();

        if (blockRepository != null) {
            blockedUserIds.addAll(blockRepository.findByBlockerId(userId).stream().map(b -> b.getBlocked().getId()).toList());
            blockedUserIds.addAll(blockRepository.findByBlockedId(userId).stream().map(b -> b.getBlocker().getId()).toList());
        }
        if (followRepository != null) {
            followingUserIds.addAll(followRepository.findByFollower_Id(userId).stream().map(f -> f.getFollowing().getId()).toList());
        }
        if (communityMemberRepository != null) {
            userCommunityIds.addAll(communityMemberRepository.findByUserId(userId).stream().map(cm -> cm.getCommunity().getId()).toList());
        }

        Specification<Bookmark> spec = PostSpecifications.bookmarkVisibleForUser(
                userId, blockedUserIds, followingUserIds, userCommunityIds);

        Page<Bookmark> bookmarkPage = bookmarkRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<PostResponse> list = toResponses(bookmarkPage.getContent().stream().map(Bookmark::getPost).toList(), userId);

        return ApiResponse.page(list, PageMeta.from(bookmarkPage));
    }

    @Override
    @Transactional
    public PostResponse like(Long userId, Long postId) {
        Post post = requireVisiblePostForUpdate(postId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.save(new PostLike(user, post));
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);

            if (post.getUser() != null) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        post.getUser().getId(),
                        userId,
                        "POST_LIKE",
                        "POST",
                        postId,
                        post.getContent()
                ));
            }
        }
        return get(postId, userId);
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        Post post = requireVisiblePostForUpdate(postId, userId);
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postRepository.save(post);
        }
    }

    @Override
    @Transactional
    public PostResponse bookmark(Long userId, Long postId) {
        Post post = requireVisiblePost(postId, userId);
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (communityMemberRepository == null
                    || !communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), userId)) {
                throw new ApiException(ErrorCode.POST_ACCESS_DENIED);
            }
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        if (!bookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            bookmarkRepository.save(new Bookmark(user, post));
        }
        return get(postId, userId);
    }

    @Override
    @Transactional
    public void removeBookmark(Long userId, Long postId) {
        bookmarkRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Override
    @Transactional
    public PostResponse repost(Long userId, Long postId) {
        Post post = requireVisiblePostForUpdate(postId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        if (!repostRepository.existsByPostIdAndUserId(postId, userId)) {
            repostRepository.save(new Repost(user, post));
            post.setRepostCount(post.getRepostCount() + 1);
            postRepository.save(post);

            if (post.getUser() != null) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        post.getUser().getId(),
                        userId,
                        "POST_REPOST",
                        "POST",
                        postId,
                        post.getContent()
                ));
            }
        }
        return get(postId, userId);
    }

    @Override
    @Transactional
    public void removeRepost(Long userId, Long postId) {
        Post post = requireVisiblePostForUpdate(postId, userId);
        if (repostRepository.existsByPostIdAndUserId(postId, userId)) {
            repostRepository.deleteByPostIdAndUserId(postId, userId);
            post.setRepostCount(Math.max(0, post.getRepostCount() - 1));
            postRepository.save(post);
        }
    }

    private Post requireVisiblePost(Long postId, Long viewerId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
        if (!isPostVisibleTo(post, viewerId)) {
            throw new ApiException(ErrorCode.POST_ACCESS_DENIED);
        }
        return post;
    }

    private Post requireVisiblePostForUpdate(Long postId, Long viewerId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
        if (post.getDeletedAt() != null) {
            throw notFound("POST_NOT_FOUND", "Post not found");
        }
        if (!isPostVisibleTo(post, viewerId)) {
            throw new ApiException(ErrorCode.POST_ACCESS_DENIED);
        }
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (viewerId == null || communityMemberRepository == null
                    || !communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), viewerId)) {
                throw new ApiException(ErrorCode.POST_ACCESS_DENIED);
            }
        }
        return post;
    }

    @Override
    public boolean isPostVisibleTo(Post post, Long viewerId) {
        if (post == null || post.getDeletedAt() != null) {
            return false;
        }

        // Soft-deleted Communities are not readable through any normal Post path.
        if (post.getCommunity() != null && post.getCommunity().getDeletedAt() != null) {
            return false;
        }

        // 1. Canonical private-community invariant: non-members cannot access private community posts.
        // Actual community owner may read posts without membership row (read consistency).
        // This check MUST occur before author exception (e.g. author who left the private community)
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (viewerId == null) {
                return false;
            }
            boolean isOwner = post.getCommunity().getOwner() != null
                    && Objects.equals(post.getCommunity().getOwner().getId(), viewerId);
            boolean isMember = communityMemberRepository != null
                    && communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), viewerId);
            if (!isOwner && !isMember) {
                return false;
            }
        }

        Long authorId = post.getUser() != null ? post.getUser().getId() : null;

        // 2. Check mutual block relationship
        if (viewerId != null && authorId != null && blockRepository != null) {
            if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, authorId)
                    || blockRepository.existsByBlockerIdAndBlockedId(authorId, viewerId)) {
                return false;
            }
        }

        // 2b. Community post approval gate: PENDING/REJECTED submissions are visible
        // only to their author and to community moderators (owner / ADMIN-role member)
        // for management. Everyone else gets a concealment-grade denial.
        if (post.getCommunity() != null
                && post.getModerationStatus() != null
                && post.getModerationStatus() != PostModerationStatus.APPROVED) {
            if (viewerId == null) {
                return false;
            }
            boolean isAuthorOfSubmission = authorId != null && Objects.equals(authorId, viewerId);
            if (!isAuthorOfSubmission && !isCommunityModerator(post.getCommunity(), viewerId)) {
                return false;
            }
        }

        // 3. Author audience exception: author can view their own post if community gate and blocks allow
        if (viewerId != null && authorId != null && Objects.equals(authorId, viewerId)) {
            return true;
        }

        // 4. Guest viewer
        if (viewerId == null) {
            if (post.getUser() != null && post.getUser().isPrivateAccount()) {
                return false;
            }
            return post.getVisibility() == PostVisibility.PUBLIC;
        }

        // 5. Authenticated non-author viewer: check author account privacy
        boolean isFollowingAuthor = false;
        if (authorId != null && followRepository != null) {
            isFollowingAuthor = followRepository.existsById(new FollowId(viewerId, authorId));
        }

        if (post.getUser() != null && post.getUser().isPrivateAccount() && !isFollowingAuthor) {
            return false;
        }

        // 6. Check post visibility enum
        if (post.getVisibility() == PostVisibility.PUBLIC) {
            return true;
        }
        if (post.getVisibility() == PostVisibility.FOLLOWERS) {
            return isFollowingAuthor;
        }
        if (post.getVisibility() == PostVisibility.MENTIONED) {
            // No canonical durable Mention entity exists in schema.
            // Non-owner MENTIONED reads fail closed under canonical policy.
            return false;
        }

        return false;
    }

    @Override
    public Specification<Post> buildVisibilitySpec(Long viewerId) {
        return buildVisibilitySpec(viewerId, null, false);
    }

    private Specification<Post> buildVisibilitySpec(Long viewerId, Long extraCommunityId) {
        return buildVisibilitySpec(viewerId, extraCommunityId, false);
    }

    private Specification<Post> buildVisibilitySpec(Long viewerId, Long extraCommunityId, boolean includeOwnUnapproved) {
        List<Long> blockedUserIds = new ArrayList<>();
        List<Long> followingUserIds = new ArrayList<>();
        List<Long> userCommunityIds = new ArrayList<>();

        if (viewerId != null) {
            if (blockRepository != null) {
                blockedUserIds.addAll(blockRepository.findByBlockerId(viewerId).stream().map(b -> b.getBlocked().getId()).toList());
                blockedUserIds.addAll(blockRepository.findByBlockedId(viewerId).stream().map(b -> b.getBlocker().getId()).toList());
            }
            if (followRepository != null) {
                followingUserIds.addAll(followRepository.findByFollower_Id(viewerId).stream().map(f -> f.getFollowing().getId()).toList());
            }
            if (communityMemberRepository != null) {
                userCommunityIds.addAll(communityMemberRepository.findByUserId(viewerId).stream().map(cm -> cm.getCommunity().getId()).toList());
            }
            if (extraCommunityId != null && !userCommunityIds.contains(extraCommunityId)) {
                userCommunityIds.add(extraCommunityId);
            }
        }

        return PostSpecifications.visibleTo(viewerId, blockedUserIds, followingUserIds, userCommunityIds, includeOwnUnapproved);
    }

    @Override
    public PostResponse toResponse(Post post, Long viewerId) {
        if (post == null) return null;
        return toResponses(List.of(post), viewerId).get(0);
    }

    /**
     * Batch assembly for list rendering. A page of posts previously triggered
     * per-post media/topic/like/bookmark/repost/poll lookups (N+1); this path
     * resolves them with one IN query each and assembles from maps. Author
     * profiles and the viewer's poll votes are batched the same way (one query
     * for all authors, one for all polls on the page).
     */
    public List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        if (posts == null || posts.isEmpty()) return List.of();
        List<Long> ids = posts.stream().map(Post::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return posts.stream().map(p -> assemble(p, viewerId,
                List.of(), List.of(), false, false, false, null, Map.of(), null, null,
                Map.of(), Map.of())).toList();

        Map<Long, List<PostMedia>> mediaByPost = postMediaRepository.findByPost_IdInOrderByPost_IdAscPositionAsc(ids)
                .stream().collect(Collectors.groupingBy(pm -> pm.getPost().getId()));
        Map<Long, List<PostTopic>> topicsByPost = postTopicRepository.findByPost_IdIn(ids)
                .stream().collect(Collectors.groupingBy(pt -> pt.getPost().getId()));
        Set<Long> liked = viewerId == null ? Set.of()
                : new HashSet<>(postLikeRepository.findLikedPostIds(ids, viewerId));
        Set<Long> bookmarked = viewerId == null ? Set.of()
                : new HashSet<>(bookmarkRepository.findBookmarkedPostIds(ids, viewerId));
        Set<Long> reposted = viewerId == null ? Set.of()
                : new HashSet<>(repostRepository.findRepostedPostIds(ids, viewerId));
        Map<Long, Poll> pollByPost = pollRepository == null ? Map.of()
                : pollRepository.findByPost_IdIn(ids).stream()
                        .collect(Collectors.toMap(p -> p.getPost().getId(), p -> p, (a, b) -> a));

        Set<Long> authorIds = posts.stream()
                .map(Post::getUser).filter(Objects::nonNull)
                .map(User::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Profile> profileByUserId = authorIds.isEmpty() ? Map.of()
                : profileRepository.findWithUserByUserIdIn(authorIds).stream()
                        .collect(Collectors.toMap(Profile::getUserId, p -> p, (a, b) -> a));

        Set<Long> pollIds = pollByPost.values().stream()
                .map(Poll::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Set<Long>> selectedOptionsByPoll = Map.of();
        if (viewerId != null && pollVoteRepository != null && !pollIds.isEmpty()) {
            selectedOptionsByPoll = pollVoteRepository.findByPoll_IdInAndUser_Id(pollIds, viewerId).stream()
                    .collect(Collectors.groupingBy(v -> v.getPoll().getId(),
                            Collectors.mapping(v -> v.getOption().getId(), Collectors.toSet())));
        }
        Map<Long, List<PollOption>> optionsByPoll = pollIds.isEmpty() || pollOptionRepository == null ? Map.of()
                : pollOptionRepository.findByPoll_IdInOrderByPoll_IdAscPositionAsc(pollIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getPoll().getId()));

        // Batch community identity + the viewer's role per community (one IN query each)
        // so the Facebook-style community header and moderation capabilities stay N+1-free.
        Set<Long> communityIds = posts.stream()
                .map(Post::getCommunity).filter(Objects::nonNull)
                .map(Community::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Community> communityById = communityIds.isEmpty() ? Map.of()
                : communityRepository.findAllById(communityIds).stream()
                        .collect(Collectors.toMap(Community::getId, c -> c, (a, b) -> a));
        Map<Long, CommunityRole> viewerRoleByCommunityId = viewerId == null || communityMemberRepository == null
                ? Map.of()
                : communityMemberRepository.findByUserId(viewerId).stream()
                        .filter(cm -> cm.getCommunity() != null && cm.getCommunity().getId() != null)
                        .collect(Collectors.toMap(cm -> cm.getCommunity().getId(), CommunityMember::getRole, (a, b) -> a));

        List<PostResponse> responses = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<MediaResponse> media = mediaByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(PostMedia::getMedia).map(this::media).toList();
            List<TopicResponse> topics = topicsByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(PostTopic::getTopic).map(this::topic).toList();
            Poll poll = pollByPost.get(post.getId());
            responses.add(assemble(post, viewerId, media, topics,
                    liked.contains(post.getId()), bookmarked.contains(post.getId()),
                    reposted.contains(post.getId()), poll, profileByUserId,
                    poll == null ? null : selectedOptionsByPoll.getOrDefault(poll.getId(), Set.of()),
                    poll == null ? null : optionsByPoll.get(poll.getId()),
                    communityById, viewerRoleByCommunityId));
        }
        return responses;
    }

    private PostResponse assemble(Post post, Long viewerId, List<MediaResponse> media,
                                  List<TopicResponse> topics, boolean hasLiked,
                                  boolean hasBookmarked, boolean hasReposted, Poll poll,
                                  Map<Long, Profile> profileByUserId, Set<Long> selectedOptionIds,
                                  List<PollOption> preloadedOptions,
                                  Map<Long, Community> communityById,
                                  Map<Long, CommunityRole> viewerRoleByCommunityId) {
        boolean owner = viewerId != null && post.getUser() != null && Objects.equals(post.getUser().getId(), viewerId);
        UserSummary author = post.getUser() != null ? summary(post.getUser(), profileByUserId) : null;

        PostViewerStateResponse viewerState = new PostViewerStateResponse(hasLiked, hasBookmarked, hasReposted);

        Community community = post.getCommunity() != null && post.getCommunity().getId() != null
                ? communityById.get(post.getCommunity().getId())
                : null;
        if (community == null && post.getCommunity() != null && post.getCommunity().getId() == null) {
            community = post.getCommunity();
        }
        CommunitySummaryResponse communitySummary = community == null ? null
                : new CommunitySummaryResponse(community.getId(), community.getName(), community.getSlug(), community.getAvatarUrl());

        boolean viewerIsCommunityModerator = community != null && viewerId != null
                && isCommunityModerator(community, viewerId, viewerRoleByCommunityId);
        PostModerationStatus status = post.getModerationStatus();
        boolean postUnapproved = community != null && status != null && status != PostModerationStatus.APPROVED;
        boolean canApprove = viewerIsCommunityModerator && postUnapproved && post.getDeletedAt() == null;
        boolean canRemove = viewerIsCommunityModerator && !postUnapproved && post.getDeletedAt() == null;
        PostCapabilitiesResponse capabilities = new PostCapabilitiesResponse(owner, owner,
                post.isAllowReply(), post.isAllowQuote(), canApprove, canRemove);

        // Rejection reason is only surfaced to the submission's author and community moderators.
        String moderationReason = postUnapproved && post.getModerationReason() != null
                && (owner || viewerIsCommunityModerator)
                ? post.getModerationReason()
                : null;

        PollResponse pollResp = poll != null ? mapPoll(poll, viewerId, selectedOptionIds, preloadedOptions) : null;

        Long parentPostId = visiblePostId(post.getParentPost(), viewerId);
        Long quotedPostId = visiblePostId(post.getQuotedPost(), viewerId);

        return new PostResponse(post.getId(), author, post.getContent(), parentPostId,
                quotedPostId, id(post.getCommunity()), post.getVisibility(), post.isAllowReply(),
                post.isAllowQuote(), media, topics, pollResp, post.getLikeCount(), post.getReplyCount(),
                post.getRepostCount(), post.getQuoteCount(), post.getViewCount(), viewerState,
                capabilities, post.getCreatedAt(), post.getUpdatedAt(),
                communitySummary, status, moderationReason);
    }

    private Long visiblePostId(Post targetPost, Long viewerId) {
        if (targetPost == null || targetPost.getDeletedAt() != null) {
            return null;
        }
        if (!isPostVisibleTo(targetPost, viewerId)) {
            return null;
        }
        return targetPost.getId();
    }

    /**
     * Creates the single poll attached to a freshly created post, in the same transaction.
     * Service-level checks mirror {@link PollCreateInput} bean validation so callers that
     * bypass the @Valid controller path (internal service calls, tests) cannot persist a
     * malformed poll.
     */
    private void createPollForPost(Post post, PollCreateInput input) {
        String question = input.getQuestion() == null ? "" : input.getQuestion().trim();
        if (question.isEmpty() || question.length() > 255) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                    "Poll question must not be blank and must not exceed 255 characters");
        }
        if (input.getOptions() == null || input.getOptions().size() < 2 || input.getOptions().size() > 10) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                    "Poll must contain between 2 and 10 options");
        }
        if (input.getDurationDays() != null && (input.getDurationDays() < 1 || input.getDurationDays() > 365)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                    "Poll duration must be between 1 and 365 days");
        }

        Instant closedAt = input.getDurationDays() != null
                ? Instant.now().plus(Duration.ofDays(input.getDurationDays()))
                : null;

        Poll poll = Poll.builder()
                .post(post)
                .question(question)
                .allowMultiple(input.isAllowMultiple())
                .closedAt(closedAt)
                .build();
        poll = pollRepository.save(poll);

        int position = 0;
        for (String rawOption : input.getOptions()) {
            String optionText = rawOption == null ? "" : rawOption.trim();
            if (optionText.isEmpty() || optionText.length() > 200) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                        "Each poll option must not be blank and must not exceed 200 characters");
            }
            pollOptionRepository.save(PollOption.builder()
                    .poll(poll)
                    .optionText(optionText)
                    .position(position++)
                    .build());
        }
    }

    /**
     * Maps a poll with its options. The viewer's votes and the option list are
     * resolved with one query per poll when not preloaded ({@code preselectedOptionIds}
     * and {@code preloadedOptions} short-circuit even that in the feed path,
     * where votes and options for every poll on the page are loaded in one IN
     * query each); the previous per-option exists() probe was an option-count N+1.
     */
    private PollResponse mapPoll(Poll poll, Long viewerId, Set<Long> preselectedOptionIds,
                                 List<PollOption> preloadedOptions) {
        Set<Long> selectedOptionIds;
        if (preselectedOptionIds != null) {
            selectedOptionIds = preselectedOptionIds;
        } else if (viewerId != null && pollVoteRepository != null) {
            selectedOptionIds = pollVoteRepository.findByPollIdAndUserId(poll.getId(), viewerId).stream()
                    .map(vote -> vote.getOption().getId())
                    .collect(Collectors.toSet());
        } else {
            selectedOptionIds = Set.of();
        }

        List<PollOption> pollOptions = preloadedOptions != null
                ? preloadedOptions
                : pollOptionRepository != null
                        ? pollOptionRepository.findByPollIdOrderByPositionAsc(poll.getId())
                        : List.of();
        List<PollOptionResponse> options = pollOptions.stream()
                .map(opt -> new PollOptionResponse(opt.getId(), opt.getOptionText(), opt.getVoteCount(),
                        viewerId == null ? null : selectedOptionIds.contains(opt.getId())))
                .toList();

        int totalVotes = options.stream().mapToInt(PollOptionResponse::getVoteCount).sum();
        Long votedOptionId = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getSelectedByCurrentUser()))
                .map(PollOptionResponse::getId)
                .findFirst()
                .orElse(null);

        return new PollResponse(poll.getId(), poll.getQuestion(), poll.isAllowMultiple(),
                poll.getClosedAt(), poll.getClosedAt(), totalVotes, votedOptionId, options);
    }

    private UserSummary summary(User user, Map<Long, Profile> profileByUserId) {
        Profile profile = profileByUserId.get(user.getId());
        if (profile == null) {
            profile = profileRepository.findById(user.getId())
                    .orElseThrow(() -> notFound("USER_NOT_FOUND", "User profile not found"));
        }
        return new UserSummary(user.getId(), user.getUsername(), profile.getDisplayName(), profile.getAvatarUrl(), user.isVerified());
    }

    private MediaResponse media(MediaAsset media) {
        return new MediaResponse(media.getId(), media.getType(), media.getUrl(), media.getWidth(),
                media.getHeight(), media.getDurationSeconds());
    }

    private TopicResponse topic(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName(), topic.getSlug());
    }

    private Long id(Post post) { return post == null ? null : post.getId(); }
    private Long id(Community community) { return community == null ? null : community.getId(); }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    /**
     * Community moderators = the community owner plus members holding the ADMIN role.
     * They may publish immediately, see and decide PENDING/REJECTED submissions,
     * and remove published community posts.
     */
    private boolean isCommunityModerator(Community community, Long userId) {
        if (community == null || userId == null) {
            return false;
        }
        if (community.getOwner() != null && Objects.equals(community.getOwner().getId(), userId)) {
            return true;
        }
        return communityMemberRepository != null
                && communityMemberRepository.findByCommunityIdAndUserId(community.getId(), userId)
                        .map(m -> m.getRole() == CommunityRole.ADMIN)
                        .orElse(false);
    }

    /** Preloaded-role variant of {@link #isCommunityModerator} used in batch mapping. */
    private boolean isCommunityModerator(Community community, Long userId, Map<Long, CommunityRole> viewerRoleByCommunityId) {
        if (community == null || userId == null) {
            return false;
        }
        if (community.getOwner() != null && Objects.equals(community.getOwner().getId(), userId)) {
            return true;
        }
        return viewerRoleByCommunityId.get(community.getId()) == CommunityRole.ADMIN;
    }

    /**
     * Documented content-edit policy for community posts:
     * <ul>
     *   <li>Approval required and the author is an ordinary member: any content edit
     *       (including of an already APPROVED post — bait-and-switch prevention) re-enters
     *       the PENDING queue; a REJECTED submission resubmits as PENDING.</li>
     *   <li>Approval not required for this author (owner/admin author, or approval
     *       disabled): a PENDING/REJECTED post becomes APPROVED on edit; legacy null
     *       status is normalised to APPROVED without changing counters.</li>
     * </ul>
     * Non-content edits (visibility/allowReply/allowQuote) never change the status.
     */
    private void applyCommunityEditModerationPolicy(Post post, Long authorId) {
        Community community = post.getCommunity();
        boolean approvalRequired = community.isRequirePostApproval() && !isCommunityModerator(community, authorId);
        PostModerationStatus current = post.getModerationStatus();
        if (approvalRequired) {
            post.setModerationStatus(PostModerationStatus.PENDING);
            post.setModerationReason(null);
        } else if (current == PostModerationStatus.PENDING || current == PostModerationStatus.REJECTED) {
            post.setModerationStatus(PostModerationStatus.APPROVED);
            post.setModerationReason(null);
        } else if (current == null) {
            post.setModerationStatus(PostModerationStatus.APPROVED);
        }
    }
}
