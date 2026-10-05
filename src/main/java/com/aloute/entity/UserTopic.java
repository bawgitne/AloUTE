package com.aloute.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_topics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_topics_user_topic", columnNames = {"user_id", "topic_id"})
})
public class UserTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void prePersist() { createdAt = Instant.now(); }
    public Long getId() { return id; }
}
