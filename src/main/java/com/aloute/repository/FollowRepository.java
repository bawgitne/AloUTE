package com.aloute.repository;

import com.aloute.entity.Follow;
import com.aloute.entity.FollowId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {
    Page<Follow> findByFollowing_Id(Long followingUserId, Pageable pageable);
    Page<Follow> findByFollower_Id(Long followerUserId, Pageable pageable);
}
