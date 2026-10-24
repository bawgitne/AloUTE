package com.aloute.repository;

import com.aloute.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {
    List<PostMedia> findByPost_IdOrderByPositionAsc(Long postId);

    /**
     * Batch lookup for list assembly. The media asset is fetch-joined so the
     * assembler does not trigger one lazy init per distinct asset.
     */
    @Query("""
            SELECT pm FROM PostMedia pm
            JOIN FETCH pm.media
            WHERE pm.post.id IN :postIds
            ORDER BY pm.post.id ASC, pm.position ASC
            """)
    List<PostMedia> findByPost_IdInOrderByPost_IdAscPositionAsc(@Param("postIds") List<Long> postIds);
}
