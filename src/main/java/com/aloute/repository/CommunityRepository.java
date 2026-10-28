package com.aloute.repository;

import com.aloute.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findByIdAndDeletedAtIsNull(Long id);
    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);

    /**
     * Atomic member-count increment. A bulk UPDATE avoids the read-modify-write
     * lost-update race when concurrent transactions join the same community.
     * clearAutomatically evicts the stale first-level-cache entities so subsequent
     * reads observe the new count.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Community c SET c.memberCount = c.memberCount + 1 WHERE c.id = :communityId")
    int incrementMemberCount(@Param("communityId") Long communityId);

    /** Atomic member-count decrement, floored at zero to mirror the previous Math.max guard. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Community c SET c.memberCount = CASE WHEN c.memberCount > 0 THEN c.memberCount - 1 ELSE 0 END WHERE c.id = :communityId")
    int decrementMemberCount(@Param("communityId") Long communityId);

    /** Atomic post-count increment for concurrent post creations in one community. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Community c SET c.postCount = c.postCount + 1 WHERE c.id = :communityId")
    int incrementPostCount(@Param("communityId") Long communityId);

    @Query("""
        SELECT c FROM Community c
        WHERE c.deletedAt IS NULL
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.memberCount DESC, c.id DESC
    """)
    Page<Community> searchCommunities(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Viewer-scoped discovery. Restrict private communities in SQL before count/pagination;
     * viewerId -1 represents an unauthenticated guest (normal User IDs are positive).
     */
    @Query(value = """
        SELECT c FROM Community c
        WHERE c.deletedAt IS NULL
          AND (c.privateCommunity = false
               OR c.owner.id = :viewerId
               OR EXISTS (SELECT cm.id FROM CommunityMember cm
                          WHERE cm.community = c AND cm.user.id = :viewerId))
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.memberCount DESC, c.id DESC
    """, countQuery = """
        SELECT COUNT(c) FROM Community c
        WHERE c.deletedAt IS NULL
          AND (c.privateCommunity = false
               OR c.owner.id = :viewerId
               OR EXISTS (SELECT cm.id FROM CommunityMember cm
                          WHERE cm.community = c AND cm.user.id = :viewerId))
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<Community> searchVisibleCommunities(@Param("keyword") String keyword,
                                               @Param("viewerId") Long viewerId,
                                               Pageable pageable);

    @Query("""
        SELECT COUNT(c) FROM Community c
        WHERE c.deletedAt IS NULL
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    long countCommunities(@Param("keyword") String keyword);
}
