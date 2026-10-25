package com.aloute.repository;

import com.aloute.entity.PostModerationRemoval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostModerationRemovalRepository extends JpaRepository<PostModerationRemoval, Long> {

    Optional<PostModerationRemoval> findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(Long postId);

    Optional<PostModerationRemoval> findByIdAndPostId(Long id, Long postId);

    boolean existsByPostIdAndRestoredAtIsNull(Long postId);

    Page<PostModerationRemoval> findByPostId(Long postId, Pageable pageable);

    @Query("""
        SELECT COUNT(r) FROM PostModerationRemoval r
        WHERE r.post.community.id = :communityId AND r.restoredAt IS NULL
        """)
    long countActiveByCommunityId(@Param("communityId") Long communityId);
}
