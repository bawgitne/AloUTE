package com.aloute.feature.social.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.feature.social.dto.response.SocialRelationResponse;
import com.aloute.feature.social.entity.Follow;
import com.aloute.feature.social.entity.FollowId;
import com.aloute.feature.social.repository.FollowRepository;
import com.aloute.feature.user.dto.response.UserSummary;
import com.aloute.feature.user.entity.Profile;
import com.aloute.feature.user.entity.User;
import com.aloute.feature.user.mapper.UserMapper;
import com.aloute.feature.user.repository.ProfileRepository;
import com.aloute.feature.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserMapper userMapper;

    public FollowServiceImpl(
            FollowRepository followRepository,
            UserRepository userRepository,
            ProfileRepository profileRepository,
            UserMapper userMapper) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public SocialRelationResponse follow(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FOLLOW_SELF_NOT_ALLOWED",
                    "A user cannot follow themself");
        }

        User follower = requireUser(currentUserId);
        User following = requireUser(targetUserId);
        FollowId id = new FollowId(currentUserId, targetUserId);

        if (followRepository.existsById(id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FOLLOW_ALREADY_EXISTS",
                    "Follow relationship already exists");
        }

        Profile followerProfile = requireProfile(currentUserId);
        Profile followingProfile = requireProfile(targetUserId);

        Follow follow = new Follow();
        follow.setId(id);
        follow.setFollower(follower);
        follow.setFollowing(following);
        followRepository.save(follow);

        followerProfile.setFollowingCount(followerProfile.getFollowingCount() + 1);
        followingProfile.setFollowersCount(followingProfile.getFollowersCount() + 1);

        return new SocialRelationResponse(targetUserId, true);
    }

    @Override
    @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        FollowId id = new FollowId(currentUserId, targetUserId);
        Follow follow = followRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "FOLLOW_NOT_FOUND",
                        "Follow relationship not found"));

        Profile followerProfile = requireProfile(currentUserId);
        Profile followingProfile = requireProfile(targetUserId);

        followRepository.delete(follow);

        followerProfile.setFollowingCount(Math.max(0, followerProfile.getFollowingCount() - 1));
        followingProfile.setFollowersCount(Math.max(0, followingProfile.getFollowersCount() - 1));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> followers(Long userId, Pageable pageable) {
        requireUser(userId);
        Page<Follow> page = followRepository.findByFollowing_Id(userId, pageable);
        List<UserSummary> data = page.getContent().stream()
                .map(Follow::getFollower)
                .map(this::toSummary)
                .toList();
        return ApiResponse.page(data, PageMeta.from(page));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> following(Long userId, Pageable pageable) {
        requireUser(userId);
        Page<Follow> page = followRepository.findByFollower_Id(userId, pageable);
        List<UserSummary> data = page.getContent().stream()
                .map(Follow::getFollowing)
                .map(this::toSummary)
                .toList();
        return ApiResponse.page(data, PageMeta.from(page));
    }

    private UserSummary toSummary(User user) {
        Profile profile = requireProfile(user.getId());
        return userMapper.toSummary(user, profile);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Profile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User profile not found"));
    }
}
