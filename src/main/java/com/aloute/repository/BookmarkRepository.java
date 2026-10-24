package com.aloute.repository;

import com.aloute.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long>, JpaSpecificationExecutor<Bookmark> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    Optional<Bookmark> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select b.post.id from Bookmark b where b.post.id in :postIds and b.user.id = :userId")
    List<Long> findBookmarkedPostIds(@Param("postIds") Collection<Long> postIds, @Param("userId") Long userId);
}
