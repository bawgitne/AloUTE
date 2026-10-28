package com.aloute.integration;

import com.aloute.entity.Block;
import com.aloute.entity.Conversation;
import com.aloute.entity.ConversationMember;
import com.aloute.entity.ConversationType;
import com.aloute.entity.User;
import com.aloute.repository.BlockRepository;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.repository.ConversationRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-11: a two-way block relationship is a business-permission input (domain §6.2)
 * for messaging, and DIRECT conversations are exactly two participants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class MessagingBlockAndDirectCardinalityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationMemberRepository conversationMemberRepository;
    @Autowired private ObjectMapper objectMapper;

    private User alice;
    private User bob;
    private User carol;
    private Authentication aliceAuth;
    private Authentication bobAuth;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        alice = newUser("msgblk_alice_" + suffix);
        bob = newUser("msgblk_bob_" + suffix);
        carol = newUser("msgblk_carol_" + suffix);
        aliceAuth = authOf(alice);
        bobAuth = authOf(bob);
    }

    private User newUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@aloute.test");
        u.setPasswordHash("$2a$10$placeholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        return userRepository.saveAndFlush(u);
    }

    private Authentication authOf(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void block(User blocker, User blocked) {
        Block b = new Block();
        b.setBlocker(blocker);
        b.setBlocked(blocked);
        blockRepository.saveAndFlush(b);
    }

    private String directBody(Long targetId) {
        return "{\"type\":\"DIRECT\",\"targetUserId\":" + targetId + "}";
    }

    @Test
    @DisplayName("BL-11: blocked user cannot create a DIRECT conversation with their blocker")
    void blockedUserCannotCreateDirectConversation() throws Exception {
        block(bob, alice);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(bob.getId()))
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("BLOCKED_BY_RELATIONSHIP"));
    }

    @Test
    @DisplayName("BL-11: the blocker also cannot open a new DIRECT conversation (two-way)")
    void blockerAlsoCannotCreateDirectConversation() throws Exception {
        block(bob, alice);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(alice.getId()))
                        .with(authentication(bobAuth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("BLOCKED_BY_RELATIONSHIP"));
    }

    @Test
    @DisplayName("BL-11: blocked user cannot send messages into a pre-existing DIRECT conversation")
    void blockedUserCannotSendInExistingConversation() throws Exception {
        Long conversationId = createDirectConversation(alice, bob);
        block(bob, alice);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello after block\"}")
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("BLOCKED_BY_RELATIONSHIP"));
    }

    @Test
    @DisplayName("BL-11: DIRECT conversation with more than two members is rejected with 422")
    void directConversationRejectsThreeMembers() throws Exception {
        String body = "{\"type\":\"DIRECT\",\"memberIds\":["
                + bob.getId() + "," + carol.getId() + "]}";

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a conversation invitation list larger than 200 members is refused before any lookup")
    void oversizedGroupInvitationListIsRefused() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long i = 1; i <= 201; i++) {
            if (i > 1) ids.append(',');
            ids.append(i);
        }
        String body = "{\"type\":\"GROUP\",\"name\":\"Too big\",\"memberIds\":[" + ids + "]}";

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Non-blocked pair can still create a DIRECT conversation and exchange messages")
    void nonBlockedPairStillWorks() throws Exception {
        String created = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(bob.getId()))
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long conversationId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello bob\"}")
                        .with(authentication(aliceAuth))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private Long createDirectConversation(User a, User b) throws Exception {
        String created = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(b.getId()))
                        .with(authentication(authOf(a)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data").path("id").asLong();
    }
}
