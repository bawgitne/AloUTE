package com.aloute.repository;

import com.aloute.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    @Query(value = "select * from sp_list_topics(:keyword, :pOffset, :pLimit)", nativeQuery = true)
    List<Topic> listTopicsFromSp(@Param("keyword") String keyword, @Param("pOffset") int offset, @Param("pLimit") int limit);

    @Query(value = "select sp_count_topics(:keyword)", nativeQuery = true)
    long countTopicsFromSp(@Param("keyword") String keyword);

    @Query(value = "select * from sp_get_topic(:topicId)", nativeQuery = true)
    Optional<Topic> getTopicFromSp(@Param("topicId") Long topicId);

    @Query(value = "select sp_follow_topic(:userId, :topicId)", nativeQuery = true)
    Long followTopicFromSp(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Query(value = "select sp_unfollow_topic(:userId, :topicId)", nativeQuery = true)
    Long unfollowTopicFromSp(@Param("userId") Long userId, @Param("topicId") Long topicId);
}
