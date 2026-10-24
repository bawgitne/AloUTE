package com.aloute.repository;

import com.aloute.entity.PostTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostTopicRepository extends JpaRepository<PostTopic, Long> {
    List<PostTopic> findByPost_Id(Long postId);

    /**
     * Batch lookup for list assembly. The topic is fetch-joined so the
     * assembler does not trigger one lazy init per distinct topic.
     */
    @Query("""
            SELECT pt FROM PostTopic pt
            JOIN FETCH pt.topic
            WHERE pt.post.id IN :postIds
            """)
    List<PostTopic> findByPost_IdIn(@Param("postIds") List<Long> postIds);
}
