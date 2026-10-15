package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        postInPrivateWithoutOwnerMember.setUser(ownerWithoutMembership);
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
    @DisplayName("Characterize owner-without-membership: post access denied with 404 while metadata remains accessible")
    void characterizeOwnerWithoutMembershipDenialAndInconsistency() throws Exception {
        // HTTP API denies posts with 404 COMMUNITY_NOT_FOUND (no bypass granted)
        mvc.perform(get("/api/communities/" + privateCommunityWithoutOwnerMember.getId() + "/posts")
                        .with(authentication(auth(ownerWithoutMembership))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));

        // Direct postService.communityPosts also throws 404 COMMUNITY_NOT_FOUND
        assertThatThrownBy(() -> postService.communityPosts(
                privateCommunityWithoutOwnerMember.getId(), 0, 20, ownerWithoutMembership.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_NOT_FOUND");
                });

        // Direct isPostVisibleTo returns false
        assertThat(postService.isPostVisibleTo(postInPrivateWithoutOwnerMember, ownerWithoutMembership.getId())).isFalse();

        // Contrasting behavior: Community metadata service allows owner read
        CommunityResponse metadata = communityService.get(
                privateCommunityWithoutOwnerMember.getId(), ownerWithoutMembership.getId());
        assertThat(metadata.id()).isEqualTo(privateCommunityWithoutOwnerMember.getId());
        assertThat(metadata.ownerId()).isEqualTo(ownerWithoutMembership.getId());
        assertThat(metadata.isOwner()).isTrue();
    }
}
