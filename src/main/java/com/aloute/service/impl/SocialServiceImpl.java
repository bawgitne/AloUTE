package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.FollowRequestSummaryResponse;
import com.aloute.dto.response.SocialRelationResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.Follow;
import com.aloute.entity.FollowId;
import com.aloute.entity.FollowRequest;
import com.aloute.entity.FollowRequestStatus;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.FollowRequestRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.SocialService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aloute.event.NotificationEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SocialServiceImpl implements SocialService {
    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final com.aloute.repository.BlockRepository blockRepository;
    private final com.aloute.repository.MuteRepository muteRepository;
    private final com.aloute.repository.RestrictionRepository restrictionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override @Transactional
    public SocialRelationResponse follow(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) throw new ApiException(HttpStatus.CONFLICT,
                "FOLLOW_SELF_NOT_ALLOWED", "A user cannot follow themself");
        User follower = requireUser(currentUserId);
        User following = requireUser(targetUserId);

        if (followRepository.existsById(new FollowId(currentUserId, targetUserId))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "FOLLOW_ALREADY_EXISTS", "Follow relationship already exists");
        }

        // BL-05: private accounts route through an explicit approval flow. No Follow
        // row, no counter movement and no FOLLOW notification until the target approves.
        if (following.isPrivateAccount()) {
            if (isBlockedEitherWay(currentUserId, targetUserId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BLOCKED_BY_RELATIONSHIP",
                        "Không thể gửi yêu cầu theo dõi giữa hai tài khoản đang chặn nhau");
            }
            FollowRequest request = upsertPendingRequest(currentUserId, targetUserId);
            eventPublisher.publishEvent(NotificationEvent.of(
                    targetUserId,
                    currentUserId,
                    "FOLLOW_REQUEST",
                    "USER",
                    currentUserId,
                    follower.getUsername() + " đã gửi yêu cầu theo dõi bạn"
            ));
            return new SocialRelationResponse(targetUserId, false, request.getStatus() == FollowRequestStatus.PENDING);
        }

        // Public account: follow directly. A stale pending request (left over from a
        // private-to-public switch the requester never revisited) is consumed here
        // instead of silently shadowing the now-public follow.
        followRequestRepository.findByRequesterIdAndTargetId(currentUserId, targetUserId)
                .ifPresent(request -> followRequestRepository.transition(
                        request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.ACCEPTED, Instant.now()));

        Follow follow = new Follow(); follow.setId(new FollowId(currentUserId, targetUserId));
        follow.setFollower(follower); follow.setFollowing(following);
        followRepository.save(follow);
        profileRepository.incrementFollowingCount(currentUserId);
        profileRepository.incrementFollowersCount(targetUserId);

        eventPublisher.publishEvent(NotificationEvent.of(
                targetUserId,
                currentUserId,
                "FOLLOW",
                "USER",
                currentUserId,
                follower.getUsername() + " started following you"
        ));

        return new SocialRelationResponse(targetUserId, true, false);
    }

    /**
     * Finds the pair row or creates it; re-requesting after reject/cancel re-arms
     * PENDING. A pre-check keeps normal double clicks idempotent; if two transactions
     * still race past it, the unique pair constraint rejects the loser with a clean
     * 409 — the session is poisoned after a constraint violation, so the loser must
     * not keep writing in the same transaction.
     */
    private FollowRequest upsertPendingRequest(Long requesterId, Long targetUserId) {
        FollowRequest existing = followRequestRepository.findByRequesterIdAndTargetId(requesterId, targetUserId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() != FollowRequestStatus.PENDING) {
                followRequestRepository.transition(existing.getId(), existing.getStatus(),
                        FollowRequestStatus.PENDING, Instant.now());
            }
            return existing;
        }

        FollowRequest created = new FollowRequest();
        created.setRequester(requireUser(requesterId));
        created.setTarget(requireUser(targetUserId));
        created.setStatus(FollowRequestStatus.PENDING);
        try {
            return followRequestRepository.saveAndFlush(created);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "FOLLOW_REQUEST_ALREADY_PENDING",
                    "Yêu cầu theo dõi đã được gửi trước đó");
        }
    }

    private boolean isBlockedEitherWay(Long a, Long b) {
        return blockRepository.existsByBlockerIdAndBlockedId(a, b)
                || blockRepository.existsByBlockerIdAndBlockedId(b, a);
    }

    @Override @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        FollowId id = new FollowId(currentUserId,targetUserId);
        if (followRepository.existsById(id)) {
            followRepository.deleteById(id);
            profileRepository.decrementFollowingCount(currentUserId);
            profileRepository.decrementFollowersCount(targetUserId);
            return;
        }
        // BL-05: with no Follow row the DELETE acts as "withdraw my pending request".
        followRequestRepository.findByRequesterIdAndTargetId(currentUserId, targetUserId)
                .filter(request -> request.getStatus() == FollowRequestStatus.PENDING)
                .ifPresent(request -> followRequestRepository.transition(
                        request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.CANCELED, Instant.now()));
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> followers(Long userId, Pageable pageable) {
        requireUser(userId); Page<Follow> p=followRepository.findByFollowing_Id(userId,pageable);
        return ApiResponse.page(p.getContent().stream().map(Follow::getFollower).map(this::summary).toList(), PageMeta.from(p));
    }
    @Override @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> following(Long userId, Pageable pageable) {
        requireUser(userId); Page<Follow> p=followRepository.findByFollower_Id(userId,pageable);
        return ApiResponse.page(p.getContent().stream().map(Follow::getFollowing).map(this::summary).toList(), PageMeta.from(p));
    }
    @Override @Transactional
    public SocialRelationResponse block(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "BLOCK_SELF_NOT_ALLOWED", "A user cannot block themself");
        }
        User blocker = requireUser(currentUserId);
        User blocked = requireUser(targetUserId);

        if (blockRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "BLOCK_ALREADY_EXISTS", "Block relationship already exists");
        }

        // Sever mutual follow relationships and pending follow requests if present
        followRepository.findById(new FollowId(currentUserId, targetUserId)).ifPresent(f -> {
            followRepository.delete(f);
            profileRepository.decrementFollowingCount(currentUserId);
            profileRepository.decrementFollowersCount(targetUserId);
        });
        followRepository.findById(new FollowId(targetUserId, currentUserId)).ifPresent(f -> {
            followRepository.delete(f);
            profileRepository.decrementFollowingCount(targetUserId);
            profileRepository.decrementFollowersCount(currentUserId);
        });
        // BL-05: a block must also terminate any outstanding private-account request
        // between the pair, in either direction.
        followRequestRepository.findByRequesterIdAndTargetId(currentUserId, targetUserId)
                .filter(request -> request.getStatus() == FollowRequestStatus.PENDING)
                .ifPresent(request -> followRequestRepository.transition(
                        request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.CANCELED, Instant.now()));
        followRequestRepository.findByRequesterIdAndTargetId(targetUserId, currentUserId)
                .filter(request -> request.getStatus() == FollowRequestStatus.PENDING)
                .ifPresent(request -> followRequestRepository.transition(
                        request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.CANCELED, Instant.now()));

        com.aloute.entity.Block block = com.aloute.entity.Block.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();
        blockRepository.save(block);
        return new SocialRelationResponse(targetUserId, true);
    }

    @Override @Transactional
    public SocialRelationResponse blockByUsername(Long currentUserId, String username) {
        if (username == null || username.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "Vui lòng nhập tên người dùng hợp lệ");
        }
        String clean = username.trim().startsWith("@") ? username.trim().substring(1) : username.trim();
        User target = userRepository.findByUsernameIgnoreCase(clean)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng @" + clean));
        return block(currentUserId, target.getId());
    }

    @Override @Transactional
    public void unblock(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        com.aloute.entity.Block b = blockRepository.findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "Block relationship not found"));
        blockRepository.delete(b);
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> blockedUsers(Long currentUserId, Pageable pageable) {
        requireUser(currentUserId);
        Page<com.aloute.entity.Block> p = blockRepository.findByBlockerId(currentUserId, pageable);
        return ApiResponse.page(p.getContent().stream().map(com.aloute.entity.Block::getBlocked).map(this::summary).toList(), PageMeta.from(p));
    }

    @Override @Transactional
    public SocialRelationResponse mute(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "MUTE_SELF_NOT_ALLOWED", "A user cannot mute themself");
        }
        User muter = requireUser(currentUserId);
        User muted = requireUser(targetUserId);

        if (muteRepository.existsByMuterIdAndMutedId(currentUserId, targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "MUTE_ALREADY_EXISTS", "Mute relationship already exists");
        }

        com.aloute.entity.Mute mute = com.aloute.entity.Mute.builder()
                .muter(muter)
                .muted(muted)
                .build();
        muteRepository.save(mute);
        return new SocialRelationResponse(targetUserId, true);
    }

    @Override @Transactional
    public SocialRelationResponse muteByUsername(Long currentUserId, String username) {
        if (username == null || username.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "Vui lòng nhập tên người dùng hợp lệ");
        }
        String clean = username.trim().startsWith("@") ? username.trim().substring(1) : username.trim();
        User target = userRepository.findByUsernameIgnoreCase(clean)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng @" + clean));
        return mute(currentUserId, target.getId());
    }

    @Override @Transactional
    public void unmute(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        com.aloute.entity.Mute m = muteRepository.findByMuterIdAndMutedId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MUTE_NOT_FOUND", "Mute relationship not found"));
        muteRepository.delete(m);
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> mutedUsers(Long currentUserId, Pageable pageable) {
        requireUser(currentUserId);
        Page<com.aloute.entity.Mute> p = muteRepository.findByMuterId(currentUserId, pageable);
        return ApiResponse.page(p.getContent().stream().map(com.aloute.entity.Mute::getMuted).map(this::summary).toList(), PageMeta.from(p));
    }

    @Override @Transactional
    public SocialRelationResponse restrict(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "RESTRICT_SELF_NOT_ALLOWED", "A user cannot restrict themself");
        }
        User restricter = requireUser(currentUserId);
        User restricted = requireUser(targetUserId);

        if (restrictionRepository.existsByRestricterIdAndRestrictedId(currentUserId, targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "RESTRICTION_ALREADY_EXISTS", "Restriction already exists");
        }

        com.aloute.entity.Restriction r = com.aloute.entity.Restriction.builder()
                .restricter(restricter)
                .restricted(restricted)
                .build();
        restrictionRepository.save(r);
        return new SocialRelationResponse(targetUserId, true);
    }

    @Override @Transactional
    public void unrestrict(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        com.aloute.entity.Restriction r = restrictionRepository.findByRestricterIdAndRestrictedId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESTRICTION_NOT_FOUND", "Restriction not found"));
        restrictionRepository.delete(r);
    }

    @Override
    @Transactional(readOnly = true)
    public com.aloute.dto.response.UserRelationStatusResponse getRelationStatus(Long currentUserId, Long targetUserId) {
        if (targetUserId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Target user not found");
        }
        requireUser(targetUserId);
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return new com.aloute.dto.response.UserRelationStatusResponse(targetUserId, false, false, false, false);
        }
        boolean following = followRepository.existsById(new FollowId(currentUserId, targetUserId));
        boolean blocked = blockRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId);
        boolean muted = muteRepository.existsByMuterIdAndMutedId(currentUserId, targetUserId);
        boolean restricted = restrictionRepository.existsByRestricterIdAndRestrictedId(currentUserId, targetUserId);
        boolean followRequestPending = followRequestRepository
                .existsByRequesterIdAndTargetIdAndStatus(currentUserId, targetUserId, FollowRequestStatus.PENDING);
        return new com.aloute.dto.response.UserRelationStatusResponse(
                targetUserId, following, blocked, muted, restricted, followRequestPending);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<FollowRequestSummaryResponse>> pendingFollowRequests(Long targetUserId, Pageable pageable) {
        requireUser(targetUserId);
        Page<FollowRequest> page = followRequestRepository
                .findByTargetIdAndStatusOrderByCreatedAtDesc(targetUserId, FollowRequestStatus.PENDING, pageable);
        List<FollowRequestSummaryResponse> list = page.getContent().stream()
                .map(request -> new FollowRequestSummaryResponse(
                        request.getId(),
                        summary(request.getRequester()),
                        request.getCreatedAt()))
                .toList();
        return ApiResponse.page(list, PageMeta.from(page));
    }

    @Override
    @Transactional
    public SocialRelationResponse approveFollowRequest(Long currentUserId, Long requestId) {
        FollowRequest request = requireOwnedRequest(currentUserId, requestId);
        // Optimistic PENDING -> ACCEPTED: a concurrent cancel/reject/second approve
        // flips the row first, and this update then affects zero rows -> 409.
        int updated = followRequestRepository.transition(
                request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.ACCEPTED, Instant.now());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FOLLOW_REQUEST_NOT_PENDING",
                    "Yêu cầu theo dõi không còn đang chờ duyệt");
        }

        // The requester may have followed through another path meanwhile (e.g. the
        // account went public); the unique Follow pair key keeps this idempotent-safe.
        FollowId followId = new FollowId(request.getRequester().getId(), currentUserId);
        if (!followRepository.existsById(followId)) {
            Follow follow = new Follow();
            follow.setId(followId);
            follow.setFollower(request.getRequester());
            follow.setFollowing(requireUser(currentUserId));
            followRepository.save(follow);
            profileRepository.incrementFollowingCount(request.getRequester().getId());
            profileRepository.incrementFollowersCount(currentUserId);
        }

        eventPublisher.publishEvent(NotificationEvent.of(
                request.getRequester().getId(),
                currentUserId,
                "FOLLOW_ACCEPTED",
                "USER",
                currentUserId,
                requireProfile(currentUserId).getDisplayName() + " đã chấp nhận yêu cầu theo dõi của bạn"
        ));

        return new SocialRelationResponse(request.getRequester().getId(), true, false);
    }

    @Override
    @Transactional
    public void rejectFollowRequest(Long currentUserId, Long requestId) {
        FollowRequest request = requireOwnedRequest(currentUserId, requestId);
        int updated = followRequestRepository.transition(
                request.getId(), FollowRequestStatus.PENDING, FollowRequestStatus.REJECTED, Instant.now());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FOLLOW_REQUEST_NOT_PENDING",
                    "Yêu cầu theo dõi không còn đang chờ duyệt");
        }
        // No notification on rejection by design: declining must stay silent.
    }

    /** Loads a request and enforces that only its target (the private-account owner) acts on it. */
    private FollowRequest requireOwnedRequest(Long currentUserId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "FOLLOW_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu theo dõi"));
        if (!request.getTarget().getId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FOLLOW_REQUEST_NOT_OWNED",
                    "Chỉ chủ tài khoản mới có thể xử lý yêu cầu này");
        }
        return request;
    }

    private UserSummary summary(User u) { Profile p=requireProfile(u.getId()); return new UserSummary(u.getId(),u.getUsername(),p.getDisplayName(),p.getAvatarUrl(),u.isVerified()); }
    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found")); }
    private Profile requireProfile(Long id) { return profileRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User profile not found")); }
}
