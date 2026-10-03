package com.aloute.service.impl;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SocialServiceImpl implements SocialService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public SocialServiceImpl(FollowRepository followRepository, UserRepository userRepository,
                             ProfileRepository profileRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

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
    private UserSummary summary(User u) { Profile p=requireProfile(u.getId()); return new UserSummary(u.getId(),u.getUsername(),p.getDisplayName(),p.getAvatarUrl(),u.isVerified()); }
    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found")); }
    private Profile requireProfile(Long id) { return profileRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User profile not found")); }
}
