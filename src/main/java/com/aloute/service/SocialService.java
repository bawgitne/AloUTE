package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.SocialRelationResponse;
import com.aloute.dto.response.UserRelationStatusResponse;
import com.aloute.dto.response.UserSummary;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface SocialService {
    SocialRelationResponse follow(Long currentUserId, Long targetUserId);
    void unfollow(Long currentUserId, Long targetUserId);
    ApiResponse<List<UserSummary>> followers(Long userId, Pageable pageable);
    ApiResponse<List<UserSummary>> following(Long userId, Pageable pageable);

    SocialRelationResponse block(Long currentUserId, Long targetUserId);
    SocialRelationResponse blockByUsername(Long currentUserId, String username);
    void unblock(Long currentUserId, Long targetUserId);
    ApiResponse<List<UserSummary>> blockedUsers(Long currentUserId, Pageable pageable);

    SocialRelationResponse mute(Long currentUserId, Long targetUserId);
    SocialRelationResponse muteByUsername(Long currentUserId, String username);
    void unmute(Long currentUserId, Long targetUserId);
    ApiResponse<List<UserSummary>> mutedUsers(Long currentUserId, Pageable pageable);

    SocialRelationResponse restrict(Long currentUserId, Long targetUserId);
    void unrestrict(Long currentUserId, Long targetUserId);

    UserRelationStatusResponse getRelationStatus(Long currentUserId, Long targetUserId);
}
