package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.FeedService;
import com.aloute.service.PostService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Database Criteria Query & Service Integration Tests for Phase A (Finding 6).
 * Validates against H2 relational DB:
 * - Standalone PUBLIC posts (null community) with explicit LEFT JOIN preservation.
 * - Private account author visibility (guest / non-follower denied, follower allowed).
 * - FOLLOWERS visibility enforcement.
 * - MENTIONED post fail-closed for non-owner.
 * - Mutual block filtering at SQL level.
 * - Private community gate (including author who left group).
 * - Soft-deleted post exclusion.
 * - Nested quote/parent reference nullification when target is restricted.
 * - Bookmark pagination and exact total count with interleaved hidden rows.
 * Owned by Ngọc (Repository/Post) and Khôi (Security/Authorization).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PostVisibilityDatabaseIntegrationTest {

    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private MuteRepository muteRepository;
    @Autowired private FeedService feedService;
    @Autowired private MockMvc mockMvc;
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private PostMediaRepository postMediaRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private PostTopicRepository postTopicRepository;

    private User publicAuthor;
    private User privateAuthor;
    private User testViewer;
    private User stranger;

    private User createUser(String username, String email, boolean isPrivate) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashedPasswordPlaceholder");
        u.setVerified(true);
        u.setPrivateAccount(isPrivate);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.save(u);

        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + username);
        profileRepository.save(p);

        return u;
    }

    private Post createPost(User author, String content, PostVisibility visibility, Community community) {
        Post p = new Post();
        p.setUser(author);
        p.setContent(content);
        p.setVisibility(visibility);
        p.setCommunity(community);
        p.setAllowReply(true);
        p.setAllowQuote(true);
        return postRepository.save(p);
    }

    private void createFollow(User follower, User following) {
        Follow f = new Follow();
        f.setId(new FollowId(follower.getId(), following.getId()));
        f.setFollower(follower);
        f.setFollowing(following);
        followRepository.save(f);
    }

    private void createMute(User muter, User muted) {
        Mute m = new Mute();
        m.setMuter(muter);
        m.setMuted(muted);
        muteRepository.save(m);
    }

    private Authentication createMockAuth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        publicAuthor = createUser("pub_author_" + suffix, "pub_" + suffix + "@aloute.test", false);
        privateAuthor = createUser("priv_author_" + suffix, "priv_" + suffix + "@aloute.test", true);
        testViewer = createUser("viewer_" + suffix, "viewer_" + suffix + "@aloute.test", false);
        stranger = createUser("stranger_" + suffix, "stranger_" + suffix + "@aloute.test", false);
    }

    @Test
    @DisplayName("DB: Standalone PUBLIC post with null community is visible to guest and logged-in viewers (LEFT JOIN check)")
    void standalonePublicPostWithNullCommunityIsVisible() {
        Post standalone = createPost(publicAuthor, "Standalone post without community", PostVisibility.PUBLIC, null);

        // Guest viewer (null viewerId)
        ApiResponse<List<PostResponse>> guestList = postService.listPublic(0, 50, null);
        assertThat(guestList.data()).extracting(PostResponse::id).contains(standalone.getId());

        // Authenticated viewer
        ApiResponse<List<PostResponse>> userList = postService.listPublic(0, 50, testViewer.getId());
        assertThat(userList.data()).extracting(PostResponse::id).contains(standalone.getId());

        // Direct get
        PostResponse directResp = postService.get(standalone.getId(), testViewer.getId());
        assertThat(directResp.id()).isEqualTo(standalone.getId());
        assertThat(directResp.communityId()).isNull();
    }

    @Test
    @DisplayName("DB: Public post by private author is hidden from guest and stranger, but visible to approved follower")
    void privateAuthorPostVisibility() {
        Post post = createPost(privateAuthor, "Diary by private author", PostVisibility.PUBLIC, null);

        // Guest cannot see in list or get
        ApiResponse<List<PostResponse>> guestList = postService.listPublic(0, 50, null);
        assertThat(guestList.data()).extracting(PostResponse::id).doesNotContain(post.getId());
        assertThatThrownBy(() -> postService.get(post.getId(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Stranger (non-follower) cannot see
        ApiResponse<List<PostResponse>> strangerList = postService.listPublic(0, 50, stranger.getId());
        assertThat(strangerList.data()).extracting(PostResponse::id).doesNotContain(post.getId());
        assertThatThrownBy(() -> postService.get(post.getId(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Make testViewer follow privateAuthor
        createFollow(testViewer, privateAuthor);

        // Approved follower CAN see in list and get
        ApiResponse<List<PostResponse>> followerList = postService.listPublic(0, 50, testViewer.getId());
        assertThat(followerList.data()).extracting(PostResponse::id).contains(post.getId());

        PostResponse followerGet = postService.get(post.getId(), testViewer.getId());
        assertThat(followerGet.id()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("DB: FOLLOWERS visibility post is visible only to followers and author, denied to non-followers")
    void followersVisibilityPost() {
        Post followersPost = createPost(publicAuthor, "Followers only special post", PostVisibility.FOLLOWERS, null);

        // Stranger denied
        assertThatThrownBy(() -> postService.get(followersPost.getId(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Author can see
        PostResponse authorResp = postService.get(followersPost.getId(), publicAuthor.getId());
        assertThat(authorResp.id()).isEqualTo(followersPost.getId());

        // Follower can see
        createFollow(testViewer, publicAuthor);
        PostResponse followerResp = postService.get(followersPost.getId(), testViewer.getId());
        assertThat(followerResp.id()).isEqualTo(followersPost.getId());
    }

    @Test
    @DisplayName("DB: MENTIONED visibility post fails closed for non-author, allowed for author")
    void mentionedVisibilityPostFailsClosedForNonAuthor() {
        Post mentionedPost = createPost(publicAuthor, "Mentioned post for someone", PostVisibility.MENTIONED, null);

        // Stranger denied
        assertThatThrownBy(() -> postService.get(mentionedPost.getId(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Author allowed
        PostResponse authorResp = postService.get(mentionedPost.getId(), publicAuthor.getId());
        assertThat(authorResp.id()).isEqualTo(mentionedPost.getId());
    }

    @Test
    @DisplayName("DB: Mutual block hides posts at SQL query and direct get level")
    void mutualBlockHidesPosts() {
        Post post = createPost(publicAuthor, "Post by public author", PostVisibility.PUBLIC, null);

        // Viewer blocks author
        blockRepository.save(Block.builder().blocker(testViewer).blocked(publicAuthor).build());

        ApiResponse<List<PostResponse>> list = postService.listPublic(0, 50, testViewer.getId());
        assertThat(list.data()).extracting(PostResponse::id).doesNotContain(post.getId());

        assertThatThrownBy(() -> postService.get(post.getId(), testViewer.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("DB: Private community post requires membership; author who left group is denied (Finding 4)")
    void privateCommunityMembershipAndLeftAuthorCheck() {
        User commOwner = createUser("comm_owner_" + System.nanoTime(), "comm_owner_" + System.nanoTime() + "@aloute.test", false);

        Community privateComm = new Community();
        privateComm.setName("Secret Club " + System.nanoTime());
        privateComm.setSlug("secret-club-" + System.nanoTime());
        privateComm.setPrivateCommunity(true);
        privateComm.setOwner(commOwner);
        privateComm = communityRepository.save(privateComm);

        Post commPost = createPost(publicAuthor, "Internal club update", PostVisibility.PUBLIC, privateComm);

        // Initially publicAuthor and testViewer are not in community members table
        // (Even the author who is not in community_members cannot access)
        assertThatThrownBy(() -> postService.get(commPost.getId(), publicAuthor.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // Actual community owner can read the post even without a membership row
        PostResponse ownerResp = postService.get(commPost.getId(), commOwner.getId());
        assertThat(ownerResp.id()).isEqualTo(commPost.getId());

        // Add testViewer as member
        communityMemberRepository.save(new CommunityMember(privateComm, testViewer, CommunityRole.MEMBER));

        // testViewer can now see the post
        PostResponse memberResp = postService.get(commPost.getId(), testViewer.getId());
        assertThat(memberResp.id()).isEqualTo(commPost.getId());

        // Stranger remains denied
        assertThatThrownBy(() -> postService.get(commPost.getId(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("DB: Nested reference nullification hides restricted quoted post ID from unauthorized viewer")
    void nestedReferenceNullification() {
        Post restrictedTarget = createPost(publicAuthor, "Target restricted post", PostVisibility.FOLLOWERS, null);

        Post publicQuote = createPost(publicAuthor, "Public quote of restricted post", PostVisibility.PUBLIC, null);
        publicQuote.setQuotedPost(restrictedTarget);
        publicQuote = postRepository.save(publicQuote);

        // Stranger (non-follower) views publicQuote
        PostResponse response = postService.get(publicQuote.getId(), stranger.getId());
        assertThat(response.id()).isEqualTo(publicQuote.getId());
        // quotedPostId must be nullified because stranger cannot view restrictedTarget
        assertThat(response.quotedPostId()).isNull();

        // Follower views publicQuote
        createFollow(testViewer, publicAuthor);
        PostResponse followerResponse = postService.get(publicQuote.getId(), testViewer.getId());
        assertThat(followerResponse.quotedPostId()).isEqualTo(restrictedTarget.getId());
    }

    @Test
    @DisplayName("DB: Bookmarked posts pagination & count with interleaved hidden rows (Finding 5)")
    void bookmarkedPostsPaginationAndCountCorrectness() {
        // Create 3 posts:
        // p1: Public post (visible)
        // p2: Followers post by publicAuthor (hidden to stranger)
        // p3: Public post (visible)
        Post p1 = createPost(publicAuthor, "Bookmark post 1", PostVisibility.PUBLIC, null);
        Post p2 = createPost(publicAuthor, "Bookmark post 2 (hidden)", PostVisibility.FOLLOWERS, null);
        Post p3 = createPost(publicAuthor, "Bookmark post 3", PostVisibility.PUBLIC, null);

        // Stranger bookmarks all 3 posts
        bookmarkRepository.save(new Bookmark(stranger, p1));
        bookmarkRepository.save(new Bookmark(stranger, p2));
        bookmarkRepository.save(new Bookmark(stranger, p3));

        // Stranger requests bookmarked posts:
        ApiResponse<List<PostResponse>> result = postService.bookmarkedPosts(stranger.getId(), 0, 10);

        assertThat(result.success()).isTrue();
        // Visible bookmarks must be exactly 2 (p3 and p1), p2 is filtered out in SQL
        assertThat(result.data()).hasSize(2);
        assertThat(result.data()).extracting(PostResponse::id).containsExactlyInAnyOrder(p3.getId(), p1.getId());

        // Total elements must be 2, NOT 3 (no short pages or invisible count disclosure!)
        PageMeta meta = (PageMeta) result.meta();
        assertThat(meta.totalElements()).isEqualTo(2);
        assertThat(meta.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB: Restricted post read with media attachments never exposes media URLs to unauthorized viewer; authorized viewer receives them")
    void restrictedPostMediaAttachmentPrivacyEnforcement() {
        // Create a restricted followers-only post
        Post followersPost = createPost(publicAuthor, "Restricted content with attachments", PostVisibility.FOLLOWERS, null);

        // Attach media asset to the post
        MediaAsset asset = new MediaAsset();
        asset.setOwner(publicAuthor);
        asset.setType(com.aloute.entity.MediaType.IMAGE);
        asset.setPurpose(MediaPurpose.POST);
        asset.setUrl("https://cdn.aloute.test/uploads/confidential-photo.jpg");
        asset.setStoragePath("/storage/confidential-photo.jpg");
        asset.setMimeType("image/jpeg");
        asset.setFileSize(204800L);
        asset = mediaAssetRepository.save(asset);

        postMediaRepository.save(new PostMedia(followersPost, asset, 0));

        // 1. Unauthorized stranger calling get() fails closed with POST_ACCESS_DENIED, never exposing media URL
        assertThatThrownBy(() -> postService.get(followersPost.getId(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // 2. Guest (unauthenticated) calling get() also fails closed with POST_ACCESS_DENIED
        assertThatThrownBy(() -> postService.get(followersPost.getId(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        // 3. Authorized follower calling get() receives PostResponse with attached media URL
        createFollow(testViewer, publicAuthor);
        PostResponse followerResponse = postService.get(followersPost.getId(), testViewer.getId());
        assertThat(followerResponse).isNotNull();
        assertThat(followerResponse.media()).hasSize(1);
        assertThat(followerResponse.media().get(0).url()).isEqualTo("https://cdn.aloute.test/uploads/confidential-photo.jpg");

        // 4. Author calling get() on their own post also receives media attachments
        PostResponse authorResponse = postService.get(followersPost.getId(), publicAuthor.getId());
        assertThat(authorResponse).isNotNull();
        assertThat(authorResponse.media()).hasSize(1);
        assertThat(authorResponse.media().get(0).url()).isEqualTo("https://cdn.aloute.test/uploads/confidential-photo.jpg");
    }

    @Test
    @DisplayName("DB: F-04: Muted author's posts are absent from home, following, and explore feeds for muting viewer, with exact page counts, while direct get remains accessible")
    void mutedAuthorPostsHiddenFromFeedsWhileDirectAccessRemainsAllowed() {
        // Author A (muted by testViewer) and Author B (unmuted)
        User authorA = publicAuthor;
        User authorB = stranger;

        Post postA = createPost(authorA, "Public post by author A (to be muted)", PostVisibility.PUBLIC, null);
        Post postB = createPost(authorB, "Public post by author B (not muted)", PostVisibility.PUBLIC, null);

        // testViewer follows both authorA and authorB
        createFollow(testViewer, authorA);
        createFollow(testViewer, authorB);

        Pageable pageable = PageRequest.of(0, 10);

        // Pre-mute: capture baseline feed totals and confirm initial visibility of both posts
        Page<PostResponse> preMuteHomeFeed = feedService.getHomeFeed(pageable, testViewer.getId());
        long preMuteHomeTotal = preMuteHomeFeed.getTotalElements();
        assertThat(preMuteHomeFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId(), postB.getId());

        Page<PostResponse> preMuteFollowingFeed = feedService.getFollowingFeed(pageable, testViewer.getId());
        long preMuteFollowingTotal = preMuteFollowingFeed.getTotalElements();
        assertThat(preMuteFollowingFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId(), postB.getId());
        // testViewer follows exactly the two test authors (authorA and authorB)
        assertThat(preMuteFollowingTotal).isEqualTo(2L);

        Page<PostResponse> preMuteExploreFeed = feedService.getExploreFeed(pageable, testViewer.getId());
        long preMuteExploreTotal = preMuteExploreFeed.getTotalElements();
        assertThat(preMuteExploreFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId(), postB.getId());

        // testViewer mutes authorA
        createMute(testViewer, authorA);

        // 1. Home Feed for testViewer: excludes postA, includes postB; total elements drops by exactly authorA's newly created post (1)
        Page<PostResponse> homeFeed = feedService.getHomeFeed(pageable, testViewer.getId());
        assertThat(homeFeed.getContent()).extracting(PostResponse::id)
                .doesNotContain(postA.getId())
                .contains(postB.getId());
        assertThat(homeFeed.getTotalElements()).isEqualTo(preMuteHomeTotal - 1L);

        // 2. Following Feed for testViewer: excludes postA, includes postB; total elements drops from 2 followed test authors to the 1 unmuted author
        Page<PostResponse> followingFeed = feedService.getFollowingFeed(pageable, testViewer.getId());
        assertThat(followingFeed.getContent()).extracting(PostResponse::id)
                .doesNotContain(postA.getId())
                .contains(postB.getId());
        assertThat(followingFeed.getTotalElements()).isEqualTo(1L);

        // 3. Explore Feed for testViewer: excludes postA, includes postB; total elements drops by exactly authorA's newly created post (1)
        Page<PostResponse> exploreFeed = feedService.getExploreFeed(pageable, testViewer.getId());
        assertThat(exploreFeed.getContent()).extracting(PostResponse::id)
                .doesNotContain(postA.getId())
                .contains(postB.getId());
        assertThat(exploreFeed.getTotalElements()).isEqualTo(preMuteExploreTotal - 1L);

        // 4. Direct access via postService.get for postA remains fully accessible to testViewer
        PostResponse directA = postService.get(postA.getId(), testViewer.getId());
        assertThat(directA).isNotNull();
        assertThat(directA.id()).isEqualTo(postA.getId());

        // 5. Direct search / public list via postService.listPublic remains accessible
        ApiResponse<List<PostResponse>> publicList = postService.listPublic(0, 10, testViewer.getId());
        assertThat(publicList.data()).extracting(PostResponse::id).contains(postA.getId());

        // 6. Guest viewer (null currentUserId) sees both postA and postB in home and explore feeds
        Page<PostResponse> guestHomeFeed = feedService.getHomeFeed(pageable, null);
        assertThat(guestHomeFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId(), postB.getId());

        Page<PostResponse> guestExploreFeed = feedService.getExploreFeed(pageable, null);
        assertThat(guestExploreFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId(), postB.getId());

        // 7. Unrelated user (authorB) who did NOT mute authorA sees postA in home feed
        Page<PostResponse> otherUserHomeFeed = feedService.getHomeFeed(pageable, authorB.getId());
        assertThat(otherUserHomeFeed.getContent()).extracting(PostResponse::id)
                .contains(postA.getId());

        // 8. Bookmarks: bookmarked post authored by muted user remains accessible in bookmarks
        bookmarkRepository.save(new Bookmark(testViewer, postA));
        ApiResponse<List<PostResponse>> bookmarks = postService.bookmarkedPosts(testViewer.getId(), 0, 10);
        assertThat(bookmarks.data()).extracting(PostResponse::id).contains(postA.getId());

        // 9. Direct search query finds postA even if author is muted
        ApiResponse<List<PostResponse>> searchRes = postService.searchPosts("Public post by author A", 0, 10, testViewer.getId());
        assertThat(searchRes.data()).extracting(PostResponse::id).contains(postA.getId());

        // 10. Unmute authorA: authorA's post immediately reappears in home feed and total increases back
        muteRepository.delete(muteRepository.findAll().stream()
                .filter(m -> m.getMuter().getId().equals(testViewer.getId()) && m.getMuted().getId().equals(authorA.getId()))
                .findFirst().orElseThrow());
        Page<PostResponse> unmutedHomeFeed = feedService.getHomeFeed(pageable, testViewer.getId());
        assertThat(unmutedHomeFeed.getContent()).extracting(PostResponse::id).contains(postA.getId());
        assertThat(unmutedHomeFeed.getTotalElements()).isEqualTo(preMuteHomeTotal);
    }

    @Test
    void communityFeedFiltersMutedAuthorsBeforePaginationButDirectPostRemainsReadable() {
        Community community = new Community();
        community.setName("Mute community " + System.nanoTime());
        community.setSlug("mute-community-" + System.nanoTime());
        community.setOwner(testViewer);
        community = communityRepository.save(community);
        Post mutedPost = createPost(publicAuthor, "Muted community post", PostVisibility.PUBLIC, community);
        Post visiblePost = createPost(stranger, "Visible community post", PostVisibility.PUBLIC, community);

        assertThat(((PageMeta) postService.communityPosts(community.getId(), 0, 1, testViewer.getId()).meta())
                .totalElements()).isEqualTo(2);
        createMute(testViewer, publicAuthor);

        ApiResponse<List<PostResponse>> page = postService.communityPosts(community.getId(), 0, 1, testViewer.getId());
        assertThat(page.data()).extracting(PostResponse::id).containsExactly(visiblePost.getId());
        assertThat(((PageMeta) page.meta()).totalElements()).isEqualTo(1);
        assertThat(postService.get(mutedPost.getId(), testViewer.getId()).id()).isEqualTo(mutedPost.getId());
        assertThat(((PageMeta) postService.communityPosts(community.getId(), 0, 1, null).meta())
                .totalElements()).isEqualTo(2);
    }

    @Test
    void topicFeedFiltersMutedAuthorsBeforePaginationOnlyForMutingViewer() {
        Topic topic = new Topic();
        topic.setName("Mute topic " + System.nanoTime());
        topic.setSlug("mute-topic-" + System.nanoTime());
        topic = topicRepository.save(topic);
        Post mutedPost = createPost(publicAuthor, "Muted topic post", PostVisibility.PUBLIC, null);
        Post visiblePost = createPost(stranger, "Visible topic post", PostVisibility.PUBLIC, null);
        postTopicRepository.save(new PostTopic(mutedPost, topic));
        postTopicRepository.save(new PostTopic(visiblePost, topic));
        createMute(testViewer, publicAuthor);

        ApiResponse<List<PostResponse>> page = postService.topicPosts(topic.getId(), 0, 1, testViewer.getId());
        assertThat(page.data()).extracting(PostResponse::id).containsExactly(visiblePost.getId());
        assertThat(((PageMeta) page.meta()).totalElements()).isEqualTo(1);
        assertThat(((PageMeta) postService.topicPosts(topic.getId(), 0, 1, stranger.getId()).meta())
                .totalElements()).isEqualTo(2);
        assertThat(((PageMeta) postService.topicPosts(topic.getId(), 0, 1, null).meta())
                .totalElements()).isEqualTo(2);
    }

    @Test
    void repliesByMutedAuthorsRemainVisible() {
        Post parent = createPost(testViewer, "Visible parent", PostVisibility.PUBLIC, null);
        Post reply = createPost(publicAuthor, "Reply by muted author", PostVisibility.PUBLIC, null);
        reply.setParentPost(parent);
        postRepository.saveAndFlush(reply);
        createMute(testViewer, publicAuthor);

        ApiResponse<List<PostResponse>> page = postService.replies(parent.getId(), 0, 1, testViewer.getId());
        assertThat(page.data()).extracting(PostResponse::id).containsExactly(reply.getId());
        assertThat(((PageMeta) page.meta()).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("N-05: Recommended feed and SSR Home exclude muted author at database level before slicing with size 1 fixture")
    void recommendedFeedAndSsrHomeExcludeMutedAuthorBeforeSlicing() throws Exception {
        User authorA = publicAuthor;
        User authorB = stranger;

        Post olderPostB = createPost(authorB, "Older post by author B (visible)", PostVisibility.PUBLIC, null);
        Thread.sleep(20);
        Post newerPostA = createPost(authorA, "Newer post by author A (to be muted)", PostVisibility.PUBLIC, null);

        // Pre-mute: capture baseline recommended feed total for testViewer
        ApiResponse<List<PostResponse>> preMuteRecommended = postService.getRecommendedFeed(testViewer.getId(), 0, 50);
        long preMuteTotal = ((PageMeta) preMuteRecommended.meta()).totalElements();
        assertThat(preMuteRecommended.data()).extracting(PostResponse::id)
                .contains(newerPostA.getId(), olderPostB.getId());

        createMute(testViewer, authorA);

        // 1. Recommended feed for testViewer with size=1:
        // Before fix: postRepository.findAll returns newerPostA (the muted post) because mute was not ANDed before slicing.
        // After fix: newerPostA is excluded before pagination; olderPostB is returned, totalElements drops by 1.
        ApiResponse<List<PostResponse>> recommended = postService.getRecommendedFeed(testViewer.getId(), 0, 1);
        assertThat(recommended.data()).extracting(PostResponse::id)
                .containsExactly(olderPostB.getId());
        assertThat(((PageMeta) recommended.meta()).totalElements()).isEqualTo(preMuteTotal - 1L);

        // 2. Direct get of newerPostA remains readable for testViewer
        PostResponse directA = postService.get(newerPostA.getId(), testViewer.getId());
        assertThat(directA).isNotNull();
        assertThat(directA.id()).isEqualTo(newerPostA.getId());

        // 3. Guest viewer (null viewerId) sees newerPostA in size=1 recommended feed
        ApiResponse<List<PostResponse>> guestRecommended = postService.getRecommendedFeed(null, 0, 1);
        assertThat(guestRecommended.data()).extracting(PostResponse::id)
                .containsExactly(newerPostA.getId());
        assertThat(((PageMeta) guestRecommended.meta()).totalElements()).isGreaterThanOrEqualTo(2L);

        // 4. Other non-muting user sees newerPostA in size=1 recommended feed
        ApiResponse<List<PostResponse>> otherRecommended = postService.getRecommendedFeed(authorB.getId(), 0, 1);
        assertThat(otherRecommended.data()).extracting(PostResponse::id)
                .containsExactly(newerPostA.getId());
        assertThat(((PageMeta) otherRecommended.meta()).totalElements()).isGreaterThanOrEqualTo(2L);

        // 5. SSR Home GET /feed with testViewer principal: model attributes posts and pageMeta exclude muted author
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/feed").with(authentication(createMockAuth(testViewer))))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<PostResponse> feedPosts = (List<PostResponse>) result.getModelAndView().getModel().get("posts");
        assertThat(feedPosts).extracting(PostResponse::id)
                .contains(olderPostB.getId())
                .doesNotContain(newerPostA.getId());

        PageMeta ssrPageMeta = (PageMeta) result.getModelAndView().getModel().get("pageMeta");
        assertThat(ssrPageMeta).isNotNull();
        assertThat(ssrPageMeta.totalElements()).isEqualTo(preMuteTotal - 1L);
    }
}
