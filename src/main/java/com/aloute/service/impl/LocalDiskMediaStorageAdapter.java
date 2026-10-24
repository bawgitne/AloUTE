package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.MediaPurpose;
import com.aloute.service.MediaStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/**
 * Dev-only storage adapter writing media to the local filesystem under
 * {@code aloute.media.upload-dir} (default: ./uploads). Files are served back
 * through the existing hardened proxy GET /api/media/file/** so no extra
 * static resource mapping is required. Opt in with aloute.media.local-storage=true
 * (local demo runs without Firebase credentials); production keeps Firebase
 * Storage and the test suites keep the Firebase-adapter 503 semantics.
 */
@Slf4j
@Component
@Primary
@Profile("dev")
@ConditionalOnProperty(name = "aloute.media.local-storage", havingValue = "true")
public class LocalDiskMediaStorageAdapter implements MediaStoragePort {

    private final Path baseDir;

    public LocalDiskMediaStorageAdapter(@Value("${aloute.media.upload-dir:uploads}") String uploadDir) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredMedia store(Long ownerId, MediaPurpose purpose, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replace('\\', '/');
        int slash = safeName.lastIndexOf('/');
        if (slash >= 0) safeName = safeName.substring(slash + 1);
        if (safeName.isBlank()) safeName = "file";

        String relativeKey = "users/%d/%s/%s-%s".formatted(ownerId, purpose.name().toLowerCase(),
                UUID.randomUUID(), safeName);
        Path target;
        try {
            target = baseDir.resolve(relativeKey).normalize();
        } catch (InvalidPathException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_UPLOAD_FAILED", "Invalid storage path");
        }
        if (!target.startsWith(baseDir) || target.equals(baseDir)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_UPLOAD_FAILED", "Invalid storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            // The string-level containment check above is not enough if a symlinked
            // directory was planted under the upload root; verify the real (link-resolved)
            // parent still lives inside the real base directory before writing.
            Path realBase = baseDir.toRealPath();
            Path realParent = target.getParent().toRealPath();
            if (!realParent.startsWith(realBase) || realParent.equals(realBase)) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_UPLOAD_FAILED", "Invalid storage path");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("Failed to store media locally: {}", ex.getMessage(), ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED", "Không thể lưu tệp vào bộ nhớ cục bộ");
        }
        return new StoredMedia("/api/media/file/" + relativeKey, relativeKey);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path target = baseDir.resolve(storagePath).normalize();
            if (!target.startsWith(baseDir) || target.equals(baseDir)) {
                return;
            }
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (InvalidPathException | IOException | SecurityException ex) {
            log.warn("Failed to delete local media {}: {}", storagePath, ex.getMessage());
        }
    }
}
