package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.MediaQuotaResponse;
import com.aloute.dto.response.MediaResponse;
import com.aloute.entity.MediaPurpose;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/media")
@Validated
public class MediaController {
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadDir;

    @Autowired
    public MediaController(MediaService mediaService,
                           CurrentUserProvider currentUserProvider,
                           @Value("${aloute.media.upload-dir:uploads}") String uploadDir) {
        this.mediaService = mediaService;
        this.currentUserProvider = currentUserProvider;
        this.uploadDir = uploadDir;
    }

    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider) {
        this(mediaService, currentUserProvider, "uploads");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponse>> upload(@RequestPart("file") MultipartFile file,
                                                            @RequestPart("purpose") MediaPurpose purpose) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mediaService.upload(currentUserProvider.requireUserId(), file, purpose)));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long mediaId) {
        mediaService.delete(currentUserProvider.requireUserId(), mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/quota")
    public ApiResponse<MediaQuotaResponse> quota() {
        return ApiResponse.success(mediaService.quota(currentUserProvider.requireUserId()));
    }

    @GetMapping("/file/**")
    public ResponseEntity<Resource> serveMediaFile(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/media/file/";
        if (!fullPath.contains(prefix)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        String rawKey = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());
        if (rawKey.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        String storageKey;
        try {
            storageKey = UriUtils.decode(rawKey, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (storageKey.contains("\0") || storageKey.contains(":")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (rawKey.startsWith("//") || rawKey.startsWith("\\\\")
                || storageKey.startsWith("//") || storageKey.startsWith("\\\\")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        String sanitizedKey = storageKey.replace('\\', '/');
        while (sanitizedKey.startsWith("/")) {
            sanitizedKey = sanitizedKey.substring(1);
        }

        if (sanitizedKey.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        Path baseDir;
        Path candidate;
        try {
            baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            candidate = baseDir.resolve(sanitizedKey).normalize();
        } catch (InvalidPathException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (!candidate.startsWith(baseDir) || candidate.equals(baseDir)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (!Files.exists(candidate) || !Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        try {
            Path realBase = Files.exists(baseDir) ? baseDir.toRealPath() : baseDir;
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realBase) || !Files.isRegularFile(realCandidate) || !Files.isReadable(realCandidate)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
            }

            Resource resource = new UrlResource(realCandidate.toUri());
            String contentType = null;
            try {
                contentType = Files.probeContentType(realCandidate);
            } catch (IOException | SecurityException ignored) {}

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(resource);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | SecurityException ignored) {}

        throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
    }
}
