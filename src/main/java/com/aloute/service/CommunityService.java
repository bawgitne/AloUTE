package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRoleRequest;
import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.CommunityResponse;
import java.util.List;

public interface CommunityService {
    ApiResponse<List<CommunityResponse>> list(String q, int page, int size, Long viewerId);
    CommunityResponse get(Long communityId, Long viewerId);
    CommunityResponse create(Long ownerId, CreateCommunityRequest request);
    CommunityResponse update(Long userId, Long communityId, UpdateCommunityRequest request);
    void delete(Long userId, Long communityId);
    CommunityMembershipResponse join(Long userId, Long communityId);
    ApiResponse<List<CommunityMembershipResponse>> members(Long communityId, int page, int size);
    void leave(Long userId, Long communityId);
    CommunityMembershipResponse updateRole(Long actorId, Long communityId, Long targetUserId, UpdateCommunityRoleRequest request);
}
