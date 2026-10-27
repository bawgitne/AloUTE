package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mod_actions_actor"))
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private ReportReason reasonCode;

    @Column(name = "reason_detail", nullable = false, length = 1000)
    private String reasonDetail;

    @Column(name = "related_report_id")
    private Long relatedReportId;
    /** Community scope for community-moderation actions (approve/reject/remove of community posts). */
    @Column(name = "community_id")
    private Long communityId;

    @Column(name = "metadata", length = 4000)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
