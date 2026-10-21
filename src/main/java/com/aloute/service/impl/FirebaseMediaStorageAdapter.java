package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.config.FirebaseProperties;
import com.aloute.entity.MediaPurpose;
import com.aloute.service.MediaStoragePort;
import com.google.cloud.storage.BlobInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class FirebaseMediaStorageAdapter implements MediaStoragePort {
    private final FirebaseProperties properties;

    public FirebaseMediaStorageAdapter(FirebaseProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredMedia store(Long ownerId, MediaPurpose purpose, MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE", "Firebase media storage is disabled");
        }
        if (FirebaseApp.getApps().isEmpty()) {
            log.error("Cannot upload media: FirebaseApp is not initialized because serviceAccountKey.json is missing or invalid.");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE",
                    "Firebase Storage chưa được kết nối. Hệ thống cần file serviceAccountKey.json hợp lệ tại thư mục gốc để tải ảnh/video.");
        }
        String objectName = "users/%d/%s/%s-%s".formatted(ownerId, purpose.name().toLowerCase(),
                UUID.randomUUID(), file.getOriginalFilename());
        try {
            String downloadToken = UUID.randomUUID().toString();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("firebaseStorageDownloadTokens", downloadToken);

            BlobInfo blobInfo = BlobInfo.newBuilder(properties.getBucket(), objectName)
                    .setContentType(file.getContentType())
                    .setMetadata(metadata)
                    .build();
            StorageClient.getInstance().bucket().getStorage().create(blobInfo, file.getBytes());
            String encoded = URLEncoder.encode(objectName, StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s".formatted(
                    properties.getBucket(), encoded, downloadToken);
            return new StoredMedia(url, objectName);
        } catch (IOException ex) {
            log.error("IO error uploading media to Firebase: {}", ex.getMessage(), ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED", "Unable to upload media to Firebase");
        } catch (Exception ex) {
            log.error("Failed to upload media to Firebase Storage: {}", ex.getMessage(), ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED", "Không thể tải file lên Firebase Storage: " + ex.getMessage());
        }
    }

    @Override
    public void delete(String storagePath) {
        if (!properties.isEnabled() || storagePath == null || FirebaseApp.getApps().isEmpty()) return;
        try {
            StorageClient.getInstance().bucket().get(storagePath).delete();
        } catch (Exception ex) {
            log.warn("Failed to delete media from Firebase Storage at {}: {}", storagePath, ex.getMessage());
        }
    }
}
