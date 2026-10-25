package com.aloute.repository;

import com.aloute.entity.CommunityMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);
    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);
    Page<CommunityMember> findByCommunityIdOrderByJoinedAtDesc(Long communityId, Pageable pageable);
    long countByCommunityId(Long communityId);
    void deleteByCommunityIdAndUserId(Long communityId, Long userId);
    java.util.List<CommunityMember> findByUserId(Long userId);

    /**
     * Member search across username and profile display name (case-insensitive),
     * paged by join date. {@code q} null/blank returns the full member list.
     */
    @Query("""
        SELECT cm FROM CommunityMember cm JOIN FETCH cm.user u
        LEFT JOIN Profile p ON p.userId = u.id
        WHERE cm.community.id = :communityId
          AND (:q IS NULL OR :q = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(p.displayName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<CommunityMember> searchMembers(@Param("communityId") Long communityId,
                                        @Param("q") String q,
                                        Pageable pageable);
}
