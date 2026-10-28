package com.aloute.integration;

import com.aloute.entity.Community;
import com.aloute.entity.CommunityMember;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.MediaAsset;
import com.aloute.entity.MediaPurpose;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.MediaAssetRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-09 regression: community avatar/cover media must belong to the acting user
 * and still exist. Attaching another user's asset, a soft-deleted asset or an
 * unknown id must fail with 404 instead of silently attaching the URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CommunityMediaOwnershipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;

    private User owner;
    private User adminMember;
    private User otherUser;
    private Community community;
    private Authentication ownerAuth;
    private Authentication adminAuth;

    private User createUser(String username) {
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
        p.setDisplayName("Display " + username);
        profileRepository.save(p);
        return u;
    }

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private MediaAsset createMedia(User mediaOwner, MediaPurpose purpose) {
        MediaAsset media = new MediaAsset();
        media.setOwner(mediaOwner);
        media.setType(com.aloute.entity.MediaType.IMAGE);
        media.setPurpose(purpose);
        String path = "media/" + purpose + "/" + System.nanoTime() + ".png";
        media.setUrl("https://storage.test/" + path);
        media.setStoragePath(path);
        media.setMimeType("image/png");
        media.setFileSize(64L);
        return mediaAssetRepository.save(media);
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        owner = createUser("cmo_owner");
        adminMember = createUser("cmo_admin");
        otherUser = createUser("cmo_other");

        ownerAuth = auth(owner);
        adminAuth = auth(adminMember);

        community = new Community();
        community.setName("Media Ownership Comm " + suffix);
        community.setSlug("media-ownership-" + suffix);
        community.setOwner(owner);
        community.setPrivateCommunity(false);
        community.setMemberCount(2L);
        community = communityRepository.save(community);
        memberRepository.save(new CommunityMember(community, owner, CommunityRole.ADMIN));
        memberRepository.save(new CommunityMember(community, adminMember, CommunityRole.ADMIN));
    }

    @Test
    @DisplayName("Create community: another user's media is rejected with 404 and not attached")
    void createRejectsForeignMedia() throws Exception {
        MediaAsset foreign = createMedia(otherUser, MediaPurpose.COMMUNITY_AVATAR);
        long communitiesBefore = communityRepository.count();

        mockMvc.perform(post("/api/communities")
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cmo Comm\",\"slug\":\"cmo-comm-"
                                + System.nanoTime() + "\",\"isPrivate\":false,\"avatarMediaId\":" + foreign.getId() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        // No community row was created by the aborted request
        assertThat(communityRepository.count()).isEqualTo(communitiesBefore);
        MediaAsset reloaded = mediaAssetRepository.findById(foreign.getId()).orElseThrow();
        assertThat(reloaded.getAttachedAt()).isNull();
    }

    @Test
    @DisplayName("Create community: own live media is accepted and stamped attachedAt")
    void createAcceptsOwnMedia() throws Exception {
        MediaAsset mine = createMedia(adminMember, MediaPurpose.COMMUNITY_AVATAR);

        mockMvc.perform(post("/api/communities")
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cmo Own Comm\",\"slug\":\"cmo-own-"
                                + System.nanoTime() + "\",\"isPrivate\":false,\"avatarMediaId\":" + mine.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatarUrl").value(mine.getUrl()));

        assertThat(mediaAssetRepository.findById(mine.getId()).orElseThrow().getAttachedAt()).isNotNull();
    }

    @Test
    @DisplayName("Update community: foreign media and soft-deleted media are rejected; own live media attached")
    void updateMediaOwnershipMatrix() throws Exception {
        MediaAsset foreign = createMedia(otherUser, MediaPurpose.COMMUNITY_COVER);
        MediaAsset mineDeleted = createMedia(owner, MediaPurpose.COMMUNITY_COVER);
        mineDeleted.setDeletedAt(Instant.now());
        mediaAssetRepository.save(mineDeleted);
        MediaAsset mineLive = createMedia(owner, MediaPurpose.COMMUNITY_COVER);

        // Foreign media → 404, existing cover unchanged
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMediaId\":" + foreign.getId() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
        assertThat(communityRepository.findById(community.getId()).orElseThrow().getCoverUrl()).isNull();

        // Own soft-deleted media → 404
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMediaId\":" + mineDeleted.getId() + "}"))
                .andExpect(status().isNotFound());

        // Admin-role member attaching their own live media is still allowed
        MediaAsset adminMedia = createMedia(adminMember, MediaPurpose.COMMUNITY_COVER);
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMediaId\":" + adminMedia.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverUrl").value(adminMedia.getUrl()));

        // Owner attaches own live media → accepted and stamped
        mockMvc.perform(patch("/api/communities/{cid}", community.getId())
                        .with(authentication(ownerAuth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverMediaId\":" + mineLive.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverUrl").value(mineLive.getUrl()));

        assertThat(mediaAssetRepository.findById(mineLive.getId()).orElseThrow().getAttachedAt()).isNotNull();
        assertThat(mediaAssetRepository.findById(adminMedia.getId()).orElseThrow().getAttachedAt()).isNotNull();
    }
}
