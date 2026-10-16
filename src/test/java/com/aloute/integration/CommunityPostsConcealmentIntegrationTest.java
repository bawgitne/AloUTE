package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.UpdateCommunityRequest;
import com.aloute.dto.request.UpdatePostRequest;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ZCode F-01 Integration Tests:
 * Verifies that unauthorized guest/nonmember requests to list private community posts
 * return the exact same 404 status and COMMUNITY_NOT_FOUND code/message contract
 * as nonexistent or deleted communities, without revealing private community existence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityPostsConcealmentIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CommunityService communityService;
    @Autowired private PostService postService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private RepostRepository repostRepository;

    private User owner;
    private User member;
    private User outsider;
    private User ownerWithoutMembership;

    private Community privateCommunity;
    private Community publicCommunity;
    private Community deletedCommunity;
    private Community privateCommunityWithoutOwnerMember;

    private Post postInPrivate;
    private Post postInPublic;
    private Post postInPrivateWithoutOwnerMember;

    private User createUser(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@aloute.test");
        user.setPasswordHash("$2a$10$hashedPlaceholder");
        user.setActive(true);
        user.setVerified(true);
        user.setRole(Role.USER);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(name);
        profileRepository.save(profile);
        return user;
    }

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void setup() {
        String suffix = Long.toString(System.nanoTime());

        owner = createUser("f01_owner_" + suffix);
        member = createUser("f01_member_" + suffix);
        outsider = createUser("f01_outsider_" + suffix);
        ownerWithoutMembership = createUser("f01_ownernomem_" + suffix);

        // 1. Private community with owner and member
        privateCommunity = new Community();
        privateCommunity.setName("Private Group " + suffix);
        privateCommunity.setSlug("private-group-" + suffix);
        privateCommunity.setDescription("Secret discussion " + suffix);
        privateCommunity.setOwner(owner);
        privateCommunity.setPrivateCommunity(true);
        privateCommunity.setMemberCount(2);
        privateCommunity = communityRepository.save(privateCommunity);

        memberRepository.save(new CommunityMember(privateCommunity, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(privateCommunity, member, CommunityRole.MEMBER));

        postInPrivate = new Post();
        postInPrivate.setUser(owner);
        postInPrivate.setContent("Private community content " + suffix);
        postInPrivate.setVisibility(PostVisibility.PUBLIC);
        postInPrivate.setCommunity(privateCommunity);
        postInPrivate.setAllowReply(true);
        postInPrivate.setAllowQuote(true);
        postInPrivate = postRepository.save(postInPrivate);

        // 2. Public community
        publicCommunity = new Community();
        publicCommunity.setName("Public Group " + suffix);
        publicCommunity.setSlug("public-group-" + suffix);
        publicCommunity.setDescription("Open discussion " + suffix);
        publicCommunity.setOwner(owner);
        publicCommunity.setPrivateCommunity(false);
        publicCommunity.setMemberCount(1);
        publicCommunity = communityRepository.save(publicCommunity);

        memberRepository.save(new CommunityMember(publicCommunity, owner, CommunityRole.ADMIN));

        postInPublic = new Post();
        postInPublic.setUser(owner);
        postInPublic.setContent("Public community content " + suffix);
        postInPublic.setVisibility(PostVisibility.PUBLIC);
        postInPublic.setCommunity(publicCommunity);
        postInPublic.setAllowReply(true);
        postInPublic.setAllowQuote(true);
        postInPublic = postRepository.save(postInPublic);

        // 3. Soft-deleted private community
        deletedCommunity = new Community();
        deletedCommunity.setName("Deleted Group " + suffix);
        deletedCommunity.setSlug("deleted-group-" + suffix);
        deletedCommunity.setOwner(owner);
        deletedCommunity.setPrivateCommunity(true);
        deletedCommunity.setDeletedAt(Instant.now());
        deletedCommunity = communityRepository.save(deletedCommunity);

        // 4. Private community where owner lacks membership row
        privateCommunityWithoutOwnerMember = new Community();
        privateCommunityWithoutOwnerMember.setName("Unlinked Owner Group " + suffix);
        privateCommunityWithoutOwnerMember.setSlug("unlinked-owner-" + suffix);
        privateCommunityWithoutOwnerMember.setOwner(ownerWithoutMembership);
        privateCommunityWithoutOwnerMember.setPrivateCommunity(true);
        privateCommunityWithoutOwnerMember.setMemberCount(0);
        privateCommunityWithoutOwnerMember = communityRepository.save(privateCommunityWithoutOwnerMember);

        postInPrivateWithoutOwnerMember = new Post();
        postInPrivateWithoutOwnerMember.setUser(member);
        postInPrivateWithoutOwnerMember.setContent("Unlinked owner post " + suffix);
        postInPrivateWithoutOwnerMember.setVisibility(PostVisibility.PUBLIC);
        postInPrivateWithoutOwnerMember.setCommunity(privateCommunityWithoutOwnerMember);
        postInPrivateWithoutOwnerMember.setAllowReply(true);
        postInPrivateWithoutOwnerMember.setAllowQuote(true);
        postInPrivateWithoutOwnerMember = postRepository.save(postInPrivateWithoutOwnerMember);
    }

    @Test
    @DisplayName("Guest (unauthenticated) requesting private community posts receives 404 COMMUNITY_NOT_FOUND")
    void guestPrivateCommunityPostsConcealedAsNotFound() throws Exception {
        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"));
    }

    @Test
    @DisplayName("Authenticated non-member requesting private community posts receives 404 COMMUNITY_NOT_FOUND")
    void authenticatedNonMemberPrivateCommunityPostsConcealedAsNotFound() throws Exception {
        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"));
    }

    @Test
    @DisplayName("Denied private community posts response contract matches nonexistent community contract exactly")
    void deniedMatchesMissingCommunityContractForGuestAndNonmember() throws Exception {
        // Query missing community
        MvcResult missingResult = mvc.perform(get("/api/communities/999999/posts"))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode missingJson = objectMapper.readTree(missingResult.getResponse().getContentAsString());
        String expectedCode = missingJson.path("error").path("code").asText();
        String expectedMessage = missingJson.path("error").path("message").asText();

        assertThat(expectedCode).isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(expectedMessage).isEqualTo("Community not found");

        // Guest on private community
        MvcResult guestResult = mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts"))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode guestJson = objectMapper.readTree(guestResult.getResponse().getContentAsString());
        assertThat(guestJson.path("error").path("code").asText()).isEqualTo(expectedCode);
        assertThat(guestJson.path("error").path("message").asText()).isEqualTo(expectedMessage);

        // Authenticated non-member on private community
        MvcResult outsiderResult = mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(outsider))))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode outsiderJson = objectMapper.readTree(outsiderResult.getResponse().getContentAsString());
        assertThat(outsiderJson.path("error").path("code").asText()).isEqualTo(expectedCode);
        assertThat(outsiderJson.path("error").path("message").asText()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Nonexistent and soft-deleted communities return 404 COMMUNITY_NOT_FOUND")
    void nonexistentAndDeletedCommunityPostsReturnNotFound() throws Exception {
        mvc.perform(get("/api/communities/999999/posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(get("/api/communities/" + deletedCommunity.getId() + "/posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(get("/api/communities/" + deletedCommunity.getId() + "/posts")
                        .with(authentication(auth(member))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("Authorized member can list private community posts")
    void memberCanReadPrivateCommunityPosts() throws Exception {
        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(postInPrivate.getId()))
                .andExpect(jsonPath("$.data[0].content").value(postInPrivate.getContent()));

        ApiResponse<List<PostResponse>> direct = postService.communityPosts(privateCommunity.getId(), 0, 20, member.getId());
        assertThat(direct.data()).extracting(PostResponse::id).contains(postInPrivate.getId());
    }

    @Test
    @DisplayName("Owner-with-membership can list private community posts")
    void ownerWithMembershipCanReadPrivateCommunityPosts() throws Exception {
        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(postInPrivate.getId()));

        ApiResponse<List<PostResponse>> direct = postService.communityPosts(privateCommunity.getId(), 0, 20, owner.getId());
        assertThat(direct.data()).extracting(PostResponse::id).contains(postInPrivate.getId());
    }

    @Test
    @DisplayName("Public community posts are accessible to guest and non-member")
    void publicCommunityPostsAccessibleToGuestAndNonMember() throws Exception {
        mvc.perform(get("/api/communities/" + publicCommunity.getId() + "/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(postInPublic.getId()));

        mvc.perform(get("/api/communities/" + publicCommunity.getId() + "/posts")
                        .with(authentication(auth(outsider))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(postInPublic.getId()));
    }

    @Test
    @DisplayName("Owner-without-membership: metadata, community posts, and direct post visibility accessible (read consistency), but mutations/posting denied")
    void ownerWithoutMembershipHasReadConsistencyWithoutMembershipRow() throws Exception {
        // Also save a post authored by the owner without membership in this community
        Post post = new Post();
        post.setUser(ownerWithoutMembership);
        post.setContent("Post authored by owner without membership");
        post.setVisibility(PostVisibility.PUBLIC);
        post.setCommunity(privateCommunityWithoutOwnerMember);
        post.setAllowReply(true);
        post.setAllowQuote(true);
        final Post ownerAuthoredPost = postRepository.save(post);

        // 1. Metadata service allows owner read
        CommunityResponse metadata = communityService.get(
                privateCommunityWithoutOwnerMember.getId(), ownerWithoutMembership.getId());
        assertThat(metadata.id()).isEqualTo(privateCommunityWithoutOwnerMember.getId());
        assertThat(metadata.ownerId()).isEqualTo(ownerWithoutMembership.getId());
        assertThat(metadata.isOwner()).isTrue();
        assertThat(metadata.currentUserRole()).isNull();

        // 2. HTTP API allows owner to read community posts (both others' post and own post)
        mvc.perform(get("/api/communities/" + privateCommunityWithoutOwnerMember.getId() + "/posts")
                        .with(authentication(auth(ownerWithoutMembership))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.id == " + postInPrivateWithoutOwnerMember.getId() + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id == " + ownerAuthoredPost.getId() + ")]").exists());

        // 3. Direct postService.communityPosts succeeds for owner without membership
        ApiResponse<List<PostResponse>> direct = postService.communityPosts(
                privateCommunityWithoutOwnerMember.getId(), 0, 20, ownerWithoutMembership.getId());
        assertThat(direct.data()).extracting(PostResponse::id)
                .contains(postInPrivateWithoutOwnerMember.getId(), ownerAuthoredPost.getId());

        // 4. Direct isPostVisibleTo returns true for owner without membership on others' post
        assertThat(postInPrivateWithoutOwnerMember.getUser().getId()).isNotEqualTo(ownerWithoutMembership.getId());
        assertThat(postService.isPostVisibleTo(postInPrivateWithoutOwnerMember, ownerWithoutMembership.getId())).isTrue();
        assertThat(postService.isPostVisibleTo(ownerAuthoredPost, ownerWithoutMembership.getId())).isTrue();

        // 5. Direct post read via HTTP succeeds for owner without membership on others' post
        mvc.perform(get("/api/posts/" + postInPrivateWithoutOwnerMember.getId())
                        .with(authentication(auth(ownerWithoutMembership))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(postInPrivateWithoutOwnerMember.getId()));

        PostResponse directGet = postService.get(postInPrivateWithoutOwnerMember.getId(), ownerWithoutMembership.getId());
        assertThat(directGet.id()).isEqualTo(postInPrivateWithoutOwnerMember.getId());

        // --- MUTATION RESTRICTIONS (Owner without membership CANNOT mutate) ---

        // 6. Posting still requires membership: service and HTTP reject with 403 COMMUNITY_MEMBERSHIP_REQUIRED
        CreatePostRequest createReq = new CreatePostRequest("Owner attempt", PostVisibility.PUBLIC, true, true, null, null, null, null);
        assertThatThrownBy(() -> postService.create(ownerWithoutMembership.getId(), createReq,
                privateCommunityWithoutOwnerMember.getId(), null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_MEMBERSHIP_REQUIRED");
                });

        mvc.perform(post("/api/communities/" + privateCommunityWithoutOwnerMember.getId() + "/posts")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_MEMBERSHIP_REQUIRED"));

        // 7. Cannot reply to others' post in private community w/o membership -> 403 POST_ACCESS_DENIED
        CreatePostRequest replyReq = new CreatePostRequest("Owner reply attempt", PostVisibility.PUBLIC, true, true, null, null, null, null);
        assertThatThrownBy(() -> postService.create(ownerWithoutMembership.getId(), replyReq, null, postInPrivateWithoutOwnerMember.getId(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(post("/api/posts/" + postInPrivateWithoutOwnerMember.getId() + "/replies")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // 8. Cannot quote others' post in private community w/o membership -> 403 POST_ACCESS_DENIED
        CreatePostRequest quoteReq = new CreatePostRequest("Owner quote attempt", PostVisibility.PUBLIC, true, true, null, null, null, null);
        assertThatThrownBy(() -> postService.create(ownerWithoutMembership.getId(), quoteReq, null, null, postInPrivateWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(post("/api/posts/" + postInPrivateWithoutOwnerMember.getId() + "/quotes")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // 9. Cannot like others' post in private community w/o membership -> 403 POST_ACCESS_DENIED
        assertThatThrownBy(() -> postService.like(ownerWithoutMembership.getId(), postInPrivateWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(put("/api/posts/" + postInPrivateWithoutOwnerMember.getId() + "/likes/me")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // 10. Cannot repost others' post in private community w/o membership -> 403 POST_ACCESS_DENIED
        assertThatThrownBy(() -> postService.repost(ownerWithoutMembership.getId(), postInPrivateWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(put("/api/posts/" + postInPrivateWithoutOwnerMember.getId() + "/reposts/me")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // 11. Cannot bookmark others' post in private community w/o membership -> 403 POST_ACCESS_DENIED
        assertThatThrownBy(() -> postService.bookmark(ownerWithoutMembership.getId(), postInPrivateWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(put("/api/posts/" + postInPrivateWithoutOwnerMember.getId() + "/bookmarks/me")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // 12. Cannot edit posts in private community w/o membership -> 404 POST_NOT_FOUND
        UpdatePostRequest updateReq = new UpdatePostRequest("Hacked content", null, null, null);
        assertThatThrownBy(() -> postService.update(ownerWithoutMembership.getId(), postInPrivateWithoutOwnerMember.getId(), updateReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("POST_NOT_FOUND");
                });

        assertThatThrownBy(() -> postService.update(ownerWithoutMembership.getId(), ownerAuthoredPost.getId(), updateReq))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("POST_NOT_FOUND");
                });

        mvc.perform(patch("/api/posts/" + ownerAuthoredPost.getId())
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));

        // 13. Cannot delete posts in private community w/o membership -> 404 POST_NOT_FOUND
        assertThatThrownBy(() -> postService.delete(ownerWithoutMembership.getId(), postInPrivateWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("POST_NOT_FOUND");
                });

        assertThatThrownBy(() -> postService.delete(ownerWithoutMembership.getId(), ownerAuthoredPost.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("POST_NOT_FOUND");
                });

        mvc.perform(delete("/api/posts/" + ownerAuthoredPost.getId())
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));

        // 14. Verify NO persisted side effects: counters, flags, memberships, and roles unchanged
        Post reloadedOthersPost = postRepository.findById(postInPrivateWithoutOwnerMember.getId()).orElseThrow();
        assertThat(reloadedOthersPost.getReplyCount()).isEqualTo(0);
        assertThat(reloadedOthersPost.getLikeCount()).isEqualTo(0);
        assertThat(reloadedOthersPost.getRepostCount()).isEqualTo(0);
        assertThat(bookmarkRepository.existsByPostIdAndUserId(postInPrivateWithoutOwnerMember.getId(), ownerWithoutMembership.getId())).isFalse();
        assertThat(postLikeRepository.existsByPostIdAndUserId(postInPrivateWithoutOwnerMember.getId(), ownerWithoutMembership.getId())).isFalse();
        assertThat(repostRepository.existsByPostIdAndUserId(postInPrivateWithoutOwnerMember.getId(), ownerWithoutMembership.getId())).isFalse();

        Post reloadedOwnPost = postRepository.findById(ownerAuthoredPost.getId()).orElseThrow();
        assertThat(reloadedOwnPost.getDeletedAt()).isNull();
        assertThat(reloadedOwnPost.getContent()).isEqualTo("Post authored by owner without membership");

        assertThat(memberRepository.existsByCommunityIdAndUserId(privateCommunityWithoutOwnerMember.getId(), ownerWithoutMembership.getId())).isFalse();
        Community reloadedComm = communityRepository.findById(privateCommunityWithoutOwnerMember.getId()).orElseThrow();
        assertThat(reloadedComm.getMemberCount()).isEqualTo(0);
        User reloadedOwner = userRepository.findById(ownerWithoutMembership.getId()).orElseThrow();
        assertThat(reloadedOwner.getRole()).isEqualTo(Role.USER);

        // 15. Joining private community is denied: owner cannot self-join private community
        assertThatThrownBy(() -> communityService.join(ownerWithoutMembership.getId(), privateCommunityWithoutOwnerMember.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_ACCESS_DENIED");
                });

        mvc.perform(post("/api/communities/" + privateCommunityWithoutOwnerMember.getId() + "/join")
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_ACCESS_DENIED"));

        // 16. Updating community requires membership row: rejected with 403
        mvc.perform(patch("/api/communities/" + privateCommunityWithoutOwnerMember.getId())
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommunityRequest("New Name", null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // 17. Deleting community is permitted for owner (existing baseline privilege)
        mvc.perform(delete("/api/communities/" + privateCommunityWithoutOwnerMember.getId())
                        .with(authentication(auth(ownerWithoutMembership)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Authenticated outsider mutations concealed as 404 matching nonexistent community contract exactly")
    void outsiderMutationsConcealedAsNotFoundMatchingMissingCommunity() throws Exception {
        CreatePostRequest postReq = new CreatePostRequest("Outsider post", PostVisibility.PUBLIC, true, true, null, null, null, null);
        UpdateCommunityRequest patchReq = new UpdateCommunityRequest("Patched", null, null, null, null);

        // --- POST /posts ---
        MvcResult missingPostResult = mvc.perform(post("/api/communities/999999/posts")
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode missingPostJson = objectMapper.readTree(missingPostResult.getResponse().getContentAsString());

        MvcResult concealedPostResult = mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode concealedPostJson = objectMapper.readTree(concealedPostResult.getResponse().getContentAsString());
        assertThat(concealedPostJson.path("error").path("code").asText())
                .isEqualTo(missingPostJson.path("error").path("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(concealedPostJson.path("error").path("message").asText())
                .isEqualTo(missingPostJson.path("error").path("message").asText())
                .isEqualTo("Community not found");

        // --- POST /join ---
        MvcResult missingJoinResult = mvc.perform(post("/api/communities/999999/join")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode missingJoinJson = objectMapper.readTree(missingJoinResult.getResponse().getContentAsString());

        MvcResult concealedJoinResult = mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/join")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode concealedJoinJson = objectMapper.readTree(concealedJoinResult.getResponse().getContentAsString());
        assertThat(concealedJoinJson.path("error").path("code").asText())
                .isEqualTo(missingJoinJson.path("error").path("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(concealedJoinJson.path("error").path("message").asText())
                .isEqualTo(missingJoinJson.path("error").path("message").asText())
                .isEqualTo("Community not found");

        // --- PATCH ---
        MvcResult missingPatchResult = mvc.perform(patch("/api/communities/999999")
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode missingPatchJson = objectMapper.readTree(missingPatchResult.getResponse().getContentAsString());

        MvcResult concealedPatchResult = mvc.perform(patch("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode concealedPatchJson = objectMapper.readTree(concealedPatchResult.getResponse().getContentAsString());
        assertThat(concealedPatchJson.path("error").path("code").asText())
                .isEqualTo(missingPatchJson.path("error").path("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(concealedPatchJson.path("error").path("message").asText())
                .isEqualTo(missingPatchJson.path("error").path("message").asText())
                .isEqualTo("Community not found");

        // --- DELETE ---
        MvcResult missingDeleteResult = mvc.perform(delete("/api/communities/999999")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode missingDeleteJson = objectMapper.readTree(missingDeleteResult.getResponse().getContentAsString());

        MvcResult concealedDeleteResult = mvc.perform(delete("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode concealedDeleteJson = objectMapper.readTree(concealedDeleteResult.getResponse().getContentAsString());
        assertThat(concealedDeleteJson.path("error").path("code").asText())
                .isEqualTo(missingDeleteJson.path("error").path("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(concealedDeleteJson.path("error").path("message").asText())
                .isEqualTo(missingDeleteJson.path("error").path("message").asText())
                .isEqualTo("Community not found");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN outsider concealed with 404 COMMUNITY_NOT_FOUND without membership/ownership override")
    void systemAdminOutsiderConcealedAsNotFound() throws Exception {
        User systemAdmin = createUser("f01_sysadmin_" + System.nanoTime());
        systemAdmin.setRole(Role.SYSTEM_ADMIN);
        systemAdmin = userRepository.save(systemAdmin);

        mvc.perform(get("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(systemAdmin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(systemAdmin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/join")
                        .with(authentication(auth(systemAdmin)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(systemAdmin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("admin post", PostVisibility.PUBLIC, true, true, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(patch("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(systemAdmin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommunityRequest("Admin rename", null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        mvc.perform(delete("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(systemAdmin)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("Guest mutations retain security-layer 401/403 and are not concealed with 404 before authentication")
    void guestMutationsRetainSecurityLayerAuthFailure() throws Exception {
        CreatePostRequest postReq = new CreatePostRequest("Guest post", PostVisibility.PUBLIC, true, true, null, null, null, null);
        UpdateCommunityRequest patchReq = new UpdateCommunityRequest("Guest patch", null, null, null, null);

        mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/join")
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        mvc.perform(patch("/api/communities/" + privateCommunity.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        mvc.perform(delete("/api/communities/" + privateCommunity.getId())
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    @Test
    @DisplayName("Authorized discoverers retain existing operation permissions and 403 when privilege lacking")
    void authorizedDiscoverersRetainExistingPermissionsAndForbidden() throws Exception {
        // Regular member cannot patch community -> 403 FORBIDDEN
        mvc.perform(patch("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(member)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommunityRequest("Member name", null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Regular member cannot delete community -> 403 FORBIDDEN
        mvc.perform(delete("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(member)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Regular member can post in private community -> 201 CREATED
        mvc.perform(post("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(member)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("Member post", PostVisibility.PUBLIC, true, true, null, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Owner with membership can update community -> 200 OK
        mvc.perform(patch("/api/communities/" + privateCommunity.getId())
                        .with(authentication(auth(owner)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommunityRequest("Updated by Owner", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated by Owner"));
    }

    @Test
    @DisplayName("Author who left private community is denied direct and community post reads")
    void authorWhoLeftPrivateCommunityDeniedDirectAndCommunityPostReads() throws Exception {
        // Member creates a post in private community
        Post memberPost = new Post();
        memberPost.setUser(member);
        memberPost.setContent("Post by member before leaving");
        memberPost.setVisibility(PostVisibility.PUBLIC);
        memberPost.setCommunity(privateCommunity);
        memberPost.setAllowReply(true);
        memberPost.setAllowQuote(true);
        Post savedMemberPost = postRepository.save(memberPost);

        // Verify member can see it before leaving
        assertThat(postService.isPostVisibleTo(savedMemberPost, member.getId())).isTrue();

        // Member leaves private community
        memberRepository.deleteByCommunityIdAndUserId(privateCommunity.getId(), member.getId());

        // Now former member: isPostVisibleTo returns false
        assertThat(postService.isPostVisibleTo(savedMemberPost, member.getId())).isFalse();

        // Direct get throws POST_ACCESS_DENIED (403)
        assertThatThrownBy(() -> postService.get(savedMemberPost.getId(), member.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));

        mvc.perform(get("/api/posts/" + savedMemberPost.getId())
                        .with(authentication(auth(member))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        // Community posts listing returns 404 COMMUNITY_NOT_FOUND (concealed)
        mvc.perform(get("/api/communities/" + privateCommunity.getId() + "/posts")
                        .with(authentication(auth(member))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("Public community mutations preserve existing public behavior")
    void publicCommunityMutationsPreservePublicBehavior() throws Exception {
        // Non-member posting to public community requires membership -> 403 COMMUNITY_MEMBERSHIP_REQUIRED
        mvc.perform(post("/api/communities/" + publicCommunity.getId() + "/posts")
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("Public outsider post", PostVisibility.PUBLIC, true, true, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_MEMBERSHIP_REQUIRED"));

        // Non-member updating public community -> 403 FORBIDDEN
        mvc.perform(patch("/api/communities/" + publicCommunity.getId())
                        .with(authentication(auth(outsider)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommunityRequest("Hack name", null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Non-owner deleting public community -> 403 FORBIDDEN
        mvc.perform(delete("/api/communities/" + publicCommunity.getId())
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Outsider joining public community -> 200 OK
        mvc.perform(post("/api/communities/" + publicCommunity.getId() + "/join")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
