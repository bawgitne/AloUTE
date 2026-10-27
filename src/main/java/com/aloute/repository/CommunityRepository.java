package com.aloute.repository;

import com.aloute.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    /**
     * BL-15: atomic lifetime post-count advance (bulk UPDATE). Semantic fixed in
     * PR #73: total posts ever created, never decremented on deletion.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Community c SET c.postCount = c.postCount + 1 WHERE c.id = :communityId")
    int incrementPostCount(@org.springframework.data.repository.query.Param("communityId") Long communityId);
    Optional<Community> findByIdAndDeletedAtIsNull(Long id);
    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);

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
