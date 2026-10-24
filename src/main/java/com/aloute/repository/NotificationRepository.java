package com.aloute.repository;

import com.aloute.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // createdAt DESC + id DESC: deterministic page order when rows share a timestamp
    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(Long recipientId, Collection<String> types, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    void markAllAsRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipient.id = :recipientId")
    void deleteByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);
}
