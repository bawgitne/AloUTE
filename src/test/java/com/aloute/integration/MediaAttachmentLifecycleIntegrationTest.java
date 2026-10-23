package com.aloute.integration;

import com.aloute.entity.MediaAsset;
import com.aloute.entity.MediaPurpose;
import com.aloute.entity.PostMedia;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.PostMediaRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Media lifecycle guards around post and profile attachment:
 * - a post may only reference the author's own, non-deleted media (no IDOR via mediaIds);
 * - attaching marks the media so DELETE /api/media can no longer remove the storage binary
 *   while a post or profile still references it;
 * - an avatar set on the profile stays readable on subsequent profile reads (refresh/re-login);
 * - a multipart upload without the required purpose part is a client error, not a 500.
 *
 * Firebase storage is disabled, so media rows are seeded directly into the database to represent
 * completed uploads; the storage port is never exercised in this suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.firebase.enabled=false"
})
class MediaAttachmentLifecycleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostMediaRepository postMediaRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private User author;
    private User other;
    private Authentication authorAuth;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        author = tx.execute(status -> createUser("attach_owner"));
        other = tx.execute(status -> createUser("attach_other"));
        UserPrincipal principal = UserPrincipal.from(author);
        authorAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private User createUser(String name) {
        User u = new User();
        u.setUsername(name + "_" + System.nanoTime());
        u.setEmail(name + "_" + System.nanoTime() + "@aloute.test");
        u.setPasswordHash("$2a$10$placeholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.save(u);
        Profile profile = new Profile();
        profile.setUser(u);
        profile.setDisplayName("Attach " + name);
        profileRepository.save(profile);
        return u;
    }

    private MediaAsset seedMedia(User owner, MediaPurpose purpose, String path) {
        MediaAsset media = new MediaAsset();
        media.setOwner(owner);
        media.setType(com.aloute.entity.MediaType.IMAGE);
        media.setPurpose(purpose);
        media.setUrl("https://storage.test/" + path);
        media.setStoragePath(path);
        media.setMimeType("image/png");
        media.setFileSize(64L);
        return mediaAssetRepository.save(media);
    }

    private String postBody(Long mediaId) {
        return "{\"content\":\"Post kem media lifecycle\",\"visibility\":\"PUBLIC\",\"mediaIds\":[" + mediaId + "]}";
    }

    @Test
    @DisplayName("Post cannot attach another user's media (no IDOR through mediaIds)")
    void postCannotAttachForeignMedia() throws Exception {
        MediaAsset foreign = seedMedia(other, MediaPurpose.POST, "users/other/posts/foreign.png");

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(foreign.getId()))
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        assertThat(postMediaRepository.findAll())
                .noneMatch(pm -> pm.getMedia().getId().equals(foreign.getId()));
    }

    @Test
    @DisplayName("Post attach marks media; DELETE /api/media then answers 409 instead of removing the file")
    void attachedMediaIsProtectedFromDelete() throws Exception {
        MediaAsset own = seedMedia(author, MediaPurpose.POST, "users/author/posts/own.png");

        MvcResultHolder created = createPost(own.getId());
        MediaAsset reloaded = mediaAssetRepository.findById(own.getId()).orElseThrow();
        assertThat(reloaded.getAttachedAt()).isNotNull();
        PostMedia link = postMediaRepository.findAll().stream()
                .filter(pm -> pm.getMedia().getId().equals(own.getId()))
                .findFirst().orElseThrow();
        assertThat(link.getPost().getId()).isEqualTo(created.postId);

        mockMvc.perform(delete("/api/media/{mediaId}", own.getId())
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEDIA_ALREADY_ATTACHED"));

        // The storage binary reference survives: the post still points at a live media row.
        assertThat(mediaAssetRepository.findById(own.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Deleted media cannot be attached to a post afterwards")
    void deletedMediaCannotBeAttached() throws Exception {
        MediaAsset own = seedMedia(author, MediaPurpose.POST, "users/author/posts/deleted.png");
        own.setDeletedAt(java.time.Instant.now());
        mediaAssetRepository.save(own);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(own.getId()))
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Avatar set from own media persists on later profile reads (refresh/re-login)")
    void avatarPersistsOnProfileReads() throws Exception {
        MediaAsset avatar = seedMedia(author, MediaPurpose.USER_AVATAR, "users/author/avatar/me.png");

        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarMediaId\":" + avatar.getId() + "}")
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://storage.test/users/author/avatar/me.png"));

        // Fresh reads (what a refresh or a re-login load does) keep returning the avatar.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/users/me/profile")
                            .with(authentication(authorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatarUrl").value("https://storage.test/users/author/avatar/me.png"));
        }
        assertThat(mediaAssetRepository.findById(avatar.getId()).orElseThrow().getAttachedAt()).isNotNull();
    }

    @Test
    @DisplayName("Multipart upload without the required purpose part is 400, not 500")
    void missingPurposePartIsClientError() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private record MvcResultHolder(long postId) {}

    private MvcResultHolder createPost(Long mediaId) throws Exception {
        String body = mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(mediaId))
                        .with(authentication(authorAuth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return new MvcResultHolder(node.path("data").path("id").asLong());
    }
}
