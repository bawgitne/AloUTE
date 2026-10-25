package com.aloute.repository;

import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long>, JpaSpecificationExecutor<ModerationAction> {

    Page<ModerationAction> findByTargetTypeAndTargetId(ModerationTargetType targetType, Long targetId, Pageable pageable);

    List<ModerationAction> findTop10ByOrderByCreatedAtDesc();

    @Query("""
        SELECT ma FROM ModerationAction ma JOIN FETCH ma.actor
        WHERE ma.communityId = :communityId
        ORDER BY ma.createdAt DESC
        """)
    List<ModerationAction> findRecentByCommunityId(@Param("communityId") Long communityId, Pageable pageable);
}
