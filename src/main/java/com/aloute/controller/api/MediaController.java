package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.MediaQuotaResponse;
import com.aloute.dto.response.MediaResponse;
import com.aloute.entity.MediaPurpose;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.MediaService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Validated
public class MediaController {
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;

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
    public ResponseEntity<org.springframework.core.io.Resource> serveMediaFile(jakarta.servlet.http.HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/media/file/";
        String storageKey = fullPath.contains(prefix) ? fullPath.substring(fullPath.indexOf(prefix) + prefix.length()) : "";
        java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(storageKey).normalize();
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = java.nio.file.Files.probeContentType(filePath);
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                        .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                        .body(resource);
            }
        } catch (Exception ignored) {}
        return ResponseEntity.notFound().build();
    }
}
