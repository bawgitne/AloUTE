package com.aloute.integration;

import com.aloute.entity.Profile;
import com.aloute.entity.User;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that POST /api/media binds the multipart {@code purpose} part for both client shapes that exist
 * in the application: the natural text part used by the Settings page and the JSON string part used by the
 * post composer. Before the binding fix the Settings shape failed with 415 UNSUPPORTED_MEDIA_TYPE, which
 * made avatar and cover uploads impossible.
 *
 * <p>Firebase storage is disabled for this suite (no credentials are used), so a bound request ends at the
 * storage port with 503 MEDIA_STORAGE_UNAVAILABLE. Reaching that response is exactly the proof that the
 * multipart part was bound and the request passed validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.firebase.enabled=false"
})
class MediaUploadPurposeBindingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        User user = new User();
        user.setUsername("media_" + suffix);
        user.setEmail("media_" + suffix + "@aloute.test");
        user.setPasswordHash("$2a$10$placeholder");
        user.setVerified(true);
        user.setActive(true);
        user.setRole(Role.USER);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName("Media Binding User");
        profileRepository.save(profile);

        UserPrincipal principal = UserPrincipal.from(user);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("Settings shape: plain text purpose part binds and reaches media storage (no 415)")
    void plainTextPurposeIsAccepted() throws Exception {
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", pngBytes()))
                        .file(new MockMultipartFile("purpose", null, "text/plain",
                                "USER_AVATAR".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("MEDIA_STORAGE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Composer shape: JSON string purpose part still binds and reaches media storage")
    void jsonEncodedPurposeIsAccepted() throws Exception {
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "post.png", "image/png", pngBytes()))
                        .file(new MockMultipartFile("purpose", null, "application/json",
                                "\"POST\"".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("MEDIA_STORAGE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Unknown purpose is rejected with 422 and never reaches media storage")
    void unknownPurposeIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "unknown.png", "image/png", pngBytes()))
                        .file(new MockMultipartFile("purpose", null, "text/plain",
                                "NOT_A_PURPOSE".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MEDIA_PURPOSE_INVALID"));
    }
}
