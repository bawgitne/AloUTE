package com.aloute.integration;

import com.aloute.entity.MediaAsset;
import com.aloute.entity.MediaPurpose;
import com.aloute.security.authorization.Role;
import com.aloute.entity.User;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.MediaStoragePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class MediaUploadLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;

    /**
     * Test storage boundary: Mocks MediaStoragePort to verify the entire HTTP pipeline,
     * multipart resolution, controller validation, error mapping, and database persistence
     * without requiring external Firebase credentials.
     * NOTE: This explicitly tests the HTTP pipeline and boundary logic; NOT real Firebase bytes.
     */
    @MockBean
    private MediaStoragePort storagePort;

    private User testUser;

    private Authentication auth(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        String unique = "uploader_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
        User u = new User();
        u.setUsername(unique);
        u.setEmail(unique + "@example.com");
        u.setPasswordHash("hashed_pwd");
        u.setActive(true);
        u.setRole(Role.USER);
        u.setAuthVersion(0L);
        u.setVerified(true);
        testUser = userRepository.saveAndFlush(u);

        when(storagePort.store(anyLong(), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return new MediaStoragePort.StoredMedia(
                    "https://storage.example.com/uploads/" + uid + "/stored_test_asset.bin",
                    "uploads/" + uid + "/stored_test_asset.bin"
            );
        });
    }



    @Test
    @DisplayName("Upload Limits: Valid 5 MiB image upload succeeds with 201 Created")
    void validImageWithin5MiB_UploadSucceeds() throws Exception {
        int fiveMiB = 5 * 1024 * 1024;
        byte[] payload = new byte[fiveMiB];

        MockMultipartFile filePart = new MockMultipartFile("file", "photo.png", "image/png", payload);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("IMAGE"))
                .andExpect(jsonPath("$.data.url").value("https://storage.example.com/uploads/" + testUser.getId() + "/stored_test_asset.bin"));
    }

    @Test
    @DisplayName("Upload Limits: Valid 30 MiB video upload succeeds with 201 Created")
    void validVideoWithin30MiB_UploadSucceeds() throws Exception {
        int thirtyMiB = 30 * 1024 * 1024;
        byte[] payload = new byte[thirtyMiB];

        MockMultipartFile filePart = new MockMultipartFile("file", "clip.mp4", "video/mp4", payload);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("VIDEO"))
                .andExpect(jsonPath("$.data.url").value("https://storage.example.com/uploads/" + testUser.getId() + "/stored_test_asset.bin"));
    }

    @Test
    @DisplayName("Upload Limits: Image exceeding 5 MiB by 1 byte is rejected with 422 MEDIA_TOO_LARGE")
    void imageBoundaryPlusOneByte_RejectsCleanly() throws Exception {
        int fiveMiBPlusOne = 5 * 1024 * 1024 + 1;
        byte[] payload = new byte[fiveMiBPlusOne];

        MockMultipartFile filePart = new MockMultipartFile("file", "photo_large.png", "image/png", payload);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_TOO_LARGE"));
    }

    @Test
    @DisplayName("Upload Limits: Video exceeding 30 MiB by 1 byte is rejected with 422 MEDIA_TOO_LARGE")
    void videoBoundaryPlusOneByte_RejectsCleanly() throws Exception {
        int thirtyMiBPlusOne = 30 * 1024 * 1024 + 1;
        byte[] payload = new byte[thirtyMiBPlusOne];

        MockMultipartFile filePart = new MockMultipartFile("file", "clip_large.mp4", "video/mp4", payload);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_TOO_LARGE"));
    }

    @Test
    @DisplayName("Security: Unauthenticated upload is rejected with 401 Unauthorized")
    void unauthenticatedUpload_RejectsWithUnauthorized() throws Exception {
        MockMultipartFile filePart = new MockMultipartFile("file", "photo.png", "image/png", new byte[100]);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Validation: Unsupported MIME type is rejected with 422 MEDIA_TYPE_UNSUPPORTED")
    void unsupportedMimeType_RejectsCleanly() throws Exception {
        MockMultipartFile filePart = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[100]);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_TYPE_UNSUPPORTED"));
    }

    @Test
    @DisplayName("Validation: Empty file upload is rejected with 422 MEDIA_FILE_REQUIRED")
    void emptyFile_RejectsCleanly() throws Exception {
        MockMultipartFile filePart = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_FILE_REQUIRED"));
    }

    @Test
    @DisplayName("Quota: Upload exceeding 150 MiB user quota is rejected with 409 MEDIA_QUOTA_EXCEEDED")
    void quotaExceeded_RejectsCleanly() throws Exception {
        MediaAsset priorAsset = new MediaAsset();
        priorAsset.setOwner(testUser);
        priorAsset.setType(com.aloute.entity.MediaType.VIDEO);
        priorAsset.setPurpose(MediaPurpose.POST);
        priorAsset.setUrl("https://storage.example.com/existing.mp4");
        priorAsset.setStoragePath("uploads/existing.mp4");
        priorAsset.setMimeType("video/mp4");
        priorAsset.setFileSize(148L * 1024L * 1024L);
        mediaAssetRepository.saveAndFlush(priorAsset);

        int fiveMiB = 5 * 1024 * 1024;
        MockMultipartFile filePart = new MockMultipartFile("file", "photo.png", "image/png", new byte[fiveMiB]);
        MockMultipartFile purposePart = new MockMultipartFile("purpose", "", "application/json", "\"POST\"".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(filePart)
                        .file(purposePart)
                        .with(csrf())
                        .with(authentication(auth(testUser))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEDIA_QUOTA_EXCEEDED"));
    }
}
