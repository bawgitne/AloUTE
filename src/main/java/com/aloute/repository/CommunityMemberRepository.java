package com.aloute.repository;

import com.aloute.entity.CommunityMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
