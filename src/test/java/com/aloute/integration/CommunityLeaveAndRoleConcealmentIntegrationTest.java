package com.aloute.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aloute.dto.request.UpdateCommunityRoleRequest;
import com.aloute.entity.Community;
import com.aloute.entity.CommunityMember;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityLeaveAndRoleConcealmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ObjectMapper objectMapper;

    private User owner;
    private User admin;
    private User member;
    private User outsider;
    private User targetUser;

    private Community privateCommunity;
    private Community publicCommunity;
    private Community deletedCommunity;
    private final Long nonexistentCommunityId = 999_999_999L;

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

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
        admin = createUser("admin_" + suffix, "admin_" + suffix + "@aloute.test");
        member = createUser("member_" + suffix, "member_" + suffix + "@aloute.test");
        outsider = createUser("outsider_" + suffix, "outsider_" + suffix + "@aloute.test");
        targetUser = createUser("target_" + suffix, "target_" + suffix + "@aloute.test");

        // 1. Private community with owner, admin, member, targetUser
        privateCommunity = new Community();
        privateCommunity.setName("Private Comm " + suffix);
        privateCommunity.setSlug("private-comm-" + suffix);
        privateCommunity.setOwner(owner);
        privateCommunity.setPrivateCommunity(true);
        privateCommunity.setMemberCount(4L);
        privateCommunity = communityRepository.save(privateCommunity);

        memberRepository.save(new CommunityMember(privateCommunity, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(privateCommunity, admin, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(privateCommunity, member, CommunityRole.MEMBER));
        memberRepository.save(new CommunityMember(privateCommunity, targetUser, CommunityRole.MEMBER));

        // 2. Public community with owner, admin, member, targetUser
        publicCommunity = new Community();
        publicCommunity.setName("Public Comm " + suffix);
        publicCommunity.setSlug("public-comm-" + suffix);
        publicCommunity.setOwner(owner);
        publicCommunity.setPrivateCommunity(false);
        publicCommunity.setMemberCount(4L);
        publicCommunity = communityRepository.save(publicCommunity);

        memberRepository.save(new CommunityMember(publicCommunity, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(publicCommunity, admin, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(publicCommunity, member, CommunityRole.MEMBER));
        memberRepository.save(new CommunityMember(publicCommunity, targetUser, CommunityRole.MEMBER));

        // 3. Soft-deleted community
        deletedCommunity = new Community();
        deletedCommunity.setName("Deleted Comm " + suffix);
        deletedCommunity.setSlug("deleted-comm-" + suffix);
        deletedCommunity.setOwner(owner);
        deletedCommunity.setPrivateCommunity(true);
        deletedCommunity.setMemberCount(1L);
        deletedCommunity.setDeletedAt(Instant.now());
        deletedCommunity = communityRepository.save(deletedCommunity);
        memberRepository.save(new CommunityMember(deletedCommunity, owner, CommunityRole.ADMIN));
    }

    // =========================================================================
    // LEAVE ENDPOINT (DELETE /api/communities/{id}/members/me)
    // =========================================================================

    @Test
    @DisplayName("D-02 Leave: Outsider receives identical 404 COMMUNITY_NOT_FOUND as nonexistent and deleted communities")
    void privateCommunityLeaveConcealedForOutsiderMatchingNonexistentAndDeleted() throws Exception {
        long initialCount = privateCommunity.getMemberCount();

        // 1. Nonexistent community leave
        MvcResult nonexistentResult = mockMvc.perform(delete("/api/communities/" + nonexistentCommunityId + "/members/me")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // 2. Deleted community leave
        MvcResult deletedResult = mockMvc.perform(delete("/api/communities/" + deletedCommunity.getId() + "/members/me")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // 3. Private community leave for outsider
        MvcResult privateOutsiderResult = mockMvc.perform(delete("/api/communities/" + privateCommunity.getId() + "/members/me")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // Exact payload comparison: error code and message are identical
        JsonNode nonexistentJson = objectMapper.readTree(nonexistentResult.getResponse().getContentAsString());
        JsonNode deletedJson = objectMapper.readTree(deletedResult.getResponse().getContentAsString());
        JsonNode privateJson = objectMapper.readTree(privateOutsiderResult.getResponse().getContentAsString());

        assertThat(privateJson.get("error").get("code").asText())
                .isEqualTo(nonexistentJson.get("error").get("code").asText())
                .isEqualTo(deletedJson.get("error").get("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");

        assertThat(privateJson.get("error").get("message").asText())
                .isEqualTo(nonexistentJson.get("error").get("message").asText())
                .isEqualTo(deletedJson.get("error").get("message").asText())
                .isEqualTo("Community not found");

        // DB state invariant: outsider membership still does not exist, memberCount unchanged
        assertThat(memberRepository.existsByCommunityIdAndUserId(privateCommunity.getId(), outsider.getId())).isFalse();
        Community reloaded = communityRepository.findById(privateCommunity.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("D-02 Leave: Public community outsider receives 404 COMMUNITY_MEMBER_NOT_FOUND (not COMMUNITY_NOT_FOUND)")
    void publicCommunityOutsiderLeaveReturnsMemberNotFound() throws Exception {
        mockMvc.perform(delete("/api/communities/" + publicCommunity.getId() + "/members/me")
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Not a member"));
    }

    @Test
    @DisplayName("D-02 Leave: Authorized regular member can leave private community (204 No Content, count decremented)")
    void privateCommunityMemberCanLeaveSuccessfully() throws Exception {
        long initialCount = privateCommunity.getMemberCount();

        mockMvc.perform(delete("/api/communities/" + privateCommunity.getId() + "/members/me")
                        .with(authentication(auth(member)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // DB proof: membership deleted, memberCount decremented
        assertThat(memberRepository.existsByCommunityIdAndUserId(privateCommunity.getId(), member.getId())).isFalse();
        Community reloaded = communityRepository.findById(privateCommunity.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(initialCount - 1);
    }

    @Test
    @DisplayName("D-02 Leave: Authorized regular member can leave public community")
    void publicCommunityMemberCanLeaveSuccessfully() throws Exception {
        long initialCount = publicCommunity.getMemberCount();

        mockMvc.perform(delete("/api/communities/" + publicCommunity.getId() + "/members/me")
                        .with(authentication(auth(member)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.existsByCommunityIdAndUserId(publicCommunity.getId(), member.getId())).isFalse();
        Community reloaded = communityRepository.findById(publicCommunity.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(initialCount - 1);
    }

    @Test
    @DisplayName("D-02 Leave: Owner cannot leave private or public community (409 Conflict OWNER_CANNOT_LEAVE)")
    void ownerCannotLeavePrivateOrPublicCommunity() throws Exception {
        // Private community
        mockMvc.perform(delete("/api/communities/" + privateCommunity.getId() + "/members/me")
                        .with(authentication(auth(owner)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_CANNOT_LEAVE"));

        // Public community
        mockMvc.perform(delete("/api/communities/" + publicCommunity.getId() + "/members/me")
                        .with(authentication(auth(owner)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_CANNOT_LEAVE"));

        assertThat(memberRepository.existsByCommunityIdAndUserId(privateCommunity.getId(), owner.getId())).isTrue();
        assertThat(memberRepository.existsByCommunityIdAndUserId(publicCommunity.getId(), owner.getId())).isTrue();
    }

    // =========================================================================
    // ROLE-CHANGE ENDPOINT (PATCH /api/communities/{id}/members/{userId}/role)
    // =========================================================================

    @Test
    @DisplayName("D-02 Role: Outsider receives identical 404 COMMUNITY_NOT_FOUND as nonexistent and deleted communities")
    void privateCommunityRoleChangeConcealedForOutsiderMatchingNonexistentAndDeleted() throws Exception {
        String requestJson = "{\"role\": \"ADMIN\"}";

        // 1. Nonexistent community role change
        MvcResult nonexistentResult = mockMvc.perform(patch("/api/communities/" + nonexistentCommunityId + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // 2. Deleted community role change
        MvcResult deletedResult = mockMvc.perform(patch("/api/communities/" + deletedCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // 3. Private community role change for outsider
        MvcResult privateOutsiderResult = mockMvc.perform(patch("/api/communities/" + privateCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Community not found"))
                .andReturn();

        // Exact payload comparison: error code and message are identical
        JsonNode nonexistentJson = objectMapper.readTree(nonexistentResult.getResponse().getContentAsString());
        JsonNode deletedJson = objectMapper.readTree(deletedResult.getResponse().getContentAsString());
        JsonNode privateJson = objectMapper.readTree(privateOutsiderResult.getResponse().getContentAsString());

        assertThat(privateJson.get("error").get("code").asText())
                .isEqualTo(nonexistentJson.get("error").get("code").asText())
                .isEqualTo(deletedJson.get("error").get("code").asText())
                .isEqualTo("COMMUNITY_NOT_FOUND");

        assertThat(privateJson.get("error").get("message").asText())
                .isEqualTo(nonexistentJson.get("error").get("message").asText())
                .isEqualTo(deletedJson.get("error").get("message").asText())
                .isEqualTo("Community not found");

        // DB state invariant: target user's role is unchanged
        CommunityMember target = memberRepository.findByCommunityIdAndUserId(privateCommunity.getId(), targetUser.getId()).orElseThrow();
        assertThat(target.getRole()).isEqualTo(CommunityRole.MEMBER);
    }

    @Test
    @DisplayName("D-02 Role: Public community outsider receives 403 FORBIDDEN (You are not a member)")
    void publicCommunityOutsiderRoleChangeReturnsForbidden() throws Exception {
        String requestJson = "{\"role\": \"ADMIN\"}";

        mockMvc.perform(patch("/api/communities/" + publicCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(outsider)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("You are not a member"));
    }

    @Test
    @DisplayName("D-02 Role: Regular member cannot change roles (403 FORBIDDEN Only admin or owner can change roles)")
    void privateCommunityRegularMemberRoleChangeReturnsForbidden() throws Exception {
        String requestJson = "{\"role\": \"ADMIN\"}";

        mockMvc.perform(patch("/api/communities/" + privateCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(member)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Only admin or owner can change roles"));
    }

    @Test
    @DisplayName("D-02 Role: Admin can update member role in private community (200 OK)")
    void privateCommunityAdminCanUpdateMemberRole() throws Exception {
        String requestJson = "{\"role\": \"MODERATOR\"}";

        mockMvc.perform(patch("/api/communities/" + privateCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(admin)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));

        CommunityMember updated = memberRepository.findByCommunityIdAndUserId(privateCommunity.getId(), targetUser.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(CommunityRole.MODERATOR);
    }

    @Test
    @DisplayName("D-02 Role: Owner can update member role in private community (200 OK)")
    void privateCommunityOwnerCanUpdateMemberRole() throws Exception {
        String requestJson = "{\"role\": \"ADMIN\"}";

        mockMvc.perform(patch("/api/communities/" + privateCommunity.getId() + "/members/" + targetUser.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(owner)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        CommunityMember updated = memberRepository.findByCommunityIdAndUserId(privateCommunity.getId(), targetUser.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(CommunityRole.ADMIN);
    }

    @Test
    @DisplayName("D-02 Role: Admin updating role for non-member target returns 404 COMMUNITY_MEMBER_NOT_FOUND")
    void privateCommunityAdminTargetNonMemberReturnsMemberNotFound() throws Exception {
        String requestJson = "{\"role\": \"MODERATOR\"}";

        mockMvc.perform(patch("/api/communities/" + privateCommunity.getId() + "/members/" + outsider.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .with(authentication(auth(admin)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Target user is not a member"));
    }
}
