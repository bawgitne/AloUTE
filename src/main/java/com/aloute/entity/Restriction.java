package com.aloute.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "restrictions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_restrictions_pair", columnNames = {"restricter_id", "restricted_id"})
}, indexes = {
        @Index(name = "idx_restrictions_restricter", columnList = "restricter_id"),
        @Index(name = "idx_restrictions_restricted", columnList = "restricted_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restricter_id", nullable = false)
    private User restricter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restricted_id", nullable = false)
    private User restricted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
