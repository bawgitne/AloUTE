package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.RequestValidationException;
import com.aloute.dto.request.UpdateProfileRequest;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aloute.dto.request.ChangePasswordRequest;
import com.aloute.entity.MediaAsset;
import com.aloute.repository.MediaAssetRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.aloute.service.admin.SystemAdminGuard systemAdminGuard;
    private final com.aloute.repository.UserNotificationSettingRepository notificationSettingRepository;

    @Override @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = requireUser(userId);
        return toProfile(user, requireProfile(userId));
    }

    @Override @Transactional(readOnly = true)
    public ProfileResponse getProfileByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        return toProfile(user, requireProfile(user.getId()));
    }

    @Override @Transactional
    public ProfileResponse updateCurrentProfile(Long currentUserId, UpdateProfileRequest request) {
        User user = requireUser(currentUserId);
        Profile profile = requireProfile(currentUserId);
        if (request.hasDisplayName()) {
            String value = request.getDisplayName();
            if (value == null || value.isBlank()) {
                throw new RequestValidationException(Map.of("displayName", List.of("Display name must not be blank")));
            }
            profile.setDisplayName(value.trim());
        }
        if (request.hasBio()) profile.setBio(trimToNull(request.getBio()));
        if (request.hasWebsite()) {
            String website = trimToNull(request.getWebsite());
            if (website != null) validateUri(website);
            profile.setWebsite(website);
        }
        if (request.hasPrivateAccount()) user.setPrivateAccount(Boolean.TRUE.equals(request.getPrivateAccount()));
        if (request.hasAvatarMediaId()) {
            if (request.getAvatarMediaId() == null) {
                profile.setAvatarUrl(null);
            } else {
                MediaAsset media = mediaAssetRepository.findByIdAndOwner_IdAndDeletedAtIsNull(request.getAvatarMediaId(), currentUserId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Avatar media not found"));
                profile.setAvatarUrl(media.getUrl());
                media.setAttachedAt(Instant.now());
            }
        }
        if (request.hasCoverMediaId()) {
            if (request.getCoverMediaId() == null) {
                profile.setCoverUrl(null);
            } else {
                MediaAsset media = mediaAssetRepository.findByIdAndOwner_IdAndDeletedAtIsNull(request.getCoverMediaId(), currentUserId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Cover media not found"));
                profile.setCoverUrl(media.getUrl());
                media.setAttachedAt(Instant.now());
            }
        }
        return toProfile(user, profile);
    }

    @Override @Transactional
    public User changePassword(Long currentUserId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH", "Mật khẩu xác nhận không khớp.");
        }
        User user = requireUser(currentUserId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không chính xác.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_SAME_AS_OLD", "Mật khẩu mới không được trùng với mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setAuthVersion((user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L);
        return userRepository.save(user);
    }

    @Override @Transactional
    public User logoutOtherSessions(Long currentUserId) {
        User user = requireUser(currentUserId);
        user.setAuthVersion((user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L);
        return userRepository.save(user);
    }

    @Override @Transactional
    public void deactivateAccount(Long currentUserId, String password) {
        User user = requireUser(currentUserId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Mật khẩu xác nhận không chính xác.");
        }
        // A SYSTEM_ADMIN must never self-deactivate if they are the last active admin.
        // The guard locks the admin set first, so this holds even under a concurrent
        // suspension of another admin. On rejection nothing is mutated (no state change,
        // no authVersion bump); the surrounding transaction simply rolls back.
        user = systemAdminGuard.lockAndRequireAdminRemains(user);
        user.setActive(false);
        user.setAuthVersion((user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L);
        userRepository.save(user);
    }

    @Override @Transactional(readOnly = true)
    public com.aloute.dto.response.NotificationSettingsResponse getNotificationSettings(Long userId) {
        return notificationSettingRepository.findById(userId)
                .map(s -> new com.aloute.dto.response.NotificationSettingsResponse(
                        s.isLikesEnabled(),
                        s.isCommentsEnabled(),
                        s.isFollowsEnabled(),
                        s.isMentionsEnabled(),
                        s.isEmailNotificationsEnabled(),
                        s.isSoundEnabled()))
                .orElseGet(com.aloute.dto.response.NotificationSettingsResponse::defaults);
    }

    @Override @Transactional
    public com.aloute.dto.response.NotificationSettingsResponse updateNotificationSettings(
            Long userId, com.aloute.dto.request.UpdateNotificationSettingsRequest request) {
        User user = requireUser(userId);
        com.aloute.entity.UserNotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> com.aloute.entity.UserNotificationSetting.builder().user(user).build());

        setting.setLikesEnabled(request.likesEnabled());
        setting.setCommentsEnabled(request.commentsEnabled());
        setting.setFollowsEnabled(request.followsEnabled());
        setting.setMentionsEnabled(request.mentionsEnabled());
        setting.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        setting.setSoundEnabled(request.soundEnabled());

        com.aloute.entity.UserNotificationSetting saved = notificationSettingRepository.save(setting);
        return new com.aloute.dto.response.NotificationSettingsResponse(
                saved.isLikesEnabled(),
                saved.isCommentsEnabled(),
                saved.isFollowsEnabled(),
                saved.isMentionsEnabled(),
                saved.isEmailNotificationsEnabled(),
                saved.isSoundEnabled());
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }
    private Profile requireProfile(Long id) {
        return profileRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User profile not found"));
    }
    private String trimToNull(String value) { if (value == null) return null; String v=value.trim(); return v.isEmpty()?null:v; }
    private void validateUri(String value) {
        try { if (!new URI(value).isAbsolute()) throw new URISyntaxException(value, "URI must be absolute"); }
        catch (URISyntaxException ex) { throw new RequestValidationException(Map.of("website", List.of("Website must be a valid URI"))); }
    }
    private ProfileResponse toProfile(User u, Profile p) {
        return new ProfileResponse(u.getId(), u.getUsername(), p.getDisplayName(), p.getBio(), p.getAvatarUrl(),
                p.getCoverUrl(), p.getWebsite(), u.isVerified(), u.isPrivateAccount(), p.getFollowersCount(), p.getFollowingCount(),
                p.getPostsCount(), null);
    }
}
