package com.aloute.service.impl;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.UpdatePostRequest;
import com.aloute.dto.response.*;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.service.FeedRecommendationService;
import com.aloute.service.NotificationService;
import com.aloute.service.PostService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PostServiceImpl implements PostService {
    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostTopicRepository postTopicRepository;
    private final TopicRepository topicRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final EntityManager entityManager;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private FeedRecommendationService feedRecommendationService;

    @Autowired(required = false)
    private FollowRepository followRepository;

    public PostServiceImpl(PostRepository postRepository, ProfileRepository profileRepository,
                           PostMediaRepository postMediaRepository, PostTopicRepository postTopicRepository,
                           TopicRepository topicRepository, CommunityMemberRepository communityMemberRepository,
                           EntityManager entityManager) {
        this.postRepository = postRepository;
        this.profileRepository = profileRepository;
        this.postMediaRepository = postMediaRepository;
        this.postTopicRepository = postTopicRepository;
        this.topicRepository = topicRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> listPublic(int page, int size, Long viewerId) {
        List<Post> posts = postRepository.listPublicPostsFromSp(offset(page, size), size);
        return ApiResponse.page(posts.stream().map(post -> toResponse(post, viewerId)).toList(),
                meta(page, size, postRepository.countPublicPostsFromSp()));
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> getRecommendedFeed(Long viewerId, int page, int size) {
        List<Post> posts = postRepository.listPublicPostsFromSp(offset(page, size), size);
        if (viewerId == null || feedRecommendationService == null) {
            return listPublic(page, size, viewerId);
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
        }).collect(java.util.stream.Collectors.toList());

        List<FeedRecommendationService.PostCandidate> ranked = feedRecommendationService.rankCandidates(candidates, java.time.Instant.now());
        java.util.Map<Long, Post> postMap = posts.stream().collect(java.util.stream.Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
        List<PostResponse> responses = ranked.stream()
                .map(c -> postMap.get(c.getPostId()))
                .filter(Objects::nonNull)
                .map(p -> toResponse(p, viewerId))
                .toList();

        return ApiResponse.page(responses, meta(page, size, postRepository.countPublicPostsFromSp()));
    }

    @Override @Transactional(readOnly = true)
    public PostResponse get(Long postId, Long viewerId) {
        return toResponse(requireVisiblePost(postId, viewerId), viewerId);
    }

    @Override @Transactional
    public PostResponse create(Long userId, CreatePostRequest request, Long communityId, Long parentPostId, Long quotedPostId) {
        if (parentPostId != null && !requireVisiblePost(parentPostId, userId).isAllowReply()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
        }
        if (quotedPostId != null && !requireVisiblePost(quotedPostId, userId).isAllowQuote()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "QUOTE_NOT_ALLOWED", "Quote is disabled for this post");
        }
        if (communityId != null && communityMemberRepository.getMembershipFromSp(communityId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMMUNITY_MEMBERSHIP_REQUIRED", "Only community members can post there");
        }
        Long id = postRepository.createPostFromSp(userId, request.content(), request.visibility().name(),
                flag(request.allowReply()), flag(request.allowQuote()), parentPostId, quotedPostId, communityId,
                csv(request.mediaIds()), csv(request.topicIds()));
        flushAndClear();
        if (notificationService != null && parentPostId != null) {
            try {
                Post parent = requireVisiblePost(parentPostId, userId);
                if (parent != null && parent.getUser() != null) {
                    notificationService.createNotification(parent.getUser().getId(), userId, "POST_REPLY", "POST", id, request.content());
                }
            } catch (Exception ignored) {}
        }
        return get(id, userId);
    }

    @Override @Transactional
    public PostResponse update(Long userId, Long postId, UpdatePostRequest request) {
        Long id = postRepository.updatePostFromSp(postId, userId, request.content(),
                postRepository.visibilityValue(request.visibility()), request.allowReply(), request.allowQuote());
        flushAndClear();
        return get(id, userId);
    }

    @Override @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.softDeletePostFromSp(postId, userId);
        flushAndClear();
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> replies(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId, viewerId);
        List<Post> posts = postRepository.listRepliesFromSp(postId, offset(page, size), size);
        return ApiResponse.page(posts.stream().map(post -> toResponse(post, viewerId)).toList(),
                meta(page, size, postRepository.countRepliesFromSp(postId)));
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> communityPosts(Long communityId, int page, int size, Long viewerId) {
        List<Post> posts = postRepository.listCommunityPostsFromSp(communityId, offset(page, size), size);
        return ApiResponse.page(posts.stream().map(post -> toResponse(post, viewerId)).toList(),
                meta(page, size, postRepository.countCommunityPostsFromSp(communityId)));
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> topicPosts(Long topicId, int page, int size, Long viewerId) {
        topicRepository.getTopicFromSp(topicId).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Topic not found"));
        List<Post> posts = postRepository.listTopicPostsFromSp(topicId, offset(page, size), size);
        return ApiResponse.page(posts.stream().map(post -> toResponse(post, viewerId)).toList(),
                meta(page, size, postRepository.countTopicPostsFromSp(topicId)));
    }

    @Override @Transactional
    public PostResponse like(Long userId, Long postId) {
        postRepository.likePostFromSp(postId, userId);
        flushAndClear();
        if (notificationService != null) {
            try {
                Post post = requireVisiblePost(postId, userId);
                if (post != null && post.getUser() != null) {
                    notificationService.createNotification(post.getUser().getId(), userId, "POST_LIKE", "POST", postId, post.getContent());
                }
            } catch (Exception ignored) {}
        }
        return get(postId, userId);
    }

    @Override @Transactional
    public void unlike(Long userId, Long postId) {
        postRepository.unlikePostFromSp(postId, userId);
        flushAndClear();
    }

    @Override @Transactional
    public PostResponse bookmark(Long userId, Long postId) {
        postRepository.bookmarkPostFromSp(postId, userId);
        flushAndClear();
        return get(postId, userId);
    }

    @Override @Transactional
    public void removeBookmark(Long userId, Long postId) {
        postRepository.removeBookmarkFromSp(postId, userId);
        flushAndClear();
    }

    @Override @Transactional
    public PostResponse repost(Long userId, Long postId) {
        postRepository.repostFromSp(postId, userId);
        flushAndClear();
        return get(postId, userId);
    }

    @Override @Transactional
    public void removeRepost(Long userId, Long postId) {
        postRepository.removeRepostFromSp(postId, userId);
        flushAndClear();
    }

    private Post requireVisiblePost(Long postId, Long viewerId) {
        return postRepository.getVisiblePostFromSp(postId, viewerId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
    }

    private PostResponse toResponse(Post post, Long viewerId) {
        boolean owner = viewerId != null && Objects.equals(post.getUser().getId(), viewerId);
        UserSummary author = summary(post.getUser());
        List<MediaResponse> media = postMediaRepository.findByPost_IdOrderByPositionAsc(post.getId()).stream()
                .map(PostMedia::getMedia).map(this::media).toList();
        List<TopicResponse> topics = postTopicRepository.findByPost_Id(post.getId()).stream()
                .map(PostTopic::getTopic).map(this::topic).toList();
        PostViewerStateResponse viewerState = viewerId == null
                ? new PostViewerStateResponse(false, false, false)
                : new PostViewerStateResponse(postRepository.hasLikedFromSp(post.getId(), viewerId),
                        postRepository.hasBookmarkedFromSp(post.getId(), viewerId),
                        postRepository.hasRepostedFromSp(post.getId(), viewerId));
        PostCapabilitiesResponse capabilities = new PostCapabilitiesResponse(owner, owner,
                post.isAllowReply(), post.isAllowQuote());
        return new PostResponse(post.getId(), author, post.getContent(), id(post.getParentPost()),
                id(post.getQuotedPost()), id(post.getCommunity()), post.getVisibility(), post.isAllowReply(),
                post.isAllowQuote(), media, topics, null, post.getLikeCount(), post.getReplyCount(),
                post.getRepostCount(), post.getQuoteCount(), post.getViewCount(), viewerState,
                capabilities, post.getCreatedAt(), post.getUpdatedAt());
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
    private boolean flag(Boolean value) { return value == null || value; }
    private int offset(int page, int size) { return page * size; }
    private String csv(List<Long> ids) { return ids == null || ids.isEmpty() ? null : String.join(",", ids.stream().map(String::valueOf).toList()); }
    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    private PageMeta meta(int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageMeta(page, size, total, totalPages, page + 1 < totalPages, page > 0);
    }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
