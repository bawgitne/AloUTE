package com.aloute.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "mutes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mutes_pair", columnNames = {"muter_id", "muted_id"})
}, indexes = {
        @Index(name = "idx_mutes_muter", columnList = "muter_id"),
        @Index(name = "idx_mutes_muted", columnList = "muted_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muter_id", nullable = false)
    private User muter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muted_id", nullable = false)
    private User muted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
