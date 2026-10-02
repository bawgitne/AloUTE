package com.aloute.feature.user.service;

import com.aloute.feature.user.dto.request.UpdateProfileRequest;
import com.aloute.feature.user.dto.response.ProfileResponse;

public interface UserProfileService {
    ProfileResponse getProfile(Long userId);
    ProfileResponse updateCurrentProfile(Long currentUserId, UpdateProfileRequest request);
}
