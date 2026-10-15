package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityJoinResidualIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CommunityService communityService;
    @Autowired private PostService postService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;

    private User owner;
    private User outsider;
    private User existingMember;
    private Community privateCommunity;
    private Community publicCommunity;
    private Post privatePost;
    private Authentication outsiderAuth;

    private User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
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

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        owner = createUser("owner_" + suffix, "owner_" + suffix + "@aloute.test");
        outsider = createUser("outsider_" + suffix, "outsider_" + suffix + "@aloute.test");
        existingMember = createUser("member_" + suffix, "member_" + suffix + "@aloute.test");

        UserPrincipal outsiderPrincipal = UserPrincipal.from(outsider);
        outsiderAuth = new UsernamePasswordAuthenticationToken(outsiderPrincipal, null, outsiderPrincipal.getAuthorities());

        privateCommunity = new Community();
        privateCommunity.setName("Private Comm " + suffix);
        privateCommunity.setSlug("private-comm-" + suffix);
        privateCommunity.setOwner(owner);
        privateCommunity.setPrivateCommunity(true);
        privateCommunity.setMemberCount(1L);
        privateCommunity = communityRepository.save(privateCommunity);

        // Add owner and existingMember as members
        memberRepository.save(new CommunityMember(privateCommunity, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(privateCommunity, existingMember, CommunityRole.MEMBER));
        privateCommunity.setMemberCount(2L);
        privateCommunity = communityRepository.save(privateCommunity);

        publicCommunity = new Community();
        publicCommunity.setName("Public Comm " + suffix);
        publicCommunity.setSlug("public-comm-" + suffix);
        publicCommunity.setOwner(owner);
        publicCommunity.setPrivateCommunity(false);
        publicCommunity.setMemberCount(1L);
        publicCommunity = communityRepository.save(publicCommunity);
        memberRepository.save(new CommunityMember(publicCommunity, owner, CommunityRole.ADMIN));

        // Create a post in private community
        privatePost = new Post();
        privatePost.setUser(owner);
        privatePost.setContent("Secret post in private community");
        privatePost.setVisibility(PostVisibility.PUBLIC);
        privatePost.setCommunity(privateCommunity);
        privatePost.setAllowReply(true);
        privatePost.setAllowQuote(true);
        privatePost = postRepository.save(privatePost);
    }

    @Test
    @DisplayName("Residual A Negative Proof: Outsider cannot join private community via HTTP/DB, member count unchanged, and private post remains unreadable")
    void outsiderCannotJoinPrivateCommunityAndCannotReadPrivatePost() throws Exception {
        long initialCount = memberRepository.countByCommunityId(privateCommunity.getId());
        long initialEntityCount = privateCommunity.getMemberCount();

        // 1. Outsider attempts to join private community -> 403 Forbidden COMMUNITY_ACCESS_DENIED
        mockMvc.perform(post("/api/communities/" + privateCommunity.getId() + "/join")
                        .with(authentication(outsiderAuth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_ACCESS_DENIED"));

        // 2. Real DB proof: no membership record created for outsider
        boolean membershipExists = memberRepository.existsByCommunityIdAndUserId(privateCommunity.getId(), outsider.getId());
        assertThat(membershipExists).isFalse();

        // 3. Real DB proof: community memberCount unchanged
        Community reloaded = communityRepository.findById(privateCommunity.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(initialEntityCount);
        assertThat(memberRepository.countByCommunityId(privateCommunity.getId())).isEqualTo(initialCount);

        // 4. Outsider cannot read private post (visibility denied)
        assertThat(postService.isPostVisibleTo(privatePost, outsider.getId())).isFalse();

        mockMvc.perform(get("/api/posts/" + privatePost.getId())
                        .with(authentication(outsiderAuth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Residual A Positives: Existing member joining private community is idempotent; outsider can join public community")
    void existingMemberIdempotentAndPublicJoinSuccess() throws Exception {
        // 1. Existing member joins private community -> 200 OK, membership returned, count unchanged
        long beforeCount = memberRepository.countByCommunityId(privateCommunity.getId());
        CommunityMembershipResponse resp = communityService.join(existingMember.getId(), privateCommunity.getId());
        assertThat(resp).isNotNull();
        assertThat(resp.communityId()).isEqualTo(privateCommunity.getId());
        assertThat(memberRepository.countByCommunityId(privateCommunity.getId())).isEqualTo(beforeCount);

        // 2. Outsider joins public community -> 200 OK, membership created, count incremented
        long pubBefore = publicCommunity.getMemberCount();
        CommunityMembershipResponse pubResp = communityService.join(outsider.getId(), publicCommunity.getId());
        assertThat(pubResp).isNotNull();
        assertThat(pubResp.communityId()).isEqualTo(publicCommunity.getId());

        Community pubReloaded = communityRepository.findById(publicCommunity.getId()).orElseThrow();
        assertThat(pubReloaded.getMemberCount()).isEqualTo(pubBefore + 1);
        assertThat(memberRepository.existsByCommunityIdAndUserId(publicCommunity.getId(), outsider.getId())).isTrue();
    }
}
