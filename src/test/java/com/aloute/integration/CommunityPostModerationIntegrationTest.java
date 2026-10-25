package com.aloute.integration;

import com.aloute.dto.request.UpdatePostRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Community post approval lifecycle: submission gating, moderator decisions,
 * leak prevention across retrieval surfaces, concurrency guards and
 * cross-community authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityPostModerationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PostService postService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostModerationRemovalRepository removalRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;

    private User owner;
    private User adminMember;
    private User ordinaryMember;
    private User anotherMember;
    private User outsider;
    private Community publicCommunity;
    private Community approvalCommunity;
    private Community otherCommunity;
    private Authentication ownerAuth;
    private Authentication adminAuth;
    private Authentication memberAuth;
    private Authentication anotherAuth;
    private Authentication outsiderAuth;

    private User createUser(String username) {
        long suffix = System.nanoTime();
        User u = new User();
        u.setUsername(username + "_" + suffix);
        u.setEmail(username + "_" + suffix + "@aloute.test");
        u.setPasswordHash("$2a$10$hashedPlaceholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.save(u);
        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + username);
        profileRepository.save(p);
        return u;
    }

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Community newCommunity(User owner, String name, boolean isPrivate, boolean requireApproval) {
        long suffix = System.nanoTime();
        Community c = new Community();
        c.setName(name + " " + suffix);
        c.setSlug((name.toLowerCase().replace(' ', '-') + "-" + suffix));
        c.setOwner(owner);
        c.setPrivateCommunity(isPrivate);
        c.setRequirePostApproval(requireApproval);
        c.setMemberCount(1L);
        c = communityRepository.save(c);
        memberRepository.save(new CommunityMember(c, owner, CommunityRole.ADMIN));
        return c;
    }

    private Post submitPost(User author, Community community) {
        return postRepository.save(postFor(author, community));
    }

    private Post postFor(User author, Community community) {
        Post post = new Post();
        post.setUser(author);
        post.setContent("Community post by " + author.getUsername());
        post.setVisibility(PostVisibility.PUBLIC);
        post.setCommunity(community);
        return post;
    }

    @BeforeEach
    void setUp() {
        owner = createUser("cmod_owner");
        adminMember = createUser("cmod_admin");
        ordinaryMember = createUser("cmod_member");
        anotherMember = createUser("cmod_member2");
        outsider = createUser("cmod_outsider");

        ownerAuth = auth(owner);
        adminAuth = auth(adminMember);
        memberAuth = auth(ordinaryMember);
        anotherAuth = auth(anotherMember);
        outsiderAuth = auth(outsider);

        publicCommunity = newCommunity(owner, "Public Comm", false, false);
        memberRepository.save(new CommunityMember(publicCommunity, ordinaryMember, CommunityRole.MEMBER));

        approvalCommunity = newCommunity(owner, "Approval Comm", false, true);
        memberRepository.save(new CommunityMember(approvalCommunity, adminMember, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(approvalCommunity, ordinaryMember, CommunityRole.MEMBER));
        memberRepository.save(new CommunityMember(approvalCommunity, anotherMember, CommunityRole.MEMBER));
        approvalCommunity.setMemberCount(4L);
        communityRepository.save(approvalCommunity);

        otherCommunity = newCommunity(owner, "Other Comm", false, false);
    }

    private Long createPostViaApi(Long communityId, Authentication userAuth, String content) throws Exception {
        String body = "{\"content\":\"" + content + "\",\"visibility\":\"PUBLIC\"}";
        String json = mockMvc.perform(post("/api/communities/{cid}/posts", communityId)
                        .with(authentication(userAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // data.id
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(json).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("Approval disabled: member submission publishes immediately")
    void memberSubmissionPublishesImmediatelyWhenApprovalDisabled() throws Exception {
        Long postId = createPostViaApi(publicCommunity.getId(), memberAuth, "Publish me directly");
        Post post = postRepository.findById(postId).orElseThrow();
        assertThat(post.getModerationStatus()).isEqualTo(PostModerationStatus.APPROVED);
        assertThat(postService.communityPosts(publicCommunity.getId(), 0, 20, anotherMember.getId())
                .getData()).extracting(PostResponse::id).contains(postId);
    }

    @Test
    @DisplayName("Approval enabled: member submission becomes PENDING; owner/admin publish immediately")
    void approvalGatingByRole() throws Exception {
        Long memberPostId = createPostViaApi(approvalCommunity.getId(), memberAuth, "Needs review");
        Post memberPost = postRepository.findById(memberPostId).orElseThrow();
        assertThat(memberPost.getModerationStatus()).isEqualTo(PostModerationStatus.PENDING);

        Long adminPostId = createPostViaApi(approvalCommunity.getId(), adminAuth, "Admin publishes instantly");
        assertThat(postRepository.findById(adminPostId).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.APPROVED);

        Long ownerPostId = createPostViaApi(approvalCommunity.getId(), ownerAuth, "Owner publishes instantly");
        assertThat(postRepository.findById(ownerPostId).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.APPROVED);
    }

    @Test
    @DisplayName("PENDING post does not leak: community feed, global list, search, direct URL, other timeline")
    void pendingPostLeaksNowhere() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        // Community feed (another member / member author / guest)
        assertThat(postService.communityPosts(approvalCommunity.getId(), 0, 20, anotherMember.getId())
                .getData()).extracting(PostResponse::id).doesNotContain(pending.getId());
        assertThat(postService.communityPosts(approvalCommunity.getId(), 0, 20, ordinaryMember.getId())
                .getData()).extracting(PostResponse::id).doesNotContain(pending.getId());

        // Global list
        assertThat(postService.listPublic(0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).doesNotContain(pending.getId());

        // Search
        assertThat(postService.searchPosts("Community post by", 0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).doesNotContain(pending.getId());

        // Other viewer's view of author timeline
        assertThat(postService.userPosts(ordinaryMember.getId(), 0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).doesNotContain(pending.getId());

        // Direct URL for non-moderator member → 404-grade denial
        mockMvc.perform(get("/api/posts/{pid}", pending.getId()).with(authentication(anotherAuth)))
                .andExpect(status().isForbidden());

        // Direct URL for outsider → denied
        mockMvc.perform(get("/api/posts/{pid}", pending.getId()).with(authentication(outsiderAuth)))
                .andExpect(status().isForbidden());

        // Repost of a pending post is denied for non-moderators
        mockMvc.perform(put("/api/posts/{pid}/reposts/me", pending.getId()).with(authentication(anotherAuth)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PENDING post is visible to its author (timeline + direct URL) and moderators")
    void pendingVisibleToAuthorAndModerators() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        // Author timeline (self)
        assertThat(postService.userPosts(ordinaryMember.getId(), 0, 50, ordinaryMember.getId()).getData())
                .extracting(PostResponse::id).contains(pending.getId());
        // Author direct read
        PostResponse self = postService.get(pending.getId(), ordinaryMember.getId());
        assertThat(self.moderationStatus()).isEqualTo(PostModerationStatus.PENDING);

        // Moderator (owner) direct read + moderation capability
        PostResponse modView = postService.get(pending.getId(), owner.getId());
        assertThat(modView.moderationStatus()).isEqualTo(PostModerationStatus.PENDING);
        assertThat(modView.capabilities().canApprove()).isTrue();
        assertThat(modView.capabilities().canRemove()).isFalse();
    }

    @Test
    @DisplayName("Approve lifecycle: publish, audit record, duplicate decision rejected")
    void approveLifecycleAndDuplicateGuard() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), pending.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));

        assertThat(pending.getModerationStatus()).isEqualTo(PostModerationStatus.APPROVED);
        assertThat(postService.communityPosts(approvalCommunity.getId(), 0, 20, anotherMember.getId())
                .getData()).extracting(PostResponse::id).contains(pending.getId());
        assertThat(postService.listPublic(0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).contains(pending.getId());

        // Audit trail
        assertThat(moderationActionRepository.findRecentByCommunityId(approvalCommunity.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)))
                .anyMatch(a -> "COMMUNITY_POST_APPROVE".equals(a.getActionType())
                        && pending.getId().equals(a.getTargetId())
                        && adminMember.getId().equals(a.getActor().getId()));

        // Duplicate approval → 409
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), pending.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Reject with reason: hidden, reason visible to author, double reject rejected, re-approve allowed")
    void rejectLifecycle() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/reject",
                        approvalCommunity.getId(), pending.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Noi dung khong phu hop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("REJECTED"));

        assertThat(pending.getModerationStatus()).isEqualTo(PostModerationStatus.REJECTED);
        assertThat(pending.getModerationReason()).isEqualTo("Noi dung khong phu hop");

        // Author still sees it, with the reason; others do not
        PostResponse authorView = postService.get(pending.getId(), ordinaryMember.getId());
        assertThat(authorView.moderationStatus()).isEqualTo(PostModerationStatus.REJECTED);
        assertThat(authorView.moderationReason()).isEqualTo("Noi dung khong phu hop");
        assertThat(postService.listPublic(0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).doesNotContain(pending.getId());

        // Double reject → 409
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/reject",
                        approvalCommunity.getId(), pending.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());

        // Moderator can still change their mind → approve
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), pending.getId())
                        .with(authentication(ownerAuth)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("Author edit policy: pending stays pending; approved re-queues under approval; rejected resubmits")
    void authorEditPolicy() {
        // pending stays pending
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);
        postService.update(ordinaryMember.getId(), pending.getId(),
                new UpdatePostRequest("edited content", null, null, null));
        assertThat(postRepository.findById(pending.getId()).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.PENDING);

        // approved + ordinary member edit under approval → PENDING again
        Post approved = submitPost(ordinaryMember, approvalCommunity);
        approved.setModerationStatus(PostModerationStatus.APPROVED);
        postRepository.save(approved);
        postService.update(ordinaryMember.getId(), approved.getId(),
                new UpdatePostRequest("edited to bypass review", null, null, null));
        assertThat(postRepository.findById(approved.getId()).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.PENDING);

        // moderator author edit of own approved post stays approved
        Post modPost = submitPost(adminMember, approvalCommunity);
        modPost.setModerationStatus(PostModerationStatus.APPROVED);
        postRepository.save(modPost);
        postService.update(adminMember.getId(), modPost.getId(),
                new UpdatePostRequest("admin edited", null, null, null));
        assertThat(postRepository.findById(modPost.getId()).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.APPROVED);

        // rejected post edited with approval off → publishes
        Community offCommunity = newCommunity(owner, "Approval Off", false, false);
        memberRepository.save(new CommunityMember(offCommunity, ordinaryMember, CommunityRole.MEMBER));
        Post rejected = submitPost(ordinaryMember, offCommunity);
        rejected.setModerationStatus(PostModerationStatus.REJECTED);
        postRepository.save(rejected);
        postService.update(ordinaryMember.getId(), rejected.getId(),
                new UpdatePostRequest("fixed content", null, null, null));
        assertThat(postRepository.findById(rejected.getId()).orElseThrow().getModerationStatus())
                .isEqualTo(PostModerationStatus.APPROVED);
    }

    @Test
    @DisplayName("Author may delete their own PENDING post")
    void authorDeletesPendingPost() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        mockMvc.perform(delete("/api/posts/{pid}", pending.getId()).with(authentication(memberAuth)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(pending.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Moderator removal of a published post: soft-delete + provenance + stats")
    void moderatorRemovalOfPublishedPost() throws Exception {
        Post published = submitPost(ordinaryMember, approvalCommunity);
        published.setModerationStatus(PostModerationStatus.APPROVED);
        postRepository.save(published);

        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/remove",
                        approvalCommunity.getId(), published.getId())
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vi pham noi quy\"}"))
                .andExpect(status().isNoContent());

        Post removed = postRepository.findById(published.getId()).orElseThrow();
        assertThat(removed.getDeletedAt()).isNotNull();
        assertThat(removalRepository.existsByPostIdAndRestoredAtIsNull(published.getId())).isTrue();
        assertThat(postService.listPublic(0, 50, anotherMember.getId()).getData())
                .extracting(PostResponse::id).doesNotContain(published.getId());

        // Removing an already-removed post → 409/404 (not 2xx)
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/remove",
                        approvalCommunity.getId(), published.getId())
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404), org.hamcrest.Matchers.is(409))));
    }

    @Test
    @DisplayName("Cross-community moderation is denied and posts of another community are concealed")
    void crossCommunityAuthorization() throws Exception {
        Post otherPost = submitPost(owner, otherCommunity);
        otherPost.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(otherPost);

        // admin of approvalCommunity is not a moderator of otherCommunity
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        otherCommunity.getId(), otherPost.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isForbidden());

        // moderating via the wrong community path conceals the post entirely
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), otherPost.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ordinary member and outsider cannot moderate; dashboard authorization enforced")
    void moderationAuthorizationMatrix() throws Exception {
        Post pending = submitPost(ordinaryMember, approvalCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        // Ordinary member of the same community
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), pending.getId()).with(authentication(anotherAuth)).with(csrf()))
                .andExpect(status().isForbidden());
        // Outsider with no membership
        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        approvalCommunity.getId(), pending.getId()).with(authentication(outsiderAuth)).with(csrf()))
                .andExpect(status().isForbidden());

        // Manage dashboard page: owner 200, admin 200, member 403, outsider 403
        mockMvc.perform(get("/communities/{cid}/manage", approvalCommunity.getId()).with(authentication(ownerAuth)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/communities/{cid}/manage", approvalCommunity.getId()).with(authentication(adminAuth)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/communities/{cid}/manage", approvalCommunity.getId()).with(authentication(memberAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/communities/{cid}/manage", approvalCommunity.getId()).with(authentication(outsiderAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Private community stays concealed: outsider moderation and dashboard yield 404")
    void privateCommunityConcealment() throws Exception {
        Community privateCommunity = newCommunity(owner, "Hidden Comm", true, true);
        memberRepository.save(new CommunityMember(privateCommunity, ordinaryMember, CommunityRole.MEMBER));
        Post pending = submitPost(ordinaryMember, privateCommunity);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);

        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        privateCommunity.getId(), pending.getId()).with(authentication(outsiderAuth)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/communities/{cid}/manage", privateCommunity.getId()).with(authentication(outsiderAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleted community: moderation is refused with 404")
    void deletedCommunityRefusesModeration() throws Exception {
        Community doomed = newCommunity(owner, "Doomed Comm", false, true);
        memberRepository.save(new CommunityMember(doomed, ordinaryMember, CommunityRole.MEMBER));
        Post pending = submitPost(ordinaryMember, doomed);
        pending.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending);
        doomed.setDeletedAt(java.time.Instant.now());
        communityRepository.save(doomed);

        mockMvc.perform(post("/api/communities/{cid}/posts/{pid}/approve",
                        doomed.getId(), pending.getId()).with(authentication(ownerAuth)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dashboard stats and pending list reflect moderation state")
    void statsAndPendingLists() {
        Post pending1 = submitPost(ordinaryMember, approvalCommunity);
        pending1.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending1);
        Post pending2 = submitPost(anotherMember, approvalCommunity);
        pending2.setModerationStatus(PostModerationStatus.PENDING);
        postRepository.save(pending2);
        Post published = submitPost(adminMember, approvalCommunity);
        published.setModerationStatus(PostModerationStatus.APPROVED);
        postRepository.save(published);

        com.aloute.dto.response.CommunityManageStatsResponse s =
                moderationService.stats(owner.getId(), approvalCommunity.getId());
        assertThat(s.pendingPostCount()).isEqualTo(2L);
        assertThat(s.publishedPostCount()).isGreaterThanOrEqualTo(1L);

        List<PostResponse> pendingList = moderationService
                .pendingPosts(owner.getId(), approvalCommunity.getId(), 0, 20).getData();
        assertThat(pendingList).extracting(PostResponse::id).contains(pending1.getId(), pending2.getId());
    }

    @Autowired
    private com.aloute.service.CommunityModerationService moderationService;
}
