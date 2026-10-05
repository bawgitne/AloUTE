package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.config.FirebaseProperties;
import com.aloute.entity.MediaPurpose;
import com.aloute.service.MediaStoragePort;
import com.google.cloud.storage.BlobInfo;
import com.google.firebase.cloud.StorageClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
        String objectName = "users/%d/%s/%s-%s".formatted(ownerId, purpose.name().toLowerCase(),
                UUID.randomUUID(), file.getOriginalFilename());
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(properties.getBucket(), objectName)
                    .setContentType(file.getContentType())
                    .build();
            StorageClient.getInstance().bucket().getStorage().create(blobInfo, file.getBytes());
            String encoded = URLEncoder.encode(objectName, StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media".formatted(properties.getBucket(), encoded);
            return new StoredMedia(url, objectName);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED", "Unable to upload media to Firebase");
        }
    }

    @Override
    public void delete(String storagePath) {
        if (!properties.isEnabled() || storagePath == null) return;
        StorageClient.getInstance().bucket().get(storagePath).delete();
    }
}
