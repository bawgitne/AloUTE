package com.aloute.repository;

import com.aloute.entity.PostModerationRemoval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostModerationRemovalRepository extends JpaRepository<PostModerationRemoval, Long> {

    Optional<PostModerationRemoval> findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(Long postId);

    Optional<PostModerationRemoval> findByIdAndPostId(Long id, Long postId);

    boolean existsByPostIdAndRestoredAtIsNull(Long postId);

    Page<PostModerationRemoval> findByPostId(Long postId, Pageable pageable);
}
