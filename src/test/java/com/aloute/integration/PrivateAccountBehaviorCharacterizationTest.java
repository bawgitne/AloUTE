package com.aloute.integration;

import com.aloute.entity.Post;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.PostRepository;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-05 characterization: the private-account follow-request lifecycle is a
 * DOCUMENTED domain gap (follow returns 409 PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED),
 * but the privacy itself is enforced — a private author's posts are concealed
 * from guests and strangers at the runtime HTTP layer. These tests lock the
 * current canonical behavior until a product decision lands the full lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PrivateAccountBehaviorCharacterizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;

    private User privateAuthor;
    private User stranger;
    private Authentication strangerAuth;
    private Long privatePostId;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        privateAuthor = newUser("privauthor_" + suffix, true);
        stranger = newUser("privstranger_" + suffix, false);
        strangerAuth = authOf(stranger);

        Post post = new Post();
        post.setUser(privateAuthor);
        post.setContent("Private author content");
        post.setVisibility(PostVisibility.PUBLIC);
        post.setAllowReply(true);
        post.setAllowQuote(true);
        privatePostId = postRepository.save(post).getId();
    }

    private User newUser(String username, boolean privateAccount) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@aloute.test");
        user.setPasswordHash("$2a$10$placeholder");
        user.setVerified(true);
        user.setActive(true);
        user.setRole(Role.USER);
        user.setPrivateAccount(privateAccount);
        user = userRepository.save(user);
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(username);
        profileRepository.save(profile);
        return user;
    }

    private Authentication authOf(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("BL-05: following a private account is the documented 409 gap, not a silent follow")
    void followPrivateAccountReturnsDocumentedGap() throws Exception {
        mockMvc.perform(put("/api/social/follows/" + privateAuthor.getId())
                        .with(authentication(strangerAuth))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("BL-05: private author's post is denied to guest and stranger (403, pre-BL-22 semantics)")
    void privateAuthorPostConcealedAtHttpLayer() throws Exception {
        // CURRENT canonical behavior: direct post reads answer 403 POST_ACCESS_DENIED
        // when isPostVisibleTo is false (the post exists). This is the BL-22
        // existence-disclosure inconsistency versus the 404 concealment policy used
        // for private communities (PR #29/#30); a dedicated fix will move this to 404.
        mockMvc.perform(get("/api/posts/" + privatePostId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));

        mockMvc.perform(get("/api/posts/" + privatePostId).with(authentication(strangerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("BL-05: the author still sees their own private-account post")
    void authorSeesOwnPost() throws Exception {
        Authentication authorAuth = authOf(privateAuthor);
        mockMvc.perform(get("/api/posts/" + privatePostId).with(authentication(authorAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(privatePostId));
    }

    @Test
    @DisplayName("BL-05: follow request is not silently created — stranger stays a stranger")
    void noSilentFollowState() throws Exception {
        mockMvc.perform(put("/api/social/follows/" + privateAuthor.getId())
                        .with(authentication(strangerAuth))
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/posts/" + privatePostId).with(authentication(strangerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
