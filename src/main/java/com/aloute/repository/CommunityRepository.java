package com.aloute.repository;

import com.aloute.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    @Query(value = "select * from sp_list_communities(:keyword, :pOffset, :pLimit)", nativeQuery = true)
    List<Community> listCommunitiesFromSp(@Param("keyword") String keyword, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_communities(:keyword)", nativeQuery = true)
    long countCommunitiesFromSp(@Param("keyword") String keyword);

    @Query(value = "select * from sp_get_community(:communityId)", nativeQuery = true)
    Optional<Community> getCommunityFromSp(@Param("communityId") Long communityId);

    @Query(value = "select sp_create_community(:ownerId, :name, :slug, :description, :isPrivate, :avatarMediaId, :coverMediaId)", nativeQuery = true)
    Long createCommunityFromSp(@Param("ownerId") Long ownerId,
                               @Param("name") String name,
                               @Param("slug") String slug,
                               @Param("description") String description,
                               @Param("isPrivate") boolean isPrivate,
                               @Param("avatarMediaId") Long avatarMediaId,
                               @Param("coverMediaId") Long coverMediaId);

    @Query(value = "select sp_update_community(:communityId, :userId, :name, :description, :isPrivate, :avatarMediaId, :coverMediaId)", nativeQuery = true)
    Long updateCommunityFromSp(@Param("communityId") Long communityId,
                               @Param("userId") Long userId,
                               @Param("name") String name,
                               @Param("description") String description,
                               @Param("isPrivate") Boolean isPrivate,
                               @Param("avatarMediaId") Long avatarMediaId,
                               @Param("coverMediaId") Long coverMediaId);

    @Query(value = "select sp_delete_community(:communityId, :userId)", nativeQuery = true)
    Long deleteCommunityFromSp(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
