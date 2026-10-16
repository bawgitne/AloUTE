package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.service.PostService;
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
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private PostMediaRepository postMediaRepository;

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
}
