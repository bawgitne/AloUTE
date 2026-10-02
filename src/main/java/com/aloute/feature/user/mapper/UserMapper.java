package com.aloute.feature.user.mapper;

import com.aloute.feature.user.dto.response.ProfileResponse;
import com.aloute.feature.user.dto.response.UserResponse;
import com.aloute.feature.user.dto.response.UserSummary;
import com.aloute.feature.user.entity.Profile;
import com.aloute.feature.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isVerified(),
                user.isPrivateAccount(),
                user.isActive()
        );
    }

    public ProfileResponse toProfileResponse(User user, Profile profile) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getWebsite(),
                user.isVerified(),
                user.isPrivateAccount(),
                profile.getFollowersCount(),
                profile.getFollowingCount(),
                profile.getPostsCount(),
                null
        );
    }

    public UserSummary toSummary(User user, Profile profile) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                user.isVerified()
        );
    }
}
