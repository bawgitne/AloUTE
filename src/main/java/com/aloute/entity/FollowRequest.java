package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * BL-05 (issue #92): one row per (requester, target) pair. The unique pair constraint
 * is the final arbiter against duplicate requests under concurrent double clicks; a
 * re-request after REJECTED/CANCELED flips the same row back to PENDING.
 */
@Entity
@Table(name = "follow_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_follow_requests_pair",
                columnNames = {"requester_id", "target_id"}),
        indexes = @Index(name = "idx_follow_requests_target_status",
                columnList = "target_id, status"))
@Getter
@Setter
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_follow_requests_requester"))
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_follow_requests_target"))
    private User target;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private FollowRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
