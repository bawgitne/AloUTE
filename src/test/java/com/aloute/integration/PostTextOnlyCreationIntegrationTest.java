package com.aloute.integration;

import com.aloute.entity.Post;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 regression guard: a post must be publishable with text content only, without attaching any media.
 * The create request deliberately omits {@code mediaIds}; the response, the database row and the public
 * feed must all reflect a valid text-only post.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PostTextOnlyCreationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ObjectMapper objectMapper;

    private Authentication auth;
    private User author;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = new User();
        author.setUsername("poster_" + suffix);
        author.setEmail("poster_" + suffix + "@aloute.test");
        author.setPasswordHash("$2a$10$placeholder");
        author.setVerified(true);
        author.setActive(true);
        author.setRole(Role.USER);
        author = userRepository.save(author);

        Profile profile = new Profile();
        profile.setUser(author);
        profile.setDisplayName("Text Only Poster");
        profileRepository.save(profile);

        UserPrincipal principal = UserPrincipal.from(author);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private long createPost(String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.media").isEmpty())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    @Test
    @DisplayName("P0: text-only post is created, persisted and visible again on re-read")
    void textOnlyPostIsCreatedAndPersisted() throws Exception {
        long postId = createPost("Bai viet chi co van ban, khong dinh kem media");

        // Persisted in the database with no media attachment.
        Post stored = postRepository.findById(postId).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("Bai viet chi co van ban, khong dinh kem media");
        assertThat(stored.getUser().getId()).isEqualTo(author.getId());
        assertThat(stored.getDeletedAt()).isNull();

        // Survives a fresh read through the API (no logout/login needed for the same principal).
        mockMvc.perform(get("/api/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId))
                .andExpect(jsonPath("$.data.content").value("Bai viet chi co van ban, khong dinh kem media"))
                .andExpect(jsonPath("$.data.media").isEmpty());

        // Appears in the public feed listing.
        MvcResult feedResult = mockMvc.perform(get("/api/posts").param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        feed.path("data").forEach(node -> ids.add(node.path("id").asLong()));
        assertThat(ids).contains(postId);
    }

    @Test
    @DisplayName("Vietnamese diacritics and emoji are stored and returned unchanged")
    void unicodeAndEmojiContentIsPreserved() throws Exception {
        String content = "Kiểm thử tiếng Việt: đăng bài không ảnh 🎓📚 — đạt yêu cầu";
        long postId = createPost(content);

        assertThat(postRepository.findById(postId).orElseThrow().getContent()).isEqualTo(content);
        mockMvc.perform(get("/api/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(content));
    }

    @Test
    @DisplayName("Blank content is still rejected with 422 (media absence does not bypass content validation)")
    void blankContentIsRejected() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Content between the web-composer 2000-char UI limit and the 4000-char contract limit is accepted")
    void contentAboveComposerLimitButWithinContractIsAccepted() throws Exception {
        String content = "x".repeat(2500);
        long postId = createPost(content);
        assertThat(postRepository.findById(postId).orElseThrow().getContent()).hasSize(2500);
    }

    @Test
    @DisplayName("Content beyond the 4000-char contract limit is rejected with 422")
    void contentBeyondContractLimitIsRejected() throws Exception {
        String content = "x".repeat(4001);
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"visibility\":\"PUBLIC\"}")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
