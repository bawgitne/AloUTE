package com.aloute.repository;

import com.aloute.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c JOIN c.members m WHERE m.user.id = :userId ORDER BY c.updatedAt DESC")
    Page<Conversation> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT c FROM Conversation c
        JOIN c.members m1
        JOIN c.members m2
        WHERE c.type = com.aloute.entity.ConversationType.DIRECT
          AND m1.user.id = :user1Id
          AND m2.user.id = :user2Id
    """)
    Optional<Conversation> findDirectConversationBetween(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
}
