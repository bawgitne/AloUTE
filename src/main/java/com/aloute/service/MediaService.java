package com.aloute.service;

import com.aloute.dto.response.MediaQuotaResponse;
import com.aloute.dto.response.MediaResponse;
import com.aloute.entity.MediaPurpose;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {
    MediaResponse upload(Long userId, MultipartFile file, MediaPurpose purpose);
    void delete(Long userId, Long mediaId);
    MediaQuotaResponse quota(Long userId);
}
