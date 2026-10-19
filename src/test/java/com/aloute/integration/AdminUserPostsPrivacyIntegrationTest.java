package com.aloute.integration;

import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.admin.AdminPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminUserPostsPrivacyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminPostService adminPostService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private PostModerationRemovalRepository postModerationRemovalRepository;

    private User adminUser;
    private User normalUser;
    private User targetAuthor;
    private Authentication adminAuth;
    private Authentication normalAuth;

    private User createUser(String username, String email, boolean isPrivate, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashedPlaceholder");
        u.setVerified(true);
        u.setPrivateAccount(isPrivate);
        u.setActive(true);
        u.setRole(role);
        u = userRepository.save(u);

        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + username);
        profileRepository.save(p);
        return u;
    }

    private Post createPost(User author, String content, PostVisibility visibility, Community community, Post parentPost, Instant deletedAt) {
        Post p = new Post();
        p.setUser(author);
        p.setContent(content);
        p.setVisibility(visibility);
        p.setCommunity(community);
        p.setParentPost(parentPost);
        p.setAllowReply(true);
        p.setAllowQuote(true);
        p.setDeletedAt(deletedAt);
        return postRepository.save(p);
    }

    private Community createCommunity(String name, String slug, User owner, boolean isPrivate) {
        Community c = new Community();
        c.setName(name);
        c.setSlug(slug);
        c.setOwner(owner);
        c.setPrivateCommunity(isPrivate);
        return communityRepository.save(c);
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        adminUser = createUser("admin_" + suffix, "admin_" + suffix + "@aloute.test", false, Role.SYSTEM_ADMIN);
        normalUser = createUser("user_" + suffix, "user_" + suffix + "@aloute.test", false, Role.USER);
        targetAuthor = createUser("author_" + suffix, "author_" + suffix + "@aloute.test", false, Role.USER);

        UserPrincipal adminPrincipal = UserPrincipal.from(adminUser);
        adminAuth = new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities());

        UserPrincipal normalPrincipal = UserPrincipal.from(normalUser);
        normalAuth = new UsernamePasswordAuthenticationToken(normalPrincipal, null, normalPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts: 401 when unauthenticated")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts: 403 when authenticated as regular user")
    void forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts")
                        .with(authentication(normalAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts: 404 USER_NOT_FOUND when user does not exist")
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/users/9999999/posts")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts: 422 on constraint violation, 400 on invalid contentState")
    void invalidParametersReturnError() throws Exception {
        // invalid page (< 0) -> ConstraintViolationException maps to 422
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?page=-1")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // invalid size (>100) -> ConstraintViolationException maps to 422
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?size=101")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // invalid contentState -> AppException(BAD_REQUEST) maps to 400
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?contentState=BOGUS_STATE")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("DB: Public posts by target user return 200 with accurate metadata and envelope")
    void publicPostsReturn200WithAccurateMetadata() throws Exception {
        Post p1 = createPost(targetAuthor, "Public post 1", PostVisibility.PUBLIC, null, null, null);
        Post p2 = createPost(targetAuthor, "Public post 2", PostVisibility.PUBLIC, null, null, null);

        // Another author's post should NOT be returned
        createPost(normalUser, "Normal user post", PostVisibility.PUBLIC, null, null, null);

        // Reply by target author should NOT be returned (parentPost != null)
        createPost(targetAuthor, "Reply post", PostVisibility.PUBLIC, null, p1, null);

        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(p2.getId()))
                .andExpect(jsonPath("$.data[1].id").value(p1.getId()))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(1))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20));
    }

    @Test
    @DisplayName("DB: Private community post is excluded for outsider admin viewer, included when member")
    void privateCommunityPostConcealmentAndAccess() {
        Community privateComm = createCommunity("Secret Comm", "secret-comm-" + System.nanoTime(), targetAuthor, true);
        Post privPost = createPost(targetAuthor, "Confidential community post", PostVisibility.PUBLIC, privateComm, null, null);

        // 1. Admin is NOT a member: post is completely excluded in database query before pagination
        Page<AdminPostSummaryResponse> outsiderPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(outsiderPage.getTotalElements()).isEqualTo(0L);
        assertThat(outsiderPage.getContent()).isEmpty();

        // 2. Admin joins private community: post is visible
        CommunityMember member = new CommunityMember();
        member.setCommunity(privateComm);
        member.setUser(adminUser);
        member.setRole(CommunityRole.MEMBER);
        communityMemberRepository.save(member);

        Page<AdminPostSummaryResponse> memberPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(memberPage.getTotalElements()).isEqualTo(1L);
        assertThat(memberPage.getContent().get(0).id()).isEqualTo(privPost.getId());
    }

    @Test
    @DisplayName("DB: Follower-only post is excluded when admin does not follow author, included when following")
    void followerOnlyPostPrivacy() {
        Post followOnlyPost = createPost(targetAuthor, "Followers only thoughts", PostVisibility.FOLLOWERS, null, null, null);

        // 1. Admin does NOT follow targetAuthor: excluded
        Page<AdminPostSummaryResponse> notFollowingPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(notFollowingPage.getTotalElements()).isEqualTo(0L);
        assertThat(notFollowingPage.getContent()).isEmpty();

        // 2. Admin follows targetAuthor: included
        Follow follow = new Follow();
        follow.setId(new FollowId(adminUser.getId(), targetAuthor.getId()));
        follow.setFollower(adminUser);
        follow.setFollowing(targetAuthor);
        followRepository.save(follow);

        Page<AdminPostSummaryResponse> followingPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(followingPage.getTotalElements()).isEqualTo(1L);
        assertThat(followingPage.getContent().get(0).id()).isEqualTo(followOnlyPost.getId());
    }

    @Test
    @DisplayName("DB: Private account posts are excluded when admin does not follow author, included when following")
    void privateAccountPostsPrivacy() {
        targetAuthor.setPrivateAccount(true);
        userRepository.save(targetAuthor);

        Post privAccPost = createPost(targetAuthor, "Private account post", PostVisibility.PUBLIC, null, null, null);

        // 1. Admin does NOT follow private targetAuthor: excluded
        Page<AdminPostSummaryResponse> notFollowingPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(notFollowingPage.getTotalElements()).isEqualTo(0L);

        // 2. Admin follows private targetAuthor: included
        Follow follow = new Follow();
        follow.setId(new FollowId(adminUser.getId(), targetAuthor.getId()));
        follow.setFollower(adminUser);
        follow.setFollowing(targetAuthor);
        followRepository.save(follow);

        Page<AdminPostSummaryResponse> followingPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(followingPage.getTotalElements()).isEqualTo(1L);
        assertThat(followingPage.getContent().get(0).id()).isEqualTo(privAccPost.getId());
    }

    @Test
    @DisplayName("DB: Both-way block excludes posts completely from query results and total counts")
    void bothWayBlockExcludesPosts() {
        Post p = createPost(targetAuthor, "Public post by author", PostVisibility.PUBLIC, null, null, null);

        // Baseline: visible
        Page<AdminPostSummaryResponse> baseline = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(baseline.getTotalElements()).isEqualTo(1L);

        // Case A: Admin blocked targetAuthor
        Block block1 = new Block();
        block1.setBlocker(adminUser);
        block1.setBlocked(targetAuthor);
        blockRepository.save(block1);

        Page<AdminPostSummaryResponse> blockedByViewer = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(blockedByViewer.getTotalElements()).isEqualTo(0L);
        assertThat(blockedByViewer.getContent()).isEmpty();

        // Clear block1, test Case B: Target author blocked admin
        blockRepository.delete(block1);

        Block block2 = new Block();
        block2.setBlocker(targetAuthor);
        block2.setBlocked(adminUser);
        blockRepository.save(block2);

        Page<AdminPostSummaryResponse> blockedByAuthor = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(blockedByAuthor.getTotalElements()).isEqualTo(0L);
        assertThat(blockedByAuthor.getContent()).isEmpty();
    }

    @Test
    @DisplayName("DB: Deleted posts do NOT leak through data, metadata or totals under ordinary-viewer privacy")
    void deletedPostsDoNotLeakUnderOrdinaryViewerPrivacy() {
        // Active post
        Post activePost = createPost(targetAuthor, "Active post", PostVisibility.PUBLIC, null, null, null);

        // Author deleted post
        Post deletedPost = createPost(targetAuthor, "Author deleted post", PostVisibility.PUBLIC, null, null, Instant.now());

        // Default query: only active post is visible; deleted post is excluded at database query level
        Page<AdminPostSummaryResponse> defaultPage = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(defaultPage.getTotalElements()).isEqualTo(1L);
        assertThat(defaultPage.getContent().get(0).id()).isEqualTo(activePost.getId());

        // Query with contentState=ACTIVE: only active post returned
        Page<AdminPostSummaryResponse> activePage = adminPostService.getUserPosts(
                targetAuthor.getId(), "ACTIVE", PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(activePage.getTotalElements()).isEqualTo(1L);
        assertThat(activePage.getContent().get(0).id()).isEqualTo(activePost.getId());

        // Query with contentState=AUTHOR_DELETED: totalElements is 0 because deleted posts are not visible to ordinary viewer
        Page<AdminPostSummaryResponse> authorDeletedPage = adminPostService.getUserPosts(
                targetAuthor.getId(), "AUTHOR_DELETED", PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(authorDeletedPage.getTotalElements()).isEqualTo(0L);
        assertThat(authorDeletedPage.getContent()).isEmpty();

        // Query with contentState=ADMIN_REMOVED: totalElements is 0
        Page<AdminPostSummaryResponse> adminRemovedPage = adminPostService.getUserPosts(
                targetAuthor.getId(), "ADMIN_REMOVED", PageRequest.of(0, 10), adminUser.getId()
        );
        assertThat(adminRemovedPage.getTotalElements()).isEqualTo(0L);
        assertThat(adminRemovedPage.getContent()).isEmpty();
    }

    @Test
    @DisplayName("DB: Mixed public and restricted posts paginate accurately with size 1 without leaking restricted records or metadata")
    void mixedPublicAndRestrictedPaginationWithoutLeakingRestrictedMetadata() throws Exception {
        // Author has:
        // 1. First public post (created earlier)
        Post pub1 = createPost(targetAuthor, "Public Post 1", PostVisibility.PUBLIC, null, null, null);
        // 2. Restricted follower-only post (viewer does not follow)
        Post restrictedFollower = createPost(targetAuthor, "Restricted Follower Post", PostVisibility.FOLLOWERS, null, null, null);
        // 3. Restricted private community post (viewer is not member)
        Community privateComm = createCommunity("Secret Group", "secret-group-" + System.nanoTime(), targetAuthor, true);
        Post restrictedComm = createPost(targetAuthor, "Restricted Community Post", PostVisibility.PUBLIC, privateComm, null, null);
        // 4. Second public post (created later)
        Post pub2 = createPost(targetAuthor, "Public Post 2", PostVisibility.PUBLIC, null, null, null);
        // 5. Author-deleted post
        Post deletedPost = createPost(targetAuthor, "Deleted Post", PostVisibility.PUBLIC, null, null, Instant.now());

        // Total visible posts for adminUser (who is not follower, not community member) must be exactly 2 (pub2 and pub1).
        // Test HTTP endpoint with size=1, page=0
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?page=0&size=1")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pub2.getId()))
                .andExpect(jsonPath("$.data[0].contentExcerpt").value("Public Post 2"))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(1));

        // Test HTTP endpoint with size=1, page=1
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?page=1&size=1")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(pub1.getId()))
                .andExpect(jsonPath("$.data[0].contentExcerpt").value("Public Post 1"))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(1));

        // Direct service check verifying no restricted IDs or metadata ever present
        Page<AdminPostSummaryResponse> page0 = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 1), adminUser.getId()
        );
        Page<AdminPostSummaryResponse> page1 = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(1, 1), adminUser.getId()
        );
        assertThat(page0.getTotalElements()).isEqualTo(2L);
        assertThat(page1.getTotalElements()).isEqualTo(2L);
        assertThat(page0.getContent().get(0).id()).isEqualTo(pub2.getId());
        assertThat(page1.getContent().get(0).id()).isEqualTo(pub1.getId());
        assertThat(List.of(page0.getContent().get(0).id(), page1.getContent().get(0).id()))
                .doesNotContain(restrictedFollower.getId(), restrictedComm.getId(), deletedPost.getId());
    }

    private Post createQuotingPost(User author, String content, PostVisibility visibility, Post quotedPost) {
        Post p = new Post();
        p.setUser(author);
        p.setContent(content);
        p.setVisibility(visibility);
        p.setQuotedPost(quotedPost);
        p.setAllowReply(true);
        p.setAllowQuote(true);
        return postRepository.save(p);
    }

    @Test
    @DisplayName("DB: Quoted post privacy in getUserPosts: public visible quote ID preserved, inaccessible/deleted quote masked to null")
    void quotedPostPrivacyInGetUserPosts() throws Exception {
        // 1. A public visible post
        Post publicTarget = createPost(targetAuthor, "Public Target", PostVisibility.PUBLIC, null, null, null);

        // 2. An inaccessible restricted post (followers only, admin does not follow)
        Post followerOnlyTarget = createPost(targetAuthor, "Followers Only Target", PostVisibility.FOLLOWERS, null, null, null);

        // 3. A soft-deleted post
        Post deletedTarget = createPost(targetAuthor, "Deleted Target", PostVisibility.PUBLIC, null, null, Instant.now());

        // 4. A post in private community where admin is NOT member
        Community secretComm = createCommunity("Hidden Club", "hidden-club-" + System.nanoTime(), targetAuthor, true);
        Post privateCommTarget = createPost(targetAuthor, "Private Comm Target", PostVisibility.PUBLIC, secretComm, null, null);

        // Author creates 4 quoting posts quoting each target:
        Post quotePublic = createQuotingPost(targetAuthor, "Quote of public", PostVisibility.PUBLIC, publicTarget);
        Post quoteFollower = createQuotingPost(targetAuthor, "Quote of follower-only", PostVisibility.PUBLIC, followerOnlyTarget);
        Post quoteDeleted = createQuotingPost(targetAuthor, "Quote of deleted", PostVisibility.PUBLIC, deletedTarget);
        Post quotePrivateComm = createQuotingPost(targetAuthor, "Quote of private comm", PostVisibility.PUBLIC, privateCommTarget);

        // Verify via HTTP endpoint
        mockMvc.perform(get("/api/admin/users/" + targetAuthor.getId() + "/posts?page=0&size=10")
                        .with(authentication(adminAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Direct service check
        Page<AdminPostSummaryResponse> page = adminPostService.getUserPosts(
                targetAuthor.getId(), null, PageRequest.of(0, 10), adminUser.getId()
        );

        // Find the quoting posts in the response
        AdminPostSummaryResponse respQuotePublic = page.getContent().stream()
                .filter(r -> r.id().equals(quotePublic.getId())).findFirst().orElseThrow();
        AdminPostSummaryResponse respQuoteFollower = page.getContent().stream()
                .filter(r -> r.id().equals(quoteFollower.getId())).findFirst().orElseThrow();
        AdminPostSummaryResponse respQuoteDeleted = page.getContent().stream()
                .filter(r -> r.id().equals(quoteDeleted.getId())).findFirst().orElseThrow();
        AdminPostSummaryResponse respQuotePrivateComm = page.getContent().stream()
                .filter(r -> r.id().equals(quotePrivateComm.getId())).findFirst().orElseThrow();

        // Visible target -> quotedPostId preserved
        assertThat(respQuotePublic.quotedPostId()).isEqualTo(publicTarget.getId());

        // Inaccessible targets -> quotedPostId masked to null
        assertThat(respQuoteFollower.quotedPostId()).isNull();
        assertThat(respQuoteDeleted.quotedPostId()).isNull();
        assertThat(respQuotePrivateComm.quotedPostId()).isNull();
    }
}
