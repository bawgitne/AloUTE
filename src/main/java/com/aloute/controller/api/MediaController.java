package com.aloute.controller.api;

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
@Validated
public class MediaController {
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;

    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider) {
        this.mediaService = mediaService;
        this.currentUserProvider = currentUserProvider;
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
}
