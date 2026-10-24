package com.aloute.controller.api;

import com.aloute.common.exception.ApiException;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.MediaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Focused tests for the dev-only plain-read media fallback
 * (MediaController#serveLocalFallback, reachable when SecureDirectoryStream is
 * unavailable, e.g. Windows NTFS). Covers: double gate (opt-in property AND dev
 * profile), symlink-file and symlinked-parent-directory escape rejection, and
 * the regular-file happy path.
 */
class MediaControllerLocalFallbackTest {

    private Path testRootDir;
    private Path uploadsDir;
    private Path outsideDir;

    private MediaService mediaService;
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() throws IOException {
        Path targetDir = Paths.get("target").toAbsolutePath().normalize();
        Files.createDirectories(targetDir);
        testRootDir = Files.createDirectories(targetDir.resolve("test-media-fallback-" + System.nanoTime()));
        uploadsDir = Files.createDirectories(testRootDir.resolve("uploads"));
        outsideDir = Files.createDirectories(testRootDir.resolve("outside"));

        mediaService = mock(MediaService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testRootDir != null && Files.exists(testRootDir)) {
            try (var stream = Files.walk(testRootDir)) {
                var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    if (!path.toAbsolutePath().normalize().startsWith(testRootDir.toAbsolutePath().normalize())) {
                        throw new IllegalStateException("Attempted to delete path outside test sandbox: " + path);
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private MediaController controllerWithFallbackEnabled() {
        return new MediaController(mediaService, currentUserProvider, uploadsDir.toString(), true);
    }

    /** Resolves relativeKey exactly the way serveMediaFile does before calling the fallback. */
    private ResponseEntity<Resource> fallbackFor(MediaController controller, String relativeKey) throws IOException {
        Path baseDir = uploadsDir.toAbsolutePath().normalize();
        Path candidate = baseDir.resolve(relativeKey).normalize();
        Path relativePath = baseDir.relativize(candidate);
        return controller.serveLocalFallback(baseDir, candidate, relativePath);
    }

    private void assertThatNotFound(String relativeKey) throws IOException {
        assertThatThrownBy(() -> fallbackFor(controllerWithFallbackEnabled(), relativeKey))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("Fallback gate requires BOTH the opt-in property and the dev profile")
    void fallbackRequiresPropertyAndDevProfile() {
        MockEnvironment env = new MockEnvironment();

        env.setProperty("aloute.media.local-storage", "true");
        env.setActiveProfiles("dev");
        assertThat(new MediaController(mediaService, currentUserProvider, uploadsDir.toString(), true, env)
                .localMediaFallbackActive()).isTrue();

        // Property alone (production profile): must NOT activate — the production fail-closed rule.
        env.setActiveProfiles("prod");
        assertThat(new MediaController(mediaService, currentUserProvider, uploadsDir.toString(), true, env)
                .localMediaFallbackActive()).isFalse();

        // No profile at all: must NOT activate.
        env.setActiveProfiles();
        assertThat(new MediaController(mediaService, currentUserProvider, uploadsDir.toString(), true, env)
                .localMediaFallbackActive()).isFalse();

        // Dev profile but property off: must NOT activate.
        env.setActiveProfiles("dev");
        assertThat(new MediaController(mediaService, currentUserProvider, uploadsDir.toString(), false, env)
                .localMediaFallbackActive()).isFalse();
    }

    @Test
    @DisplayName("Fallback serves a regular file inside the upload root")
    void servesRegularFileInsideRoot() throws Exception {
        Path file = uploadsDir.resolve("users/7/avatar/sample.png");
        Files.createDirectories(file.getParent());
        byte[] content = "fallback-png-bytes".getBytes();
        Files.write(file, content);

        ResponseEntity<Resource> response = fallbackFor(controllerWithFallbackEnabled(), "users/7/avatar/sample.png");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=31536000, immutable");
        try (var in = response.getBody().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("Fallback rejects a regular file that resolves outside the upload root")
    void rejectsFileOutsideRoot() throws Exception {
        Path file = uploadsDir.resolve("nested/sample.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, "inside".getBytes());
        Path outsideSecret = outsideDir.resolve("secret.txt");
        Files.writeString(outsideSecret, "outside-content");

        assertThatNotFound("../outside/secret.txt");
    }

    @Test
    @DisplayName("Fallback rejects a symlinked file pointing outside the upload root")
    void rejectsSymlinkedFile() throws Exception {
        Path outsideSecret = outsideDir.resolve("secret.txt");
        Files.writeString(outsideSecret, "outside-content");

        Path link = uploadsDir.resolve("link-out.txt");
        try {
            Files.createSymbolicLink(link, outsideSecret);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        assertThatNotFound("link-out.txt");
    }

    @Test
    @DisplayName("Fallback rejects reads through a symlinked parent directory")
    void rejectsSymlinkedParentDirectory() throws Exception {
        Path outsideFile = outsideDir.resolve("child.txt");
        Files.writeString(outsideFile, "outside-content");

        Path linkDir = uploadsDir.resolve("users-link");
        try {
            Files.createSymbolicLink(linkDir, outsideDir);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        assertThatNotFound("users-link/child.txt");
    }

    @Test
    @DisplayName("Fallback rejects directories, missing files and the root itself")
    void rejectsNonRegularTargets() throws Exception {
        Files.createDirectories(uploadsDir.resolve("adir"));

        assertThatNotFound("adir");
        assertThatNotFound("missing.png");
        assertThatNotFound(".");
    }

    @Test
    @DisplayName("serveMediaFile wires the fallback only when SecureDirectoryStream is unavailable")
    void serveMediaFileUsesFallbackWhenSecureStreamUnsupported() throws Exception {
        Assumptions.assumeFalse(isSecureDirectoryStreamSupported(uploadsDir),
                "SecureDirectoryStream supported on this filesystem; the fallback branch is unreachable by design");

        Path file = uploadsDir.resolve("sample.png");
        byte[] content = "wired-fallback-bytes".getBytes();
        Files.write(file, content);

        var controller = controllerWithFallbackEnabled();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/sample.png");
        ResponseEntity<Resource> response = controller.serveMediaFile(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        try (var in = response.getBody().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    private boolean isSecureDirectoryStreamSupported(Path dir) {
        try (var ds = Files.newDirectoryStream(dir)) {
            return ds instanceof java.nio.file.SecureDirectoryStream;
        } catch (IOException e) {
            return false;
        }
    }
}
