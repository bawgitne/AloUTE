package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "community_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_members_user", columnNames = {"community_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false, foreignKey = @ForeignKey(name = "fk_community_members_community"))
    private Community community;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_community_members_user"))
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommunityRole role = CommunityRole.MEMBER;
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist void prePersist() { joinedAt = Instant.now(); }

    public CommunityMember(Community community, User user, CommunityRole role) {
        this.community = community;
        this.user = user;
        this.role = role;
    }
}
