package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.MediaPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the dev-only local-disk storage adapter: writes stay
 * confined to the upload root (including real-path checks against planted
 * symlinked directories), traversal filenames are sanitized, and deletes never
 * follow symlinks or escape the root.
 */
class LocalDiskMediaStorageAdapterTest {

    private Path testRootDir;
    private Path uploadsDir;
    private Path outsideDir;
    private LocalDiskMediaStorageAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        Path targetDir = Paths.get("target").toAbsolutePath().normalize();
        Files.createDirectories(targetDir);
        testRootDir = Files.createDirectories(targetDir.resolve("test-local-media-adapter-" + System.nanoTime()));
        uploadsDir = testRootDir.resolve("uploads");
        Files.createDirectories(uploadsDir);
        outsideDir = Files.createDirectories(testRootDir.resolve("outside"));
        adapter = new LocalDiskMediaStorageAdapter(uploadsDir.toString());
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

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    @Test
    @DisplayName("store writes inside the upload root and returns an /api/media/file/ URL")
    void storesInsideUploadRoot() {
        byte[] content = "avatar-bytes".getBytes();
        var stored = adapter.store(42L, MediaPurpose.USER_AVATAR, file("me.png", content));

        assertThat(stored.url()).startsWith("/api/media/file/users/42/user_avatar/");
        Path onDisk = uploadsDir.resolve(stored.storagePath()).normalize();
        assertThat(onDisk.startsWith(uploadsDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(onDisk)).isTrue();
    }

    @Test
    @DisplayName("store never escapes the upload root even with a traversal filename")
    void storeSanitizesTraversalFilename() {
        var stored = adapter.store(42L, MediaPurpose.USER_AVATAR,
                file("..\\..\\outside\\evil.png", "escaped".getBytes()));

        Path onDisk = uploadsDir.resolve(stored.storagePath()).normalize();
        assertThat(onDisk.startsWith(uploadsDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(outsideDir.resolve("evil.png"))).isFalse();
    }

    @Test
    @DisplayName("store refuses to write through a symlinked directory planted under the upload root")
    void storeRefusesSymlinkedParentDirectory() throws Exception {
        Path planted = uploadsDir.resolve("users");
        try {
            Files.createSymbolicLink(planted, outsideDir);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        assertThatThrownBy(() -> adapter.store(7L, MediaPurpose.USER_AVATAR, file("x.png", "x".getBytes())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("MEDIA_UPLOAD_FAILED"));
        // No file may land outside the upload root; the real-path check aborts before the copy.
        try (var stream = Files.walk(outsideDir)) {
            assertThat(stream.filter(Files::isRegularFile).count()).isZero();
        }
    }

    @Test
    @DisplayName("delete removes a stored file but never a traversal target outside the root")
    void deleteConfinedToUploadRoot() throws Exception {
        var stored = adapter.store(42L, MediaPurpose.USER_AVATAR, file("gone.png", "bye".getBytes()));
        assertThat(Files.exists(uploadsDir.resolve(stored.storagePath()))).isTrue();

        Path outsideSecret = outsideDir.resolve("secret.txt");
        Files.writeString(outsideSecret, "keep-me");

        adapter.delete(stored.storagePath());
        assertThat(Files.exists(uploadsDir.resolve(stored.storagePath()))).isFalse();

        adapter.delete("../../outside/secret.txt");
        assertThat(Files.exists(outsideSecret)).isTrue();
    }

    @Test
    @DisplayName("delete does not follow a symlink planted at the storage path")
    void deleteDoesNotFollowSymlink() throws Exception {
        Path outsideSecret = outsideDir.resolve("precious.txt");
        Files.writeString(outsideSecret, "still-here");
        Path plantedLink = uploadsDir.resolve("users/9/avatar/link.png");
        Files.createDirectories(plantedLink.getParent());
        try {
            Files.createSymbolicLink(plantedLink, outsideSecret);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.abort("OS denies symlink creation: " + ex.getMessage());
        }

        adapter.delete(uploadsDir.relativize(plantedLink).toString());

        assertThat(Files.exists(outsideSecret)).isTrue();
        assertThat(Files.readString(outsideSecret)).isEqualTo("still-here");
    }
}
