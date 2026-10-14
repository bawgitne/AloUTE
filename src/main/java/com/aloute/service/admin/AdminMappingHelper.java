package com.aloute.service.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMappingHelper {
    private final ProfileRepository profileRepository;

    public UserSummary toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        String displayName = user.getUsername();
        String avatarUrl = null;
        Profile profile = profileRepository.findById(user.getId()).orElse(null);
        if (profile != null) {
            if (profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
                displayName = profile.getDisplayName();
            }
            avatarUrl = profile.getAvatarUrl();
        }
        return new UserSummary(user.getId(), user.getUsername(), displayName, avatarUrl, user.isVerified());
    }
}
