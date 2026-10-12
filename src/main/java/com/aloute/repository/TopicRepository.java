package com.aloute.repository;

import com.aloute.entity.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findBySlug(String slug);
    Optional<Topic> findByName(String name);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);

    @Query("""
        SELECT t FROM Topic t
        WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY t.name ASC
    """)
    Page<Topic> searchTopics(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT COUNT(t) FROM Topic t
        WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    long countTopics(@Param("keyword") String keyword);
}
