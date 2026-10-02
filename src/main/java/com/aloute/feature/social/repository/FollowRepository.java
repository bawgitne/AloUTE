package com.aloute.feature.social.repository;

import com.aloute.feature.social.entity.Follow;
import com.aloute.feature.social.entity.FollowId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {
    Page<Follow> findByFollowing_Id(Long followingUserId, Pageable pageable);
    Page<Follow> findByFollower_Id(Long followerUserId, Pageable pageable);
}
