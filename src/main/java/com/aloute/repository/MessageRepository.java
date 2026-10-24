package com.aloute.repository;

import com.aloute.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // createdAt DESC + id DESC: deterministic page order when rows share a timestamp
    Page<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversationIdAndIdGreaterThan(Long conversationId, Long messageId);

    long countByConversationId(Long conversationId);
}
