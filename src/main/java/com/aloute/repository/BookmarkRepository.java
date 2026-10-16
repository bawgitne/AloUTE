package com.aloute.repository;

import com.aloute.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long>, JpaSpecificationExecutor<Bookmark> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    Optional<Bookmark> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
