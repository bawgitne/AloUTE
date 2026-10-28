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
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.CommunityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final PostRepository postRepository;

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

        attachCommunityMedia(community, request.avatarMediaId(), request.coverMediaId(), ownerId);

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

        // Approval policy is an owner-only setting; admins keep every other update right.
        if (request.requirePostApproval() != null
                && request.requirePostApproval() != community.isRequirePostApproval()) {
            if (!Objects.equals(community.getOwner().getId(), userId)) {
                throw new ApiException(ErrorCode.COMMUNITY_OWNER_REQUIRED,
                        "Only the community owner can change the post approval setting");
            }
            community.setRequirePostApproval(request.requirePostApproval());
        }

        if (request.name() != null) community.setName(request.name());
        if (request.description() != null) community.setDescription(request.description());
        if (request.isPrivate() != null) community.setPrivateCommunity(request.isPrivate());

        attachCommunityMedia(community, request.avatarMediaId(), request.coverMediaId(), userId);

        return toResponse(communityRepository.save(community), userId);
    }

    /**
     * Community avatar/cover media must belong to the acting user and still exist
     * (same ownership rule as post media and profile avatar/cover): attaching
     * another user's asset or a soft-deleted asset is a 404, never a silent attach.
     */
    private void attachCommunityMedia(Community community, Long avatarMediaId, Long coverMediaId, Long actorId) {
        if (avatarMediaId != null) {
            MediaAsset media = mediaAssetRepository.findByIdAndOwner_IdAndDeletedAtIsNull(avatarMediaId, actorId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Avatar media not found"));
            community.setAvatarUrl(media.getUrl());
            media.setAttachedAt(Instant.now());
        }
        if (coverMediaId != null) {
            MediaAsset media = mediaAssetRepository.findByIdAndOwner_IdAndDeletedAtIsNull(coverMediaId, actorId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Cover media not found"));
            community.setCoverUrl(media.getUrl());
            media.setAttachedAt(Instant.now());
        }
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

        communityRepository.incrementMemberCount(communityId);

        return membership(savedMember);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CommunityMembershipResponse>> members(Long communityId, int page, int size, Long viewerId) {
        return members(communityId, null, page, size, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CommunityMembershipResponse>> members(Long communityId, String q, int page, int size, Long viewerId) {
        requireReadableCommunity(communityId, viewerId);
        String keyword = q != null && !q.isBlank() ? q.trim() : null;
        Page<CommunityMember> membersPage = memberRepository.searchMembers(
                communityId, keyword,
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "joinedAt")));
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
        communityRepository.decrementMemberCount(communityId);
    }

    @Override
    @Transactional
    public CommunityMembershipResponse updateRole(Long actorId, Long communityId, Long targetUserId, UpdateCommunityRoleRequest request) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, actorId);

        // Role changes (promote/demote ADMIN) are owner-only: an ADMIN-role member
        // must never be able to grant or revoke community privileges.
        if (!Objects.equals(community.getOwner().getId(), actorId)) {
            throw new ApiException(ErrorCode.COMMUNITY_ROLE_CHANGE_NOT_ALLOWED,
                    "Only the community owner can change member roles");
        }
        if (Objects.equals(community.getOwner().getId(), targetUserId)) {
            throw new ApiException(ErrorCode.COMMUNITY_ROLE_CHANGE_NOT_ALLOWED,
                    "The community owner role cannot be changed here");
        }

        CommunityMember targetMember = memberRepository.findByCommunityIdAndUserId(communityId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_MEMBER_NOT_FOUND", "Target user is not a member"));

        targetMember.setRole(request.role());
        return membership(memberRepository.save(targetMember));
    }

    @Override
    @Transactional
    public void removeMember(Long actorId, Long communityId, Long targetUserId) {
        Community community = requireCommunity(communityId);
        requireDiscoveryAccess(community, actorId);

        boolean actorIsOwner = Objects.equals(community.getOwner().getId(), actorId);
        CommunityMember actorMember = memberRepository.findByCommunityIdAndUserId(communityId, actorId)
                .orElse(null);
        boolean actorIsAdminRole = actorMember != null && actorMember.getRole() == CommunityRole.ADMIN;
        if (!actorIsOwner && !actorIsAdminRole) {
            throw new ApiException(ErrorCode.COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED,
                    "Only the community owner or admins can remove members");
        }

        // The owner can never be removed; removing yourself is "leave", not moderation.
        if (Objects.equals(community.getOwner().getId(), targetUserId) || Objects.equals(actorId, targetUserId)) {
            throw new ApiException(ErrorCode.COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED,
                    "Use leave-community instead; the owner cannot be removed");
        }

        CommunityMember targetMember = memberRepository.findByCommunityIdAndUserId(communityId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMUNITY_MEMBER_NOT_FOUND));

        // Admins manage ordinary members only; removing another admin (or any admin
        // row) is reserved to the owner to prevent privilege struggles.
        if (!actorIsOwner && targetMember.getRole() == CommunityRole.ADMIN) {
            throw new ApiException(ErrorCode.COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED,
                    "Only the community owner can remove an admin");
        }

        memberRepository.delete(targetMember);
        communityRepository.decrementMemberCount(communityId);
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
        boolean canManage = owner || role == CommunityRole.ADMIN;
        // The pending counter is management-only metadata: hidden from everyone except
        // owner/admins of this community (never leaks to ordinary members or guests).
        long pendingPostCount = canManage && community.isRequirePostApproval()
                ? postRepository.countPendingByCommunityId(community.getId())
                : 0L;
        return new CommunityResponse(community.getId(), community.getOwner().getId(), community.getName(),
                community.getSlug(), community.getDescription(), community.getAvatarUrl(), community.getCoverUrl(),
                community.isPrivateCommunity(), community.getMemberCount(), community.getPostCount(), role, owner,
                community.isRequirePostApproval(), canManage, pendingPostCount);
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
