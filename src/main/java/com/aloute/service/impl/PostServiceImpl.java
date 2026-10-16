package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.CreatePostRequest;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> getRecommendedFeed(Long viewerId, int page, int size) {
        Specification<Post> spec = (root, query, cb) -> cb.isNull(root.get("parentPost"));
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<Post> posts = postPage.getContent();
        if (viewerId == null || feedRecommendationService == null) {
            List<PostResponse> list = posts.stream()
                    .map(post -> toResponse(post, viewerId))
                    .toList();
            return ApiResponse.page(list, PageMeta.from(postPage));
        }

        List<FeedRecommendationService.PostCandidate> candidates = posts.stream().map(p -> {
            boolean isFollowing = false;
            if (followRepository != null && p.getUser() != null) {
                isFollowing = followRepository.existsById(new FollowId(viewerId, p.getUser().getId()));
            }
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
        List<PostResponse> responses = ranked.stream()
                .map(c -> postMap.get(c.getPostId()))
                .filter(Objects::nonNull)
                .map(p -> toResponse(p, viewerId))
                .toList();

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

        Post savedPost = postRepository.save(post);

        // Save media attachments
        if (request.mediaIds() != null && !request.mediaIds().isEmpty()) {
            int pos = 0;
            for (Long mediaId : request.mediaIds()) {
                MediaAsset media = mediaAssetRepository.findById(mediaId).orElse(null);
                if (media != null) {
                    postMediaRepository.save(new PostMedia(savedPost, media, pos++));
                }
            }
        }

        // Save topic tags
        if (request.topicIds() != null && !request.topicIds().isEmpty()) {
            for (Long topicId : request.topicIds()) {
                Topic topic = topicRepository.findById(topicId).orElse(null);
                if (topic != null) {
                    postTopicRepository.save(new PostTopic(savedPost, topic));
                }
            }
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

        if (community != null) {
            community.setPostCount(community.getPostCount() + 1);
            communityRepository.save(community);
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
        if (!Objects.equals(post.getUser().getId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only post owner can edit the post");
        }

        if (request.content() != null) post.setContent(request.content());
        if (request.visibility() != null) post.setVisibility(request.visibility());
        if (request.allowReply() != null) post.setAllowReply(request.allowReply());
        if (request.allowQuote() != null) post.setAllowQuote(request.allowQuote());

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
        List<PostResponse> list = repliesPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(repliesPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> quotes(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId, viewerId);
        Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("quotedPost").get("id"), postId);
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> quotesPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = quotesPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(quotesPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> communityPosts(Long communityId, int page, int size, Long viewerId) {
        Community community = communityRepository.findByIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> notFound("COMMUNITY_NOT_FOUND", "Community not found"));
        if (community.isPrivateCommunity()) {
            if (viewerId == null || !communityMemberRepository.existsByCommunityIdAndUserId(communityId, viewerId)) {
                throw notFound("COMMUNITY_NOT_FOUND", "Community not found");
            }
        }
        Specification<Post> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("community").get("id"), communityId),
                cb.isNull(root.get("parentPost"))
        );
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
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
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
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
        spec = spec.and(buildVisibilitySpec(viewerId));
        Page<Post> postPage = postRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
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
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
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

        List<PostResponse> list = bookmarkPage.getContent().stream()
                .map(b -> toResponse(b.getPost(), userId))
                .toList();

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

        // 1. Canonical private-community invariant: non-members cannot access private community posts
        // This check MUST occur before author exception (e.g. author who left the private community)
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (viewerId == null) {
                return false;
            }
            if (communityMemberRepository == null
                    || !communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), viewerId)) {
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
        }

        return PostSpecifications.visibleTo(viewerId, blockedUserIds, followingUserIds, userCommunityIds);
    }

    @Override
    public PostResponse toResponse(Post post, Long viewerId) {
        if (post == null) return null;
        boolean owner = viewerId != null && post.getUser() != null && Objects.equals(post.getUser().getId(), viewerId);
        UserSummary author = post.getUser() != null ? summary(post.getUser()) : null;
        List<MediaResponse> media = postMediaRepository.findByPost_IdOrderByPositionAsc(post.getId()).stream()
                .map(PostMedia::getMedia).map(this::media).toList();
        List<TopicResponse> topics = postTopicRepository.findByPost_Id(post.getId()).stream()
                .map(PostTopic::getTopic).map(this::topic).toList();

        boolean hasLiked = viewerId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), viewerId);
        boolean hasBookmarked = viewerId != null && bookmarkRepository.existsByPostIdAndUserId(post.getId(), viewerId);
        boolean hasReposted = viewerId != null && repostRepository.existsByPostIdAndUserId(post.getId(), viewerId);

        PostViewerStateResponse viewerState = new PostViewerStateResponse(hasLiked, hasBookmarked, hasReposted);
        PostCapabilitiesResponse capabilities = new PostCapabilitiesResponse(owner, owner,
                post.isAllowReply(), post.isAllowQuote());

        PollResponse pollResp = null;
        if (pollRepository != null) {
            pollResp = pollRepository.findByPostId(post.getId())
                    .map(p -> mapPoll(p, viewerId))
                    .orElse(null);
        }

        Long parentPostId = visiblePostId(post.getParentPost(), viewerId);
        Long quotedPostId = visiblePostId(post.getQuotedPost(), viewerId);

        return new PostResponse(post.getId(), author, post.getContent(), parentPostId,
                quotedPostId, id(post.getCommunity()), post.getVisibility(), post.isAllowReply(),
                post.isAllowQuote(), media, topics, pollResp, post.getLikeCount(), post.getReplyCount(),
                post.getRepostCount(), post.getQuoteCount(), post.getViewCount(), viewerState,
                capabilities, post.getCreatedAt(), post.getUpdatedAt());
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

    private PollResponse mapPoll(Poll poll, Long viewerId) {
        List<PollOptionResponse> options = pollOptionRepository != null
                ? pollOptionRepository.findByPollIdOrderByPositionAsc(poll.getId()).stream()
                .map(opt -> {
                    Boolean selected = viewerId != null && pollVoteRepository != null
                            ? pollVoteRepository.existsByPollIdAndOptionIdAndUserId(poll.getId(), opt.getId(), viewerId)
                            : null;
                    return new PollOptionResponse(opt.getId(), opt.getOptionText(), opt.getVoteCount(), selected);
                }).toList()
                : List.of();

        int totalVotes = options.stream().mapToInt(PollOptionResponse::getVoteCount).sum();
        Long votedOptionId = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getSelectedByCurrentUser()))
                .map(PollOptionResponse::getId)
                .findFirst()
                .orElse(null);

        return new PollResponse(poll.getId(), poll.getQuestion(), poll.isAllowMultiple(),
                poll.getClosedAt(), poll.getClosedAt(), totalVotes, votedOptionId, options);
    }

    private UserSummary summary(User user) {
        Profile profile = profileRepository.findById(user.getId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User profile not found"));
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
}
