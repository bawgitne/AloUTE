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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

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
        if (request.hasAvatarMediaId() && request.getAvatarMediaId() != null) {
            throw new RequestValidationException(Map.of("avatarMediaId", List.of(
                    "Avatar media update requires the Media module; no Media relation is invented in Khôi scope")));
        }
        return toProfile(user, profile);
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
                p.getWebsite(), u.isVerified(), u.isPrivateAccount(), p.getFollowersCount(), p.getFollowingCount(),
                p.getPostsCount(), null);
    }
}
