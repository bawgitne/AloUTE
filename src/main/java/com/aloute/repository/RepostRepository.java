package com.aloute.repository;

import com.aloute.entity.Repost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepostRepository extends JpaRepository<Repost, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    Optional<Repost> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);

    @Query("select r.post.id from Repost r where r.post.id in :postIds and r.user.id = :userId")
    List<Long> findRepostedPostIds(@Param("postIds") Collection<Long> postIds, @Param("userId") Long userId);
}
