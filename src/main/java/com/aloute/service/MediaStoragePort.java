package com.aloute.service;

import com.aloute.entity.MediaPurpose;
import org.springframework.web.multipart.MultipartFile;

public interface MediaStoragePort {
    StoredMedia store(Long ownerId, MediaPurpose purpose, MultipartFile file);
    void delete(String storagePath);

    record StoredMedia(String url, String storagePath) {}
}
