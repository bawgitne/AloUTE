package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.MediaQuotaResponse;
import com.aloute.dto.response.MediaResponse;
import com.aloute.entity.*;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.MediaService;
import com.aloute.service.MediaStoragePort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class MediaServiceImpl implements MediaService {
    private static final long USER_QUOTA_BYTES = 150L * 1024L * 1024L;
    private static final long IMAGE_MAX_BYTES = 5L * 1024L * 1024L;
    private static final long VIDEO_MAX_BYTES = 30L * 1024L * 1024L;
    private final MediaAssetRepository mediaRepository;
    private final UserRepository userRepository;
    private final MediaStoragePort storagePort;

    public MediaServiceImpl(MediaAssetRepository mediaRepository, UserRepository userRepository, MediaStoragePort storagePort) {
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.storagePort = storagePort;
    }

    @Override @Transactional
    public MediaResponse upload(Long userId, MultipartFile file, MediaPurpose purpose) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_FILE_REQUIRED", "Media file is required");
        MediaType type = detectType(file.getContentType());
        long maxBytes = type == MediaType.VIDEO ? VIDEO_MAX_BYTES : IMAGE_MAX_BYTES;
        if (file.getSize() > maxBytes) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_TOO_LARGE", "Media file exceeds the configured limit");
        long used = mediaRepository.sumActiveBytesByOwner(userId);
        if (used + file.getSize() > USER_QUOTA_BYTES) throw new ApiException(HttpStatus.CONFLICT, "MEDIA_QUOTA_EXCEEDED", "User media quota exceeded");
        User owner = userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        MediaStoragePort.StoredMedia stored = storagePort.store(userId, purpose, file);
        MediaAsset media = new MediaAsset();
        media.setOwner(owner);
        media.setPurpose(purpose);
        media.setType(type);
        media.setMimeType(file.getContentType());
        media.setFileSize(file.getSize());
        media.setStoragePath(stored.storagePath());
        media.setUrl(stored.url());
        return toResponse(mediaRepository.save(media));
    }

    @Override @Transactional
    public void delete(Long userId, Long mediaId) {
        MediaAsset media = mediaRepository.findByIdAndOwner_IdAndDeletedAtIsNull(mediaId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found"));
        if (media.getAttachedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "MEDIA_ALREADY_ATTACHED", "Attached media cannot be deleted here");
        media.setDeletedAt(Instant.now());
        storagePort.delete(media.getStoragePath());
    }

    @Override @Transactional(readOnly = true)
    public MediaQuotaResponse quota(Long userId) {
        long used = mediaRepository.sumActiveBytesByOwner(userId);
        return new MediaQuotaResponse(used, USER_QUOTA_BYTES, Math.max(0, USER_QUOTA_BYTES - used));
    }

    private MediaResponse toResponse(MediaAsset media) {
        return new MediaResponse(media.getId(), media.getType(), media.getUrl(), media.getWidth(), media.getHeight(), media.getDurationSeconds());
    }

    private MediaType detectType(String mimeType) {
        if (mimeType == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_TYPE_UNSUPPORTED", "Unsupported media type");
        if (mimeType.equalsIgnoreCase("image/gif")) return MediaType.GIF;
        if (mimeType.startsWith("image/")) return MediaType.IMAGE;
        if (mimeType.startsWith("video/")) return MediaType.VIDEO;
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_TYPE_UNSUPPORTED", "Unsupported media type");
    }
}
