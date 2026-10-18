package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.CreateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRequest;
import com.aloute.dto.request.UpdateCommunityRoleRequest;
import com.aloute.dto.response.CommunityMembershipResponse;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.*;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.CommunityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class CommunityServiceImpl implements CommunityService {
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MediaAssetRepository mediaAssetRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CommunityResponse>> list(String q, int page, int size, Long viewerId) {
        Page<Community> communityPage = communityRepository.searchVisibleCommunities(
                q, viewerId == null ? -1L : viewerId,
                PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<CommunityResponse> list = communityPage.getContent().stream()
                .map(c -> toResponse(c, viewerId))
                .toList();
        return ApiResponse.page(list, PageMeta.from(communityPage));
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityResponse get(Long communityId, Long viewerId) {
        return toResponse(requireReadableCommunity(communityId, viewerId), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityResponse getBySlug(String slug, Long viewerId) {
        Community community = communityRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMUNITY_NOT_FOUND));
        requireDiscoveryAccess(community, viewerId);
        return toResponse(community, viewerId);
    }

    @Override
    @Transactional
    public CommunityResponse create(Long ownerId, CreateCommunityRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Owner not found"));

        if (communityRepository.existsBySlug(request.slug())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_SLUG_EXISTS", "Community slug already exists");
        }

        Community community = new Community();
        community.setOwner(owner);
        community.setName(request.name());
        community.setSlug(request.slug());
        community.setDescription(request.description());
        community.setPrivateCommunity(Boolean.TRUE.equals(request.isPrivate()));
        community.setMemberCount(1);
        community.setPostCount(0);

        if (request.avatarMediaId() != null) {
            mediaAssetRepository.findById(request.avatarMediaId())
                    .ifPresent(m -> community.setAvatarUrl(m.getUrl()));
        }
        if (request.coverMediaId() != null) {
            mediaAssetRepository.findById(request.coverMediaId())
                    .ifPresent(m -> community.setCoverUrl(m.getUrl()));
        }

        Community saved = communityRepository.save(community);

        // Add creator as ADMIN member
        CommunityMember ownerMember = new CommunityMember(saved, owner, CommunityRole.ADMIN);
        memberRepository.save(ownerMember);

        return toResponse(saved, ownerId);
    }

    @Override
    @Transactional
    public CommunityResponse update(Long userId, Long communityId, UpdateCommunityRequest request) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, userId);
        CommunityMember actorMember = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Must be a community member"));

        if (actorMember.getRole() != CommunityRole.ADMIN && !community.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Must be an admin or owner to update community");
        }

        if (request.name() != null) community.setName(request.name());
        if (request.description() != null) community.setDescription(request.description());
        if (request.isPrivate() != null) community.setPrivateCommunity(request.isPrivate());

        if (request.avatarMediaId() != null) {
            mediaAssetRepository.findById(request.avatarMediaId())
                    .ifPresent(m -> community.setAvatarUrl(m.getUrl()));
        }
        if (request.coverMediaId() != null) {
            mediaAssetRepository.findById(request.coverMediaId())
                    .ifPresent(m -> community.setCoverUrl(m.getUrl()));
        }

        return toResponse(communityRepository.save(community), userId);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long communityId) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, userId);
        if (!Objects.equals(community.getOwner().getId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only community owner can delete the community");
        }
        community.setDeletedAt(Instant.now());
        communityRepository.save(community);
    }

    @Override
    @Transactional
    public CommunityMembershipResponse join(Long userId, Long communityId) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            CommunityMember existing = memberRepository.findByCommunityIdAndUserId(communityId, userId).get();
            return membership(existing);
        }

        if (community.isPrivateCommunity()) {
            throw new ApiException(ErrorCode.COMMUNITY_ACCESS_DENIED, "Cannot self-join private community");
        }

        CommunityMember newMember = new CommunityMember(community, user, CommunityRole.MEMBER);
        CommunityMember savedMember = memberRepository.save(newMember);

        community.setMemberCount(community.getMemberCount() + 1);
        communityRepository.save(community);

        return membership(savedMember);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CommunityMembershipResponse>> members(Long communityId, int page, int size, Long viewerId) {
        requireReadableCommunity(communityId, viewerId);
        Page<CommunityMember> membersPage = memberRepository.findByCommunityIdOrderByJoinedAtDesc(
                communityId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<CommunityMembershipResponse> list = membersPage.getContent().stream()
                .map(this::membership)
                .toList();
        return ApiResponse.page(list, PageMeta.from(membersPage));
    }

    @Override
    @Transactional
    public void leave(Long userId, Long communityId) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, userId);
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_MEMBER_NOT_FOUND", "Not a member"));

        if (community.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE", "Owner cannot leave the community without transferring ownership");
        }

        memberRepository.delete(member);
        community.setMemberCount(Math.max(0, community.getMemberCount() - 1));
        communityRepository.save(community);
    }

    @Override
    @Transactional
    public CommunityMembershipResponse updateRole(Long actorId, Long communityId, Long targetUserId, UpdateCommunityRoleRequest request) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, actorId);
        CommunityMember actorMember = memberRepository.findByCommunityIdAndUserId(communityId, actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not a member"));

        if (actorMember.getRole() != CommunityRole.ADMIN && !community.getOwner().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only admin or owner can change roles");
        }

        CommunityMember targetMember = memberRepository.findByCommunityIdAndUserId(communityId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_MEMBER_NOT_FOUND", "Target user is not a member"));

        targetMember.setRole(request.role());
        return membership(memberRepository.save(targetMember));
    }

    private Community requireCommunity(Long id) {
        return communityRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMUNITY_NOT_FOUND));
    }

    private Community requireReadableCommunity(Long id, Long viewerId) {
        Community community = requireCommunity(id);
        requireDiscoveryAccess(community, viewerId);
        return community;
    }

    /**
     * Strict private-discovery proposal: unauthorized viewers receive the same
     * not-found response as a missing/deleted Community, with no metadata leaked.
     * No automatic SYSTEM_ADMIN override of private Community membership.
     */
    private void requireDiscoveryAccess(Community community, Long viewerId) {
        if (!community.isPrivateCommunity()) {
            return;
        }
        if (viewerId != null
                && (Objects.equals(community.getOwner().getId(), viewerId)
                    || memberRepository.existsByCommunityIdAndUserId(community.getId(), viewerId))) {
            return;
        }
        throw new ApiException(ErrorCode.COMMUNITY_NOT_FOUND);
    }

    private CommunityResponse toResponse(Community community, Long viewerId) {
        CommunityRole role = viewerId == null ? null : memberRepository.findByCommunityIdAndUserId(community.getId(), viewerId)
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
}
