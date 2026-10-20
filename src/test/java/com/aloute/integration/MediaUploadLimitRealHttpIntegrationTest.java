package com.aloute.integration;

import com.aloute.entity.User;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.MediaStoragePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class MediaUploadLimitRealHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private MediaStoragePort storagePort;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        String unique = "realhttp_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
        User u = new User();
        u.setUsername(unique);
        u.setEmail(unique + "@example.com");
        u.setPasswordHash("hashed_pwd");
        u.setActive(true);
        u.setRole(com.aloute.security.authorization.Role.USER);
        u.setAuthVersion(0L);
        u.setVerified(true);
        testUser = userRepository.saveAndFlush(u);

        UserPrincipal principal = UserPrincipal.from(testUser);
        jwtToken = jwtTokenProvider.generate(principal);

        when(storagePort.store(anyLong(), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return new MediaStoragePort.StoredMedia(
                    "https://storage.example.com/uploads/" + uid + "/realhttp_asset.bin",
                    "uploads/" + uid + "/realhttp_asset.bin"
            );
        });
    }

    @AfterEach
    void tearDown() {
        if (testUser != null && testUser.getId() != null) {
            try {
                mediaAssetRepository.findAll().stream()
                        .filter(m -> m.getOwner() != null && testUser.getId().equals(m.getOwner().getId()))
                        .forEach(mediaAssetRepository::delete);
            } finally {
                userRepository.deleteById(testUser.getId());
            }
        }
    }

    private ResponseEntity<String> sendMultipartUpload(byte[] payload, String filename, String mimeType) {
        ByteArrayResource fileResource = new ByteArrayResource(payload) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.valueOf(mimeType));
        HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(fileResource, fileHeaders);

        HttpHeaders purposeHeaders = new HttpHeaders();
        purposeHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> purposeEntity = new HttpEntity<>("\"POST\"", purposeHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileEntity);
        body.add("purpose", purposeEntity);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwtToken);
        headers.set("X-Requested-With", "XMLHttpRequest");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/media",
                requestEntity,
                String.class
        );
    }

    @Test
    @DisplayName("Real HTTP: Valid 5 MiB image upload succeeds through socket with 201 Created and HTTPS url")
    void realHttp5MiBImageUpload_Succeeds() {
        int fiveMiB = 5 * 1024 * 1024;
        byte[] payload = new byte[fiveMiB];

        ResponseEntity<String> response = sendMultipartUpload(payload, "photo.png", "image/png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(response.getBody()).contains("\"type\":\"IMAGE\"");
        assertThat(response.getBody()).contains("https://storage.example.com/uploads/" + testUser.getId() + "/realhttp_asset.bin");
    }

    @Test
    @DisplayName("Real HTTP: Image exceeding 5 MiB by 1 byte rejected cleanly with 422 MEDIA_TOO_LARGE")
    void realHttpImage5MiBPlusOneByte_RejectsCleanly() {
        int fiveMiBPlusOne = 5 * 1024 * 1024 + 1;
        byte[] payload = new byte[fiveMiBPlusOne];

        ResponseEntity<String> response = sendMultipartUpload(payload, "photo_large.png", "image/png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("\"MEDIA_TOO_LARGE\"");
        assertThat(response.getBody()).doesNotContain("Exception");
        assertThat(response.getBody()).doesNotContain("at com.");
    }

    @Test
    @DisplayName("Real HTTP: Valid 30 MiB video upload succeeds through socket with 201 Created and HTTPS url")
    void realHttp30MiBVideoUpload_Succeeds() {
        int thirtyMiB = 30 * 1024 * 1024;
        byte[] payload = new byte[thirtyMiB];

        ResponseEntity<String> response = sendMultipartUpload(payload, "clip.mp4", "video/mp4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(response.getBody()).contains("\"type\":\"VIDEO\"");
        assertThat(response.getBody()).contains("https://storage.example.com/uploads/" + testUser.getId() + "/realhttp_asset.bin");
    }

    @Test
    @DisplayName("Real HTTP: Video exceeding 30 MiB by 1 byte rejected cleanly with 422 MEDIA_TOO_LARGE")
    void realHttpVideo30MiBPlusOneByte_RejectsCleanly() {
        int thirtyMiBPlusOne = 30 * 1024 * 1024 + 1;
        byte[] payload = new byte[thirtyMiBPlusOne];

        ResponseEntity<String> response = sendMultipartUpload(payload, "clip_large.mp4", "video/mp4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("\"MEDIA_TOO_LARGE\"");
        assertThat(response.getBody()).doesNotContain("Exception");
        assertThat(response.getBody()).doesNotContain("at com.");
    }

    @Test
    @DisplayName("Real HTTP: Upload exceeding 35 MB multipart limit rejected cleanly with 422 MEDIA_TOO_LARGE without reset")
    void realHttpMultipartOversize_RejectsCleanlyWithoutReset() {
        int thirtySixMiB = 36 * 1024 * 1024;
        byte[] payload = new byte[thirtySixMiB];

        ResponseEntity<String> response = sendMultipartUpload(payload, "giant.mp4", "video/mp4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("\"MEDIA_TOO_LARGE\"");
        assertThat(response.getBody()).doesNotContain("Exception");
        assertThat(response.getBody()).doesNotContain("at com.");
    }
}
