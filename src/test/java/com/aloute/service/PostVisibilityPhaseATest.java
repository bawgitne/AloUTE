package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.service.impl.PostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase A Regression Tests for Canonical Post Visibility & Privacy Invariants.
 * Covers: guest, normal user, owner, followers, mentions fail-closed, private accounts,
 * mutual blocks, soft-deleted posts, private communities (including owner who left),
 * and nested reference nullification.
 * Owned by Ngọc (Content/Post/Repository) and Khôi (Security/Authorization).
 */
class PostVisibilityPhaseATest {

    private PostRepository postRepository;
    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private PostMediaRepository postMediaRepository;
    private PostTopicRepository postTopicRepository;
    private TopicRepository topicRepository;
    private CommunityMemberRepository communityMemberRepository;
    private CommunityRepository communityRepository;
    private PostLikeRepository postLikeRepository;
    private BookmarkRepository bookmarkRepository;
    private RepostRepository repostRepository;
    private MediaAssetRepository mediaAssetRepository;
    private BlockRepository blockRepository;
    private FollowRepository followRepository;
    private ApplicationEventPublisher eventPublisher;

    private PostServiceImpl postService;

    private User author;
    private User viewer;
    private Profile authorProfile;
    private Post publicPost;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        postMediaRepository = mock(PostMediaRepository.class);
        postTopicRepository = mock(PostTopicRepository.class);
        topicRepository = mock(TopicRepository.class);
        communityMemberRepository = mock(CommunityMemberRepository.class);
        communityRepository = mock(CommunityRepository.class);
        postLikeRepository = mock(PostLikeRepository.class);
        bookmarkRepository = mock(BookmarkRepository.class);
        repostRepository = mock(RepostRepository.class);
        mediaAssetRepository = mock(MediaAssetRepository.class);
        blockRepository = mock(BlockRepository.class);
        followRepository = mock(FollowRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        postService = new PostServiceImpl(
                postRepository,
                userRepository,
                profileRepository,
                postMediaRepository,
                postTopicRepository,
                topicRepository,
                communityMemberRepository,
                communityRepository,
                postLikeRepository,
                bookmarkRepository,
                repostRepository,
                mediaAssetRepository,
                blockRepository,
                followRepository,
                eventPublisher
        );

        author = new User();
        author.setId(10L);
        author.setUsername("author_user");
        author.setActive(true);
        author.setPrivateAccount(false);

        viewer = new User();
        viewer.setId(20L);
        viewer.setUsername("viewer_user");
        viewer.setActive(true);

        authorProfile = new Profile();
        authorProfile.setUser(author);
        authorProfile.setDisplayName("Author Display");
        authorProfile.setAvatarUrl("https://example.com/avatar.png");

        publicPost = new Post();
        publicPost.setId(100L);
        publicPost.setUser(author);
        publicPost.setContent("Hello world public post");
        publicPost.setVisibility(PostVisibility.PUBLIC);
        publicPost.setAllowReply(true);
        publicPost.setAllowQuote(true);

        when(userRepository.findById(10L)).thenReturn(Optional.of(author));
        when(userRepository.findById(20L)).thenReturn(Optional.of(viewer));
        when(profileRepository.findById(10L)).thenReturn(Optional.of(authorProfile));
        when(postMediaRepository.findByPost_IdOrderByPositionAsc(any())).thenReturn(Collections.emptyList());
        when(postTopicRepository.findByPost_Id(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Guest viewer can see PUBLIC post from public account")
    void guestCanSeePublicPostFromPublicAccount() {
        assertThat(postService.isPostVisibleTo(publicPost, null)).isTrue();
    }

    @Test
    @DisplayName("Guest viewer cannot see PUBLIC post if author has private account")
    void guestCannotSeePublicPostFromPrivateAccount() {
        author.setPrivateAccount(true);
        assertThat(postService.isPostVisibleTo(publicPost, null)).isFalse();
    }

    @Test
    @DisplayName("Guest viewer cannot see FOLLOWERS or MENTIONED posts")
    void guestCannotSeeRestrictedPosts() {
        publicPost.setVisibility(PostVisibility.FOLLOWERS);
        assertThat(postService.isPostVisibleTo(publicPost, null)).isFalse();

        publicPost.setVisibility(PostVisibility.MENTIONED);
        assertThat(postService.isPostVisibleTo(publicPost, null)).isFalse();
    }

    @Test
    @DisplayName("Post author (owner) can always see their own post even if private/followers/mentioned")
    void ownerCanAlwaysSeeOwnPost() {
        publicPost.setVisibility(PostVisibility.FOLLOWERS);
        assertThat(postService.isPostVisibleTo(publicPost, 10L)).isTrue();

        publicPost.setVisibility(PostVisibility.MENTIONED);
        assertThat(postService.isPostVisibleTo(publicPost, 10L)).isTrue();
    }

    @Test
    @DisplayName("Soft-deleted post is hidden from all viewers including the author")
    void softDeletedPostIsHiddenFromEveryone() {
        publicPost.setDeletedAt(Instant.now());
        assertThat(postService.isPostVisibleTo(publicPost, 10L)).isFalse();
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();
        assertThat(postService.isPostVisibleTo(publicPost, null)).isFalse();
    }

    @Test
    @DisplayName("Mutual block hides posts: author blocked viewer OR viewer blocked author")
    void mutualBlockHidesPosts() {
        // Viewer blocked author
        when(blockRepository.existsByBlockerIdAndBlockedId(20L, 10L)).thenReturn(true);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();

        // Reset and test author blocked viewer
        when(blockRepository.existsByBlockerIdAndBlockedId(20L, 10L)).thenReturn(false);
        when(blockRepository.existsByBlockerIdAndBlockedId(10L, 20L)).thenReturn(true);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();
    }

    @Test
    @DisplayName("FOLLOWERS visibility: only followed users can see")
    void followersVisibilityEnforcement() {
        publicPost.setVisibility(PostVisibility.FOLLOWERS);

        // Not following
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(false);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();

        // Following
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(true);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isTrue();
    }

    @Test
    @DisplayName("Private account author hides posts from non-followers even if post is PUBLIC")
    void privateAccountHidesPostsFromNonFollowers() {
        author.setPrivateAccount(true);
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(false);

        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();

        // Following author
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(true);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isTrue();
    }

    @Test
    @DisplayName("MENTIONED visibility fails closed for non-author under canonical policy")
    void mentionedVisibilityFailsClosedForNonAuthor() {
        publicPost.setVisibility(PostVisibility.MENTIONED);
        publicPost.setContent("Hey @viewer_user check this out");

        // Non-owner cannot view MENTIONED posts because no canonical Mention entity exists
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();

        // Author can view their own MENTIONED post
        assertThat(postService.isPostVisibleTo(publicPost, 10L)).isTrue();
    }

    @Test
    @DisplayName("Private community post: only community members can view")
    void privateCommunityVisibilityEnforcement() {
        Community privateComm = new Community();
        privateComm.setId(500L);
        privateComm.setPrivateCommunity(true);
        publicPost.setCommunity(privateComm);

        // Guest cannot view
        assertThat(postService.isPostVisibleTo(publicPost, null)).isFalse();

        // Non-member cannot view
        when(communityMemberRepository.existsByCommunityIdAndUserId(500L, 20L)).thenReturn(false);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isFalse();

        // Member can view
        when(communityMemberRepository.existsByCommunityIdAndUserId(500L, 20L)).thenReturn(true);
        assertThat(postService.isPostVisibleTo(publicPost, 20L)).isTrue();
    }

    @Test
    @DisplayName("Private community invariant: author who left private community cannot view post (Finding 4)")
    void authorWhoLeftPrivateCommunityCannotViewPost() {
        Community privateComm = new Community();
        privateComm.setId(500L);
        privateComm.setPrivateCommunity(true);
        publicPost.setCommunity(privateComm);

        // Author (10L) is no longer a member of the private community
        when(communityMemberRepository.existsByCommunityIdAndUserId(500L, 10L)).thenReturn(false);

        assertThat(postService.isPostVisibleTo(publicPost, 10L)).isFalse();
    }

    @Test
    @DisplayName("communityPosts rejects non-members when community is private with COMMUNITY_ACCESS_DENIED")
    void communityPostsRejectsNonMembersWhenPrivate() {
        Community privateComm = new Community();
        privateComm.setId(500L);
        privateComm.setPrivateCommunity(true);

        when(communityRepository.findByIdAndDeletedAtIsNull(500L)).thenReturn(Optional.of(privateComm));
        when(communityMemberRepository.existsByCommunityIdAndUserId(500L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> postService.communityPosts(500L, 0, 20, 20L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_ACCESS_DENIED));
    }

    @Test
    @DisplayName("get(postId) throws POST_ACCESS_DENIED when post is not visible to viewer")
    void getThrowsPostAccessDeniedWhenRestricted() {
        publicPost.setVisibility(PostVisibility.FOLLOWERS);
        when(postRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(publicPost));
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(false);

        assertThatThrownBy(() -> postService.get(100L, 20L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("toResponse nullifies parentPostId and quotedPostId when referenced posts are restricted or deleted")
    void toResponseNullifiesRestrictedParentAndQuotedReferences() {
        Post parentPost = new Post();
        parentPost.setId(901L);
        parentPost.setUser(author);
        parentPost.setVisibility(PostVisibility.FOLLOWERS); // Restricted to non-followers

        Post quotedPost = new Post();
        quotedPost.setId(902L);
        quotedPost.setUser(author);
        quotedPost.setDeletedAt(Instant.now()); // Soft-deleted

        publicPost.setParentPost(parentPost);
        publicPost.setQuotedPost(quotedPost);

        // Viewer does not follow author
        when(followRepository.existsById(new FollowId(20L, 10L))).thenReturn(false);

        PostResponse response = postService.toResponse(publicPost, 20L);

        assertThat(response.id()).isEqualTo(100L);
        // Parent and Quoted must be nullified to prevent leaking IDs of restricted/deleted posts
        assertThat(response.parentPostId()).isNull();
        assertThat(response.quotedPostId()).isNull();
    }

    @Test
    @DisplayName("bookmarkedPosts queries bookmarkRepository via Specification ensuring exact count and pagination (Finding 5)")
    void bookmarkedPostsUsesSpecificationQuery() {
        Bookmark bookmark = new Bookmark(viewer, publicPost);
        bookmark.setId(1L);

        when(bookmarkRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(bookmark), PageRequest.of(0, 10), 1));

        ApiResponse<List<PostResponse>> response = postService.bookmarkedPosts(20L, 0, 10);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(1);
        PageMeta meta = (PageMeta) response.meta();
        assertThat(meta.totalElements()).isEqualTo(1);
        verify(bookmarkRepository).findAll(any(Specification.class), any(PageRequest.class));
    }
}
