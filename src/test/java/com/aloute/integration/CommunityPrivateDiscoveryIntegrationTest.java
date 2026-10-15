package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner-review candidate for Option A strict privacy. Relational H2 tests only;
 * MySQL and owner policy approval are separate release gates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityPrivateDiscoveryIntegrationTest {

    @Autowired private MockMvc mvc;
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
    private Community secret;
    private Community open;
    private Post secretPost;
    private String searchTerm;

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
        searchTerm = "privacy-review-" + suffix;
        owner = createUser("privacy_owner_" + suffix);
        member = createUser("privacy_member_" + suffix);
        outsider = createUser("privacy_outsider_" + suffix);

        secret = new Community();
        secret.setName(searchTerm + " secret");
        secret.setSlug(searchTerm + "-secret");
        secret.setDescription("restricted description " + suffix);
        secret.setOwner(owner);
        secret.setPrivateCommunity(true);
        secret.setMemberCount(1);
        secret = communityRepository.save(secret);

        // Member has been admitted by an approved workflow; public self-join is
        // the only self-service path and is not used to manufacture private access.
        memberRepository.save(new CommunityMember(secret, member, CommunityRole.MEMBER));

        open = new Community();
        open.setName(searchTerm + " public");
        open.setSlug(searchTerm + "-public");
        open.setOwner(owner);
        open.setPrivateCommunity(false);
        open = communityRepository.save(open);

        secretPost = new Post();
        secretPost.setUser(owner);
        secretPost.setContent("Restricted discussion " + suffix);
        secretPost.setVisibility(PostVisibility.PUBLIC);
        secretPost.setCommunity(secret);
        secretPost.setAllowReply(true);
        secretPost.setAllowQuote(true);
        secretPost = postRepository.save(secretPost);
    }

    @Test
    void discoveryFiltersPrivateCommunitiesBeforePaginationAndTotals() throws Exception {
        ApiResponse<List<CommunityResponse>> guest = communityService.list(searchTerm, 0, 1, null);
        assertThat(guest.data()).extracting(CommunityResponse::id).containsExactly(open.getId());
        assertThat(((PageMeta) guest.meta()).totalElements()).isEqualTo(1);

        ApiResponse<List<CommunityResponse>> unrelated = communityService.list(searchTerm, 0, 10, outsider.getId());
        assertThat(unrelated.data()).extracting(CommunityResponse::id).containsExactly(open.getId());
        assertThat(((PageMeta) unrelated.meta()).totalElements()).isEqualTo(1);

        ApiResponse<List<CommunityResponse>> authorized = communityService.list(searchTerm, 0, 10, member.getId());
        assertThat(authorized.data()).extracting(CommunityResponse::id).containsExactlyInAnyOrder(secret.getId(), open.getId());
        assertThat(((PageMeta) authorized.meta()).totalElements()).isEqualTo(2);

        ApiResponse<List<CommunityResponse>> ownerResult = communityService.list(searchTerm, 0, 10, owner.getId());
        assertThat(ownerResult.data()).extracting(CommunityResponse::id).contains(secret.getId());

        mvc.perform(get("/api/communities").param("q", searchTerm))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(secret.getSlug(), secret.getDescription()));

        mvc.perform(get("/communities").param("q", searchTerm))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(secret.getSlug(), secret.getDescription()));
    }

    @Test
    void privateDetailsAndMembershipAreConcealedFromGuestsAndOutsiders() throws Exception {
        assertThatThrownBy(() -> communityService.get(secret.getId(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_NOT_FOUND));
        assertThatThrownBy(() -> communityService.getBySlug(secret.getSlug(), outsider.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_NOT_FOUND));

        mvc.perform(get("/api/communities/" + secret.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/communities/" + secret.getId() + "/members"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/communities/" + secret.getId() + "/members")
                        .with(authentication(auth(outsider))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/c/" + secret.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(secret.getDescription()));

        mvc.perform(get("/api/communities/" + secret.getId()).with(authentication(auth(member))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/communities/" + secret.getId() + "/members")
                        .with(authentication(auth(member))))
                .andExpect(status().isOk());
        mvc.perform(get("/c/" + secret.getSlug()).with(authentication(auth(member))))
                .andExpect(status().isOk());

        assertThat(communityService.get(secret.getId(), owner.getId()).id()).isEqualTo(secret.getId());
        mvc.perform(get("/api/communities/" + open.getId() + "/members"))
                .andExpect(status().isOk());
    }

    @Test
    void deletedCommunityAndItsPostsCannotBeDiscovered() throws Exception {
        secret.setDeletedAt(Instant.now());
        communityRepository.saveAndFlush(secret);

        assertThatThrownBy(() -> communityService.getBySlug(secret.getSlug(), member.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_NOT_FOUND));
        assertThatThrownBy(() -> communityService.get(secret.getId(), owner.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_NOT_FOUND));

        mvc.perform(get("/api/communities/" + secret.getId()).with(authentication(auth(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/communities/" + secret.getId() + "/members")
                        .with(authentication(auth(member))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/communities/" + secret.getSlug()).with(authentication(auth(member))))
                .andExpect(status().isNotFound());

        ApiResponse<List<CommunityResponse>> visible = communityService.list(searchTerm, 0, 10, member.getId());
        assertThat(visible.data()).extracting(CommunityResponse::id).doesNotContain(secret.getId());
        assertThat(((PageMeta) visible.meta()).totalElements()).isEqualTo(1);

        assertThat(postService.isPostVisibleTo(secretPost, member.getId())).isFalse();
        assertThatThrownBy(() -> postService.get(secretPost.getId(), member.getId()))
                .isInstanceOf(ApiException.class);

        ApiResponse<List<PostResponse>> posts = postService.listPublic(0, 100, member.getId());
        assertThat(posts.data()).extracting(PostResponse::id).doesNotContain(secretPost.getId());
    }
}
