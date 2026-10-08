package com.aloute.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookmarks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookmarks_user_post", columnNames = {"user_id", "post_id"})
})
public class Bookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void prePersist() { createdAt = Instant.now(); }
    public Long getId() { return id; }
}
