package com.aloute.repository;

import com.aloute.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /**
     * Batch lookup for list assembly. The user is fetch-joined so building
     * UserSummary rows does not trigger one lazy init per author.
     */
    @Query("""
            SELECT p FROM Profile p
            JOIN FETCH p.user
            WHERE p.userId IN :userIds
            """)
    List<Profile> findWithUserByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /**
     * BL-05: atomic followers/following counter advance — never read-modify-write, so
     * concurrent follows/approvals cannot lose increments.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.followersCount = p.followersCount + 1 WHERE p.userId = :userId")
    int incrementFollowersCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.followingCount = p.followingCount + 1 WHERE p.userId = :userId")
    int incrementFollowingCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.followersCount = CASE WHEN p.followersCount > 0 THEN p.followersCount - 1 ELSE 0 END WHERE p.userId = :userId")
    int decrementFollowersCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.followingCount = CASE WHEN p.followingCount > 0 THEN p.followingCount - 1 ELSE 0 END WHERE p.userId = :userId")
    int decrementFollowingCount(@Param("userId") Long userId);

     * Semantic (issue #92 decision): counts ROOT posts ever created by the user —
     * quotes and community posts included, replies excluded — and is never decremented
     * on soft delete. Concurrent creations therefore cannot lose increments.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.postsCount = p.postsCount + 1 WHERE p.userId = :userId")
    int incrementPostsCount(@Param("userId") Long userId);
}
