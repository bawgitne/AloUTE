package com.aloute.repository;

import com.aloute.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);

    @Query("select pl.post.id from PostLike pl where pl.post.id in :postIds and pl.user.id = :userId")
    List<Long> findLikedPostIds(@Param("postIds") Collection<Long> postIds, @Param("userId") Long userId);
}
