package com.aloute.repository;

import com.aloute.entity.FollowRequest;
import com.aloute.entity.FollowRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    Optional<FollowRequest> findByRequesterIdAndTargetId(Long requesterId, Long targetId);

    Optional<FollowRequest> findByIdAndStatus(Long id, FollowRequestStatus status);

    Page<FollowRequest> findByTargetIdAndStatusOrderByCreatedAtDesc(Long targetId, FollowRequestStatus status, Pageable pageable);

    boolean existsByRequesterIdAndTargetIdAndStatus(Long requesterId, Long targetId, FollowRequestStatus status);

    long countByTargetIdAndStatus(Long targetId, FollowRequestStatus status);

    /**
     * Optimistic status transition: the update only lands while the row is still in the
     * expected state, so a concurrent approve and cancel (or a double approve) cannot
     * both succeed — exactly one writer sees a non-zero affected-row count.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FollowRequest fr
            SET fr.status = :toStatus, fr.updatedAt = :now
            WHERE fr.id = :id AND fr.status = :fromStatus
            """)
    int transition(@Param("id") Long id,
                   @Param("fromStatus") FollowRequestStatus fromStatus,
                   @Param("toStatus") FollowRequestStatus toStatus,
                   @Param("now") Instant now);

    /**
     * Cancels every request the given account still has pending toward it. Used by the
     * documented private-to-public policy: going public must not silently auto-approve
     * a backlog of old requests, so they are invalidated instead.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FollowRequest fr
            SET fr.status = 'CANCELED', fr.updatedAt = :now
            WHERE fr.target.id = :targetId AND fr.status = 'PENDING'
            """)
    int cancelAllPendingForTarget(@Param("targetId") Long targetId, @Param("now") Instant now);
}
