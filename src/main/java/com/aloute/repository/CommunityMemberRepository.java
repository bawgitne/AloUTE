package com.aloute.repository;

import com.aloute.entity.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    @Query(value = "select * from sp_get_community_membership(:communityId, :userId)", nativeQuery = true)
    Optional<CommunityMember> getMembershipFromSp(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Query(value = "select * from sp_list_community_members(:communityId, :pOffset, :pLimit)", nativeQuery = true)
    List<CommunityMember> listMembersFromSp(@Param("communityId") Long communityId, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_community_members(:communityId)", nativeQuery = true)
    long countMembersFromSp(@Param("communityId") Long communityId);

    @Query(value = "select sp_join_community(:communityId, :userId)", nativeQuery = true)
    Long joinCommunityFromSp(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Query(value = "select sp_leave_community(:communityId, :userId)", nativeQuery = true)
    Long leaveCommunityFromSp(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Query(value = "select sp_update_community_role(:communityId, :actorId, :targetUserId, :role)", nativeQuery = true)
    Long updateRoleFromSp(@Param("communityId") Long communityId,
                          @Param("actorId") Long actorId,
                          @Param("targetUserId") Long targetUserId,
                          @Param("role") String role);
}
