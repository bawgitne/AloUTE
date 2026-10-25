package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "communities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_communities_slug", columnNames = "slug")
})
@Getter
@Setter
public class Community {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_communities_owner"))
    private User owner;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 100)
    private String slug;
    @Column(length = 2000)
    private String description;
    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;
    @Column(name = "cover_url", length = 2048)
    private String coverUrl;
    @Column(name = "is_private", nullable = false)
    private boolean privateCommunity;
    /** When true, ordinary member submissions require owner/admin approval before publishing. */
    @Column(name = "require_post_approval", nullable = false)
    private boolean requirePostApproval;
    @Column(name = "member_count", nullable = false)
    private long memberCount;
    @Column(name = "post_count", nullable = false)
    private long postCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist void prePersist() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
