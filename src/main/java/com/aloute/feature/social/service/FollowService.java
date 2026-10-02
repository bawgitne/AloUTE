package com.aloute.feature.social.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.feature.social.dto.response.SocialRelationResponse;
import com.aloute.feature.user.dto.response.UserSummary;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FollowService {
    SocialRelationResponse follow(Long currentUserId, Long targetUserId);
    void unfollow(Long currentUserId, Long targetUserId);
    ApiResponse<List<UserSummary>> followers(Long userId, Pageable pageable);
    ApiResponse<List<UserSummary>> following(Long userId, Pageable pageable);
}
