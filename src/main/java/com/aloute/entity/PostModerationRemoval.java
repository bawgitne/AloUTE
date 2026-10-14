package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "post_moderation_removals")
@Getter
@Setter
public class PostModerationRemoval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pmr_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remove_action_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pmr_remove_action"))
    private ModerationAction removeAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restore_action_id", foreignKey = @ForeignKey(name = "fk_pmr_restore_action"))
    private ModerationAction restoreAction;

    @Column(name = "removed_at", nullable = false)
    private Instant removedAt;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @PrePersist
    void prePersist() {
        if (removedAt == null) {
            removedAt = Instant.now();
        }
    }
}
