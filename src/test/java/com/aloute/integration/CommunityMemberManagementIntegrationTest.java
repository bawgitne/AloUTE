package com.aloute.integration;

import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.CommunityResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Community member management: search, role changes (owner-only policy),
 * member removal matrix, owner protection and the approval settings gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityMemberManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CommunityService communityService;
    @Autowired private PostService postService;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;

    private User owner;
    private User adminMember;
    private User memberOne;   // displayName contains "Nguyen Van An"
    private User memberTwo;   // username contains "tran thi bui"
    private Community community;

    private Authentication ownerAuth;
    private Authentication adminAuth;
    private Authentication memberOneAuth;
    private Authentication memberTwoAuth;

    private User createUser(String username, String displayName) {
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
        p.setDisplayName(displayName);
        profileRepository.save(p);
        return u;
    }

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        owner = createUser("cmm_owner", "Chu Cong Dong");
        adminMember = createUser("cmm_admin", "Quan Tri Vien");
        memberOne = createUser("an_nguyenvan", "Nguyen Van An");
        memberTwo = createUser("tran_thibui", "Bui Thi Tran");

        ownerAuth = auth(owner);
        adminAuth = auth(adminMember);
        memberOneAuth = auth(memberOne);
        memberTwoAuth = auth(memberTwo);

        community = new Community();
        community.setName("Member Mgmt Comm " + suffix);
        community.setSlug("member-mgmt-" + suffix);
        community.setOwner(owner);
        community.setPrivateCommunity(false);
        community.setMemberCount(4L);
        community = communityRepository.save(community);
        memberRepository.save(new CommunityMember(community, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(community, adminMember, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(community, memberOne, CommunityRole.MEMBER));
        memberRepository.save(new CommunityMember(community, memberTwo, CommunityRole.MEMBER));
    }

    private void publishPost(User author) {
        Post post = new Post();
        post.setUser(author);
        post.setContent("Published by " + author.getUsername());
        post.setVisibility(PostVisibility.PUBLIC);
        post.setCommunity(community);
        post.setModerationStatus(PostModerationStatus.APPROVED);
        postRepository.save(post);
    }

    @Test
    @DisplayName("Member listing: paginated + keyword search on username and display name")
    void memberSearch() {
        var all = communityService.members(community.getId(), null, 0, 20, owner.getId()).getData();
        assertThat(all).hasSize(4);

        var byUsername = communityService.members(community.getId(), "an_nguyenvan", 0, 20, owner.getId()).getData();
        assertThat(byUsername).extracting(m -> m.user().id()).containsExactly(memberOne.getId());

        var byDisplayName = communityService.members(community.getId(), "nguyen van", 0, 20, owner.getId()).getData();
        assertThat(byDisplayName).extracting(m -> m.user().id()).containsExactly(memberOne.getId());

        var none = communityService.members(community.getId(), "khong-ton-tai", 0, 20, owner.getId()).getData();
        assertThat(none).isEmpty();
    }

    @Test
    @DisplayName("Role changes are owner-only: admin cannot promote, owner can promote/demote, owner row immutable")
    void roleChangeMatrix() throws Exception {
        // ADMIN tries to promote a member → forbidden (no privilege escalation)
        mockMvc.perform(patch("/api/communities/{cid}/members/{uid}/role", community.getId(), memberOne.getId())
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        assertThat(memberRepository.findByCommunityIdAndUserId(community.getId(), memberOne.getId())
                .orElseThrow().getRole()).isEqualTo(CommunityRole.MEMBER);

        // Owner promotes to ADMIN
        mockMvc.perform(patch("/api/communities/{cid}/members/{uid}/role", community.getId(), memberOne.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // Owner demotes back to MEMBER
        mockMvc.perform(patch("/api/communities/{cid}/members/{uid}/role", community.getId(), memberOne.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        // Owner's own membership row cannot be changed through this endpoint
        mockMvc.perform(patch("/api/communities/{cid}/members/{uid}/role", community.getId(), owner.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());
        assertThat(memberRepository.findByCommunityIdAndUserId(community.getId(), owner.getId())
                .orElseThrow().getRole()).isEqualTo(CommunityRole.ADMIN);
    }

    @Test
    @DisplayName("Member removal matrix: admin removes member; admin cannot remove admin; owner can; owner protected")
    void memberRemovalMatrix() throws Exception {
        // Ordinary member cannot remove anyone
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), memberTwo.getId())
                        .with(authentication(memberOneAuth)).with(csrf()))
                .andExpect(status().isForbidden());

        // Admin removes an ordinary member
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), memberTwo.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(memberRepository.findByCommunityIdAndUserId(community.getId(), memberTwo.getId())).isEmpty();
        assertThat(communityRepository.findById(community.getId()).orElseThrow().getMemberCount()).isEqualTo(3L);

        // Admin cannot remove another admin
        User secondAdmin = createUser("cmm_admin2", "Quan Tri Hai");
        memberRepository.save(new CommunityMember(community, secondAdmin, CommunityRole.ADMIN));
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), secondAdmin.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isForbidden());

        // Owner removes an admin — allowed
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), secondAdmin.getId())
                        .with(authentication(ownerAuth)).with(csrf()))
                .andExpect(status().isNoContent());

        // Nobody can remove the owner
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), owner.getId())
                        .with(authentication(adminAuth)).with(csrf()))
                .andExpect(status().isForbidden());

        // Self-removal must use the leave flow
        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), memberOne.getId())
                        .with(authentication(memberOneAuth)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Removed member's published posts stay visible; community keeps working")
    void removedMemberPostsRemain() throws Exception {
        publishPost(memberOne);
        long publishedBefore = postRepository.countPublishedByCommunityId(community.getId());

        mockMvc.perform(delete("/api/communities/{cid}/members/{uid}", community.getId(), memberOne.getId())
                        .with(authentication(ownerAuth)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(postRepository.countPublishedByCommunityId(community.getId())).isEqualTo(publishedBefore);
        assertThat(postService.communityPosts(community.getId(), 0, 20, owner.getId()).getData()).isNotEmpty();
    }

    @Test
    @DisplayName("Approval setting: owner-only toggle, reflected in response; admin/member changes rejected")
    void approvalSettingsGate() throws Exception {
        CommunityResponse before = communityService.get(community.getId(), owner.getId());
        assertThat(before.requirePostApproval()).isFalse();
        assertThat(before.canManage()).isTrue();

        // Admin cannot toggle the approval policy
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirePostApproval\":true}"))
                .andExpect(status().isForbidden());
        assertThat(communityRepository.findById(community.getId()).orElseThrow().isRequirePostApproval()).isFalse();

        // Ordinary member cannot either (already forbidden from update entirely)
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(memberOneAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirePostApproval\":true}"))
                .andExpect(status().isForbidden());

        // Owner toggles it on
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirePostApproval\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirePostApproval").value(true));

        // Non-manager viewers never see pending counts
        CommunityResponse memberView = communityService.get(community.getId(), memberOne.getId());
        assertThat(memberView.canManage()).isFalse();
        assertThat(memberView.pendingPostCount()).isZero();
    }

    @Test
    @DisplayName("Members management page renders for owner and admin, denied for member")
    void manageMembersPageAuthorization() throws Exception {
        mockMvc.perform(get("/communities/{cid}/manage/members", community.getId()).with(authentication(ownerAuth)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/communities/{cid}/manage/members", community.getId()).with(authentication(adminAuth)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/communities/{cid}/manage/members", community.getId()).with(authentication(memberOneAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guest is redirected to login from the management dashboard")
    void guestRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/communities/{cid}/manage", community.getId()))
                .andExpect(status().is3xxRedirection());
    }
}
