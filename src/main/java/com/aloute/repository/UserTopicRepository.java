package com.aloute.repository;

import com.aloute.entity.UserTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTopicRepository extends JpaRepository<UserTopic, Long> {
    boolean existsByUserIdAndTopicId(Long userId, Long topicId);
    Optional<UserTopic> findByUserIdAndTopicId(Long userId, Long topicId);
    void deleteByUserIdAndTopicId(Long userId, Long topicId);
}
