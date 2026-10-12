package com.aloute.repository;

import com.aloute.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    Page<Post> findByParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByParentPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long parentPostId, Pageable pageable);

    Page<Post> findByQuotedPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long quotedPostId, Pageable pageable);

    Page<Post> findByUserIdAndParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.community.id = :communityId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Page<Post> findByCommunityId(@Param("communityId") Long communityId, Pageable pageable);

    @Query("SELECT pt.post FROM PostTopic pt WHERE pt.topic.id = :topicId AND pt.post.deletedAt IS NULL ORDER BY pt.post.createdAt DESC")
    Page<Post> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds AND p.parentPost IS NULL AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Page<Post> findByFollowingUsers(@Param("userIds") Collection<Long> userIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.parentPost IS NULL AND p.deletedAt IS NULL ORDER BY (p.likeCount * 2 + p.repostCount * 3 + p.replyCount) DESC, p.createdAt DESC")
    Page<Post> findExplorePosts(Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        WHERE p.deletedAt IS NULL AND p.parentPost IS NULL
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.createdAt DESC
    """)
    Page<Post> searchPosts(@Param("keyword") String keyword, Pageable pageable);

    long countByParentPostIsNullAndDeletedAtIsNull();

    long countByParentPostIdAndDeletedAtIsNull(Long parentPostId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.community.id = :communityId AND p.deletedAt IS NULL")
    long countByCommunityId(@Param("communityId") Long communityId);

    @Query("SELECT COUNT(pt) FROM PostTopic pt WHERE pt.topic.id = :topicId AND pt.post.deletedAt IS NULL")
    long countByTopicId(@Param("topicId") Long topicId);

    long countByUserIdAndParentPostIsNullAndDeletedAtIsNull(Long userId);
}
