package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "topics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_topics_name", columnNames = "name"),
        @UniqueConstraint(name = "uk_topics_slug", columnNames = "slug")
})
@Getter
@Setter
public class Topic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 120)
    private String slug;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { createdAt = Instant.now(); }
}
