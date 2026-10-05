package com.aloute.service.impl;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.CreateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRoleRequest;
import com.aloute.dto.response.*;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.service.CommunityService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class CommunityServiceImpl implements CommunityService {
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final EntityManager entityManager;

    public CommunityServiceImpl(CommunityRepository communityRepository, CommunityMemberRepository memberRepository,
                                ProfileRepository profileRepository, EntityManager entityManager) {
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.profileRepository = profileRepository;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<CommunityResponse>> list(String q, int page, int size, Long viewerId) {
        long total = communityRepository.countCommunitiesFromSp(q);
        return ApiResponse.page(communityRepository.listCommunitiesFromSp(q, page * size, size).stream()
                .map(community -> toResponse(community, viewerId)).toList(), meta(page, size, total));
    }

    @Override @Transactional(readOnly = true)
    public CommunityResponse get(Long communityId, Long viewerId) {
        return toResponse(requireCommunity(communityId), viewerId);
    }

    @Override @Transactional
    public CommunityResponse create(Long ownerId, CreateCommunityRequest request) {
        Long id = communityRepository.createCommunityFromSp(ownerId, request.name(), request.slug(),
                request.description(), request.isPrivate(), request.avatarMediaId(), request.coverMediaId());
        flushAndClear();
        return get(id, ownerId);
    }

    @Override @Transactional
    public CommunityResponse update(Long userId, Long communityId, UpdateCommunityRequest request) {
        Long id = communityRepository.updateCommunityFromSp(communityId, userId, request.name(), request.description(),
                request.isPrivate(), request.avatarMediaId(), request.coverMediaId());
        flushAndClear();
        return get(id, userId);
    }

    @Override @Transactional
    public void delete(Long userId, Long communityId) {
        communityRepository.deleteCommunityFromSp(communityId, userId);
        flushAndClear();
    }

    @Override @Transactional
    public CommunityMembershipResponse join(Long userId, Long communityId) {
        Long id = memberRepository.joinCommunityFromSp(communityId, userId);
        flushAndClear();
        return memberRepository.findById(id).map(this::membership)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_MEMBER_NOT_FOUND", "Community membership not found"));
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<CommunityMembershipResponse>> members(Long communityId, int page, int size) {
        requireCommunity(communityId);
        long total = memberRepository.countMembersFromSp(communityId);
        return ApiResponse.page(memberRepository.listMembersFromSp(communityId, page * size, size).stream()
                .map(this::membership).toList(), meta(page, size, total));
    }

    @Override @Transactional
    public void leave(Long userId, Long communityId) {
        memberRepository.leaveCommunityFromSp(communityId, userId);
        flushAndClear();
    }

    @Override @Transactional
    public CommunityMembershipResponse updateRole(Long actorId, Long communityId, Long targetUserId, UpdateCommunityRoleRequest request) {
        Long id = memberRepository.updateRoleFromSp(communityId, actorId, targetUserId, request.role().name());
        flushAndClear();
        return memberRepository.findById(id).map(this::membership)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_MEMBER_NOT_FOUND", "Community membership not found"));
    }

    private Community requireCommunity(Long id) {
        return communityRepository.getCommunityFromSp(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "Community not found"));
    }

    private CommunityResponse toResponse(Community community, Long viewerId) {
        CommunityRole role = viewerId == null ? null : memberRepository.getMembershipFromSp(community.getId(), viewerId)
                .map(CommunityMember::getRole).orElse(null);
        boolean owner = viewerId != null && Objects.equals(community.getOwner().getId(), viewerId);
        return new CommunityResponse(community.getId(), community.getOwner().getId(), community.getName(),
                community.getSlug(), community.getDescription(), community.getAvatarUrl(), community.getCoverUrl(),
                community.isPrivateCommunity(), community.getMemberCount(), community.getPostCount(), role, owner);
    }

    private CommunityMembershipResponse membership(CommunityMember member) {
        User user = member.getUser();
        Profile profile = profileRepository.findById(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User profile not found"));
        return new CommunityMembershipResponse(member.getCommunity().getId(),
                new UserSummary(user.getId(), user.getUsername(), profile.getDisplayName(), profile.getAvatarUrl(), user.isVerified()),
                member.getRole(), member.getJoinedAt());
    }

    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    private PageMeta meta(int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageMeta(page, size, total, totalPages, page + 1 < totalPages, page > 0);
    }
}
