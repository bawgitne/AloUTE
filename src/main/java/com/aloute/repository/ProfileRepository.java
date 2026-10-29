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
     * BL-15: atomic lifetime post-count advance (bulk UPDATE), never read-modify-write.
     * Semantic (issue #92 decision): counts ROOT posts ever created by the user —
     * quotes and community posts included, replies excluded — and is never decremented
     * on soft delete. Concurrent creations therefore cannot lose increments.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Profile p SET p.postsCount = p.postsCount + 1 WHERE p.userId = :userId")
    int incrementPostsCount(@Param("userId") Long userId);
}
