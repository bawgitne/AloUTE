package com.aloute.integration;

import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-15: profile.postsCount was never advanced (always 0 on live profiles) and
 * community.postCount advanced via read-modify-write (lost updates possible).
 * Both counters now advance atomically with the lifetime-created semantics
 * fixed for community.postCount in PR #73 — deletion does not decrement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class ProfilePostCounterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private EntityManager entityManager;

    private User author;
    private Authentication authorAuth;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = new User();
        author.setUsername("counter_" + suffix);
        author.setEmail("counter_" + suffix + "@aloute.test");
        author.setPasswordHash("$2a$10$placeholder");
        author.setVerified(true);
        author.setActive(true);
        author.setRole(Role.USER);
        author = userRepository.saveAndFlush(author);
        Profile profile = new Profile();
        profile.setUser(author);
        profile.setDisplayName("Counter Author");
        profileRepository.saveAndFlush(profile);
        UserPrincipal principal = UserPrincipal.from(author);
        authorAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("BL-15: creating posts advances profile.postsCount atomically")
    void profileCounterAdvancesOnCreate() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"first\",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isCreated());
        entityManager.clear();

        assertThat(profileRepository.findWithUserByUserIdIn(java.util.List.of(author.getId()))
                .get(0).getPostsCount()).isEqualTo(1);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"second\",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isCreated());
        entityManager.clear();

        assertThat(profileRepository.findWithUserByUserIdIn(java.util.List.of(author.getId()))
                .get(0).getPostsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("BL-15: deleting a post keeps the lifetime count (PR #73 convention)")
    void lifetimeSemanticsOnDelete() throws Exception {
        String created = mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"to delete\",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long postId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created).path("data").path("id").asLong();
        entityManager.clear();

        mockMvc.perform(delete("/api/posts/" + postId)
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.clear();

        assertThat(profileRepository.findWithUserByUserIdIn(java.util.List.of(author.getId()))
                .get(0).getPostsCount()).isEqualTo(1);
    }
}
