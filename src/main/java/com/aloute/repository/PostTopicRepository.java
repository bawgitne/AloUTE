package com.aloute.repository;

import com.aloute.entity.PostTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostTopicRepository extends JpaRepository<PostTopic, Long> {
    List<PostTopic> findByPost_Id(Long postId);
    List<PostTopic> findByPost_IdIn(List<Long> postIds);
}
