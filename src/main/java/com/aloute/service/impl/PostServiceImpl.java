package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Autowired(required = false)
    private PollRepository pollRepository;

    @Autowired(required = false)
    private PollOptionRepository pollOptionRepository;

    @Autowired(required = false)
    private PollVoteRepository pollVoteRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private FeedRecommendationService feedRecommendationService;

    @Autowired(required = false)
    private FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> listPublic(int page, int size, Long viewerId) {
        Page<Post> postPage = postRepository.findByParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> getRecommendedFeed(Long viewerId, int page, int size) {
        Page<Post> postPage = postRepository.findByParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<Post> posts = postPage.getContent();
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
        if (parentPostId != null) {
            parentPost = requireVisiblePost(parentPostId, userId);
            if (!parentPost.isAllowReply()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REPLY_NOT_ALLOWED", "Reply is disabled for this post");
            }
        }

        Post quotedPost = null;
        if (quotedPostId != null) {
            quotedPost = requireVisiblePost(quotedPostId, userId);
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

        // Update counters
        if (parentPost != null) {
            parentPost.setReplyCount(parentPost.getReplyCount() + 1);
            postRepository.save(parentPost);
            if (notificationService != null && parentPost.getUser() != null) {
                try {
                    notificationService.createNotification(parentPost.getUser().getId(), userId, "POST_REPLY", "POST", savedPost.getId(), request.content());
                } catch (Exception ignored) {}
            }
        }

        if (quotedPost != null) {
            quotedPost.setQuoteCount(quotedPost.getQuoteCount() + 1);
            postRepository.save(quotedPost);
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
        Post post = requireVisiblePost(postId, userId);
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
        Post post = requireVisiblePost(postId, userId);
        if (!Objects.equals(post.getUser().getId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only post owner can delete the post");
        }
        post.setDeletedAt(Instant.now());
        postRepository.save(post);

        if (post.getParentPost() != null) {
            Post parent = post.getParentPost();
            parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
            postRepository.save(parent);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> replies(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId, viewerId);
        Page<Post> repliesPage = postRepository.findByParentPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                postId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = repliesPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(repliesPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> quotes(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId, viewerId);
        Page<Post> quotesPage = postRepository.findByQuotedPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                postId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = quotesPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(quotesPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> communityPosts(Long communityId, int page, int size, Long viewerId) {
        Page<Post> postPage = postRepository.findByCommunityId(
                communityId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> topicPosts(Long topicId, int page, int size, Long viewerId) {
        topicRepository.findById(topicId).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Topic not found"));
        Page<Post> postPage = postRepository.findByTopicId(
                topicId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> userPosts(Long userId, int page, int size, Long viewerId) {
        Page<Post> postPage = postRepository.findByUserIdAndParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> searchPosts(String query, int page, int size, Long viewerId) {
        Page<Post> postPage = postRepository.searchPosts(
                query, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = postPage.getContent().stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(postPage));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PostResponse>> bookmarkedPosts(Long userId, int page, int size) {
        Page<Bookmark> bookmarkPage = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<PostResponse> list = bookmarkPage.getContent().stream()
                .map(b -> toResponse(b.getPost(), userId))
                .filter(Objects::nonNull)
                .toList();
        return ApiResponse.page(list, PageMeta.from(bookmarkPage));
    }

    @Override
    @Transactional
    public PostResponse like(Long userId, Long postId) {
        Post post = requireVisiblePost(postId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.save(new PostLike(user, post));
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);

            if (notificationService != null && post.getUser() != null) {
                try {
                    notificationService.createNotification(post.getUser().getId(), userId, "POST_LIKE", "POST", postId, post.getContent());
                } catch (Exception ignored) {}
            }
        }
        return get(postId, userId);
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        Post post = requireVisiblePost(postId, userId);
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
        Post post = requireVisiblePost(postId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found"));

        if (!repostRepository.existsByPostIdAndUserId(postId, userId)) {
            repostRepository.save(new Repost(user, post));
            post.setRepostCount(post.getRepostCount() + 1);
            postRepository.save(post);
        }
        return get(postId, userId);
    }

    @Override
    @Transactional
    public void removeRepost(Long userId, Long postId) {
        Post post = requireVisiblePost(postId, userId);
        if (repostRepository.existsByPostIdAndUserId(postId, userId)) {
            repostRepository.deleteByPostIdAndUserId(postId, userId);
            post.setRepostCount(Math.max(0, post.getRepostCount() - 1));
            postRepository.save(post);
        }
    }

    private Post requireVisiblePost(Long postId, Long viewerId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "Post not found"));
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

        return new PostResponse(post.getId(), author, post.getContent(), id(post.getParentPost()),
                id(post.getQuotedPost()), id(post.getCommunity()), post.getVisibility(), post.isAllowReply(),
                post.isAllowQuote(), media, topics, pollResp, post.getLikeCount(), post.getReplyCount(),
                post.getRepostCount(), post.getQuoteCount(), post.getViewCount(), viewerState,
                capabilities, post.getCreatedAt(), post.getUpdatedAt());
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
