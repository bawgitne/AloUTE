package com.aloute.service;

import com.aloute.dto.request.ChangePasswordRequest;
import com.aloute.dto.request.UpdateProfileRequest;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.entity.User;

public interface UserService {
    ProfileResponse getProfile(Long userId);
    ProfileResponse getProfileByUsername(String username);
    ProfileResponse updateCurrentProfile(Long currentUserId, UpdateProfileRequest request);
    User changePassword(Long currentUserId, ChangePasswordRequest request);
    User logoutOtherSessions(Long currentUserId);
    void deactivateAccount(Long currentUserId, String password);
    com.aloute.dto.response.NotificationSettingsResponse getNotificationSettings(Long userId);
    com.aloute.dto.response.NotificationSettingsResponse updateNotificationSettings(Long userId, com.aloute.dto.request.UpdateNotificationSettingsRequest request);
}
