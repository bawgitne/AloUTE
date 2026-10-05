package com.aloute.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_members_user", columnNames = {"community_id", "user_id"})
})
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

    public Long getId() { return id; }
    public Community getCommunity() { return community; }
    public void setCommunity(Community community) { this.community = community; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public CommunityRole getRole() { return role; }
    public void setRole(CommunityRole role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
}
