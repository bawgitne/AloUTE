package com.aloute.controller.api;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.GlobalExceptionHandler;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.MediaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaControllerPathConfinementTest {

    private Path testRootDir;
    private Path tempUploadsDir;
    private Path outsideDir;

    private MediaService mediaService;
    private CurrentUserProvider currentUserProvider;
    private MediaController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        Path targetDir = Paths.get("target").toAbsolutePath().normalize();
        Files.createDirectories(targetDir);
        testRootDir = Files.createDirectories(targetDir.resolve("test-media-sandbox-" + System.nanoTime()));
        tempUploadsDir = Files.createDirectories(testRootDir.resolve("uploads"));
        outsideDir = Files.createDirectories(testRootDir.resolve("outside"));

        mediaService = mock(MediaService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new MediaController(mediaService, currentUserProvider, tempUploadsDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testRootDir != null && Files.exists(testRootDir)) {
            Path normalizedRoot = testRootDir.toAbsolutePath().normalize();
            try (var stream = Files.walk(testRootDir)) {
                var paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Path normalizedPath = path.toAbsolutePath().normalize();
                    if (!normalizedPath.startsWith(normalizedRoot)) {
                        throw new IllegalStateException("Attempted to delete path outside test sandbox: " + path);
                    }
                    Files.delete(path);
                }
            }
            assertThat(Files.exists(testRootDir)).isFalse();
        }
    }

    @Test
    @DisplayName("Valid file under uploads root is served successfully with caching headers")
    void servesValidFileUnderUploadsRoot() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is unsupported on this filesystem provider; controller fails closed by design");
        Path validFile = tempUploadsDir.resolve("sample.png");
        byte[] expectedContent = "test-image-content".getBytes();
        Files.write(validFile, expectedContent);

        mockMvc.perform(get("/api/media/file/sample.png"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(content().bytes(expectedContent));
    }

    @Test
    @DisplayName("Valid nested file under uploads root is served successfully")
    void servesValidNestedFileUnderUploadsRoot() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is unsupported on this filesystem provider; controller fails closed by design");
        Path nestedDir = tempUploadsDir.resolve("users/42/posts");
        Files.createDirectories(nestedDir);
        Path validFile = nestedDir.resolve("nested.jpg");
        byte[] content = "nested-image-bytes".getBytes();
        Files.write(validFile, content);

        mockMvc.perform(get("/api/media/file/users/42/posts/nested.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("Valid file with literal plus sign in path is served without being converted to space")
    void servesValidFileWithLiteralPlusSign() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is unsupported on this filesystem provider; controller fails closed by design");
        Path validFile = tempUploadsDir.resolve("avatar+photo.png");
        byte[] content = "plus-photo-bytes".getBytes();
        Files.write(validFile, content);

        // Literal '+' in request URI must not be decoded as space
        mockMvc.perform(get("/api/media/file/avatar+photo.png"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));

        // Direct request with percent-encoded %2B resolves to literal '+'
        mockMvc.perform(get(URI.create("/api/media/file/avatar%2Bphoto.png")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("Directory traversal using literal .. segments is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsLiteralParentDirectoryTraversal() throws Exception {
        Path secretFile = outsideDir.resolve("secret.txt");
        Files.writeString(secretFile, "confidential");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/../" + outsideDir.getFileName() + "/secret.txt");

        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });

        mockMvc.perform(get("/api/media/file/../../secret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Directory traversal using percent-encoded %2e%2e segments is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsPercentEncodedDirectoryTraversal() throws Exception {
        Path outsideSentinel = outsideDir.resolve("sentinel.txt");
        Files.writeString(outsideSentinel, "outside-secret");

        // Use URI.create to avoid MockMvc get(String) double-encoding % into %25
        mockMvc.perform(get(URI.create("/api/media/file/%2e%2e/" + outsideDir.getFileName() + "/sentinel.txt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        mockMvc.perform(get(URI.create("/api/media/file/%2e%2e%2f" + outsideDir.getFileName() + "%2fsentinel.txt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/media/file/%2e%2e/" + outsideDir.getFileName() + "/sentinel.txt");
        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("Request with leading slashes is confined and cannot escape root")
    void rejectsLeadingSlashesTraversal() throws Exception {
        mockMvc.perform(get("/api/media/file///etc/passwd"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Request for root directory or empty path is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsEmptyOrRootDirectoryPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/");

        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("Request resolving to a directory instead of a regular file is rejected")
    void rejectsDirectoryTarget() throws Exception {
        Path folder = tempUploadsDir.resolve("subfolder");
        Files.createDirectories(folder);

        mockMvc.perform(get("/api/media/file/subfolder"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Request containing null byte is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsNullByteInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/photo.png%00.txt");

        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("Request for non-existent file under uploads root returns 404 MEDIA_NOT_FOUND")
    void returnsNotFoundForNonExistentFile() throws Exception {
        mockMvc.perform(get("/api/media/file/missing-file.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Symlink escaping upload root is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsSymlinkEscapingUploadRoot() throws Exception {
        Path outsideTarget = outsideDir.resolve("outside-target.txt");
        Files.writeString(outsideTarget, "outside-content");

        Path symlink = tempUploadsDir.resolve("link-to-outside.txt");
        try {
            Files.createSymbolicLink(symlink, outsideTarget);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        mockMvc.perform(get("/api/media/file/link-to-outside.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Directory traversal using Windows backslashes is rejected with 404 MEDIA_NOT_FOUND")
    void rejectsWindowsBackslashTraversal() throws Exception {
        mockMvc.perform(get("/api/media/file/..\\..\\secret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        mockMvc.perform(get("/api/media/file/%2e%2e%5c%2e%2e%5csecret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        mockMvc.perform(get("/api/media/file/subfolder\\..\\..\\secret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Windows drive letter or colon paths are rejected with 404 MEDIA_NOT_FOUND")
    void rejectsDriveLetterAndColonPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/C:/Windows/win.ini");
        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });

        mockMvc.perform(get("/api/media/file/C%3A/Windows/win.ini"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        mockMvc.perform(get("/api/media/file/D:/secret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("UNC path forms are rejected with 404 MEDIA_NOT_FOUND")
    void rejectsUncPaths() throws Exception {
        mockMvc.perform(get("/api/media/file//server/share/file.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/\\\\server\\share\\file.txt");
        assertThatThrownBy(() -> controller.serveMediaFile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });

        mockMvc.perform(get("/api/media/file/%5c%5cserver%5cshare%5cfile.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Sibling directory with matching prefix is rejected and cannot escape baseDir")
    void rejectsSiblingDirectoryPrefixEscape() throws Exception {
        Path siblingDir = tempUploadsDir.getParent().resolve(tempUploadsDir.getFileName().toString() + "-sibling");
        Files.createDirectories(siblingDir);
        Path secretFile = siblingDir.resolve("secret.txt");
        Files.writeString(secretFile, "sibling-secret-content");

        // Traversal attempt to sibling prefix directory using relative URI
        mockMvc.perform(get(URI.create("/api/media/file/../" + siblingDir.getFileName() + "/secret.txt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        // Symlink inside uploads root targeting sibling prefix directory
        Path symlinkToSibling = tempUploadsDir.resolve("sibling-link");
        try {
            Files.createSymbolicLink(symlinkToSibling, siblingDir);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        mockMvc.perform(get("/api/media/file/sibling-link/secret.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("In-root file with path-case variant is handled without security bypass")
    void handlesPathCaseVariants() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is unsupported on this filesystem provider; controller fails closed by design");
        Path validFile = tempUploadsDir.resolve("case-sample.png");
        byte[] content = "cased-sample-bytes".getBytes();
        Files.write(validFile, content);

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            // Windows filesystem is case-insensitive, so uppercase request successfully serves the file
            mockMvc.perform(get("/api/media/file/CASE-SAMPLE.PNG"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(content));
        } else {
            // Linux ext4 is case-sensitive, non-matching case returns 404
            mockMvc.perform(get("/api/media/file/CASE-SAMPLE.PNG"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("Malformed percent-encoded paths are rejected with 404 MEDIA_NOT_FOUND")
    void rejectsMalformedPercentEncoding() throws Exception {
        // Invalid hex characters: %zz
        MockHttpServletRequest reqInvalidHex = new MockHttpServletRequest("GET", "/api/media/file/%zz");
        assertThatThrownBy(() -> controller.serveMediaFile(reqInvalidHex))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });

        // Truncated percent sequence: %2
        MockHttpServletRequest reqTruncated = new MockHttpServletRequest("GET", "/api/media/file/%2");
        assertThatThrownBy(() -> controller.serveMediaFile(reqTruncated))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });

        // Trailing percent character: test%
        MockHttpServletRequest reqTrailing = new MockHttpServletRequest("GET", "/api/media/file/test%");
        assertThatThrownBy(() -> controller.serveMediaFile(reqTrailing))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("Invalid path characters are handled safely as 404 MEDIA_NOT_FOUND")
    void rejectsInvalidPathCharacters() throws Exception {
        String[] invalidPaths = new String[] {
                "/api/media/file/test<file>.png",
                "/api/media/file/test>file.png",
                "/api/media/file/test\"file.png",
                "/api/media/file/test|file.png",
                "/api/media/file/test?file.png",
                "/api/media/file/test*file.png"
        };

        for (String path : invalidPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            assertThatThrownBy(() -> controller.serveMediaFile(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                        assertThat(apiEx.getStatus().value()).isEqualTo(404);
                    });
        }
    }

    @Test
    @DisplayName("Secure handle: serves from an open handle InputStreamResource and not a pathname-backed UrlResource")
    void servesFromSecurelyOpenedHandleNotPathnameUrlResource() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is unsupported on this filesystem provider; controller fails closed by design");
        Path validFile = tempUploadsDir.resolve("handle-race-test.png");
        byte[] expectedBytes = "secure-handle-payload-bytes".getBytes();
        Files.write(validFile, expectedBytes);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/handle-race-test.png");
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> response = controller.serveMediaFile(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // 1. Proves resource is NOT a pathname-backed UrlResource (which would re-open the path upon read)
        assertThat(response.getBody()).isNotInstanceOf(org.springframework.core.io.UrlResource.class);
        assertThat(response.getBody()).isInstanceOf(org.springframework.core.io.InputStreamResource.class);

        // 2. Proves contentLength matches without re-opening
        assertThat(response.getBody().contentLength()).isEqualTo(expectedBytes.length);

        // 3. Proves payload is read directly from the securely opened handle
        try (var is = response.getBody().getInputStream()) {
            byte[] actualBytes = is.readAllBytes();
            assertThat(actualBytes).isEqualTo(expectedBytes);
        }
    }

    @Test
    @DisplayName("Provider limitation: Fails closed with 404 MEDIA_NOT_FOUND when SecureDirectoryStream is unsupported")
    void failsClosedWhenSecureDirectoryStreamUnsupported() throws Exception {
        Assumptions.assumeFalse(isSecureDirectoryStreamSupported(tempUploadsDir),
                "Only applicable when SecureDirectoryStream is unsupported on filesystem provider");
        Path validFile = tempUploadsDir.resolve("sample.png");
        Files.write(validFile, "test-content".getBytes());

        mockMvc.perform(get("/api/media/file/sample.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("Race resistance: Held directory stream rejects target replacement with symlink to outside secret")
    void heldDirectoryStreamRejectsSymlinkReplacement() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(tempUploadsDir),
                "SecureDirectoryStream is required to test held-directory handle symlink replacement");

        Path validFile = tempUploadsDir.resolve("replace-target.png");
        Files.writeString(validFile, "original-content");

        Path outsideSecret = outsideDir.resolve("confidential.txt");
        Files.writeString(outsideSecret, "super-secret-bytes");

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(tempUploadsDir)) {
            assertThat(ds).isInstanceOf(SecureDirectoryStream.class);
            SecureDirectoryStream<Path> sds = (SecureDirectoryStream<Path>) ds;

            Files.delete(validFile);
            try {
                Files.createSymbolicLink(validFile, outsideSecret);
            } catch (UnsupportedOperationException | IOException | SecurityException ex) {
                Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
            }

            List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
            assertThatThrownBy(() -> controller.openSecureFileChannel(sds, Paths.get("replace-target.png"), opened))
                    .isInstanceOf(Exception.class);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/file/replace-target.png");
            assertThatThrownBy(() -> controller.serveMediaFile(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                    });
        }
    }

    @Test
    @DisplayName("Root anchor resistance: Held parent directory stream rejects configured root swapped for external symlink")
    void heldParentStreamRejectsRootSymlinkReplacement() throws Exception {
        Assumptions.assumeTrue(isSecureDirectoryStreamSupported(testRootDir),
                "SecureDirectoryStream is required to test held-directory handle root symlink replacement");

        Path configuredRoot = testRootDir.resolve("configured-upload-root");
        Files.createDirectories(configuredRoot);

        Path outsideSecret = outsideDir.resolve("confidential-root.txt");
        Files.writeString(outsideSecret, "confidential-root-secret");

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(testRootDir)) {
            assertThat(ds).isInstanceOf(SecureDirectoryStream.class);
            SecureDirectoryStream<Path> parentStream = (SecureDirectoryStream<Path>) ds;

            // Delete configured root directory and replace with external symlink while parent handle is held
            Files.delete(configuredRoot);
            try {
                Files.createSymbolicLink(configuredRoot, outsideDir);
            } catch (UnsupportedOperationException | IOException | SecurityException ex) {
                Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
            }

            List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
            try {
                Path rootEntry = Paths.get("configured-upload-root");
                assertThatThrownBy(() -> controller.openSecureDirectory(parentStream, rootEntry, opened))
                        .isInstanceOf(ApiException.class)
                        .satisfies(ex -> {
                            ApiException apiEx = (ApiException) ex;
                            assertThat(apiEx.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                            assertThat(apiEx.getStatus().value()).isEqualTo(404);
                        });
            } finally {
                for (int i = opened.size() - 1; i >= 0; i--) {
                    try {
                        opened.get(i).close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    private boolean isSecureDirectoryStreamSupported(Path dir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            return ds instanceof SecureDirectoryStream;
        } catch (IOException e) {
            return false;
        }
    }
}
