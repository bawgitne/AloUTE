package com.aloute.service;

import com.aloute.dto.request.UpdateProfileRequest;
import com.aloute.dto.response.ProfileResponse;

public interface UserService {
    ProfileResponse getProfile(Long userId);
    ProfileResponse getProfileByUsername(String username);
    ProfileResponse updateCurrentProfile(Long currentUserId, UpdateProfileRequest request);
}
