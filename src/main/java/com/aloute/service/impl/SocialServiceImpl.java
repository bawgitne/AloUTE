package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.SocialRelationResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.Follow;
import com.aloute.entity.FollowId;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.SocialService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.aloute.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SocialServiceImpl implements SocialService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final com.aloute.repository.BlockRepository blockRepository;
    private final com.aloute.repository.MuteRepository muteRepository;
    private final com.aloute.repository.RestrictionRepository restrictionRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Override @Transactional
    public SocialRelationResponse follow(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) throw new ApiException(HttpStatus.CONFLICT,
                "FOLLOW_SELF_NOT_ALLOWED", "A user cannot follow themself");
        User follower = requireUser(currentUserId);
        User following = requireUser(targetUserId);
        if (following.isPrivateAccount()) throw new ApiException(HttpStatus.CONFLICT,
                "PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED",
                "Private-account follow request lifecycle is a documented domain gap");
        FollowId id = new FollowId(currentUserId, targetUserId);
        if (followRepository.existsById(id)) throw new ApiException(HttpStatus.CONFLICT,
                "FOLLOW_ALREADY_EXISTS", "Follow relationship already exists");
        Follow follow = new Follow(); follow.setId(id); follow.setFollower(follower); follow.setFollowing(following);
        followRepository.save(follow);
        // Counters are kept here for local/H2 use; Ngọc's DB trigger can later become the canonical updater.
        Profile fp=requireProfile(currentUserId), tp=requireProfile(targetUserId);
        fp.setFollowingCount(fp.getFollowingCount()+1); tp.setFollowersCount(tp.getFollowersCount()+1);
        if (notificationService != null) {
            try {
                notificationService.createNotification(targetUserId, currentUserId, "FOLLOW", "USER", currentUserId, follower.getUsername() + " started following you");
            } catch (Exception ignored) {}
        }
        return new SocialRelationResponse(targetUserId, true);
    }

    @Override @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        FollowId id = new FollowId(currentUserId,targetUserId);
        Follow f = followRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "FOLLOW_NOT_FOUND", "Follow relationship not found"));
        followRepository.delete(f);
        Profile fp=requireProfile(currentUserId), tp=requireProfile(targetUserId);
        fp.setFollowingCount(Math.max(0,fp.getFollowingCount()-1));
        tp.setFollowersCount(Math.max(0,tp.getFollowersCount()-1));
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

        // Sever mutual follow relationships if present
        followRepository.findById(new FollowId(currentUserId, targetUserId)).ifPresent(f -> {
            followRepository.delete(f);
            Profile fp = requireProfile(currentUserId);
            Profile tp = requireProfile(targetUserId);
            fp.setFollowingCount(Math.max(0, fp.getFollowingCount() - 1));
            tp.setFollowersCount(Math.max(0, tp.getFollowersCount() - 1));
        });
        followRepository.findById(new FollowId(targetUserId, currentUserId)).ifPresent(f -> {
            followRepository.delete(f);
            Profile tp = requireProfile(targetUserId);
            Profile fp = requireProfile(currentUserId);
            tp.setFollowingCount(Math.max(0, tp.getFollowingCount() - 1));
            fp.setFollowersCount(Math.max(0, fp.getFollowersCount() - 1));
        });

        com.aloute.entity.Block block = com.aloute.entity.Block.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();
        blockRepository.save(block);
        return new SocialRelationResponse(targetUserId, true);
    }

    @Override @Transactional
    public void unblock(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        com.aloute.entity.Block b = blockRepository.findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "Block relationship not found"));
        blockRepository.delete(b);
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
    public void unmute(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        com.aloute.entity.Mute m = muteRepository.findByMuterIdAndMutedId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MUTE_NOT_FOUND", "Mute relationship not found"));
        muteRepository.delete(m);
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

    private UserSummary summary(User u) { Profile p=requireProfile(u.getId()); return new UserSummary(u.getId(),u.getUsername(),p.getDisplayName(),p.getAvatarUrl(),u.isVerified()); }
    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found")); }
    private Profile requireProfile(Long id) { return profileRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User profile not found")); }
}
