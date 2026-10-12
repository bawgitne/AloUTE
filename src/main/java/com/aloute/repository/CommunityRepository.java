package com.aloute.repository;

import com.aloute.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findByIdAndDeletedAtIsNull(Long id);
    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);

    @Query("""
        SELECT c FROM Community c
        WHERE c.deletedAt IS NULL
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.memberCount DESC, c.id DESC
    """)
    Page<Community> searchCommunities(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT COUNT(c) FROM Community c
        WHERE c.deletedAt IS NULL
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    long countCommunities(@Param("keyword") String keyword);
}
