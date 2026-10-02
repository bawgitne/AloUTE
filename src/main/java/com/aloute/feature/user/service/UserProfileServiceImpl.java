package com.aloute.feature.user.service;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.RequestValidationException;
import com.aloute.feature.user.dto.request.UpdateProfileRequest;
import com.aloute.feature.user.dto.response.ProfileResponse;
import com.aloute.feature.user.entity.Profile;
import com.aloute.feature.user.entity.User;
import com.aloute.feature.user.mapper.UserMapper;
import com.aloute.feature.user.repository.ProfileRepository;
import com.aloute.feature.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserMapper userMapper;

    public UserProfileServiceImpl(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = requireUser(userId);
        Profile profile = requireProfile(userId);
        return userMapper.toProfileResponse(user, profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateCurrentProfile(Long currentUserId, UpdateProfileRequest request) {
        User user = requireUser(currentUserId);
        Profile profile = requireProfile(currentUserId);

        if (request.hasDisplayName()) {
            String value = request.getDisplayName();
            if (value == null || value.isBlank()) {
                throw new RequestValidationException(
                        Map.of("displayName", List.of("Display name must not be blank")));
            }
            profile.setDisplayName(value.trim());
        }

        if (request.hasBio()) {
            profile.setBio(trimToNull(request.getBio()));
        }

        if (request.hasWebsite()) {
            String website = trimToNull(request.getWebsite());
            if (website != null) {
                validateUri(website);
            }
            profile.setWebsite(website);
        }

        if (request.hasPrivateAccount()) {
            user.setPrivateAccount(Boolean.TRUE.equals(request.getPrivateAccount()));
        }

        if (request.hasAvatarMediaId() && request.getAvatarMediaId() != null) {
            throw new RequestValidationException(
                    Map.of("avatarMediaId", List.of(
                            "Avatar media update depends on the Media module, which is outside Khôi's assigned scope")));
        }

        return userMapper.toProfileResponse(user, profile);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Profile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User profile not found"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateUri(String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                throw new URISyntaxException(value, "URI must be absolute");
            }
        } catch (URISyntaxException ex) {
            throw new RequestValidationException(
                    Map.of("website", List.of("Website must be a valid URI")));
        }
    }
}
