package com.aloute.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "follows")
public class Follow {
    @EmbeddedId
    private FollowId id;
    @MapsId("followerId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_follows_follower"))
    private User follower;
    @MapsId("followingId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false, foreignKey = @ForeignKey(name = "fk_follows_following"))
    private User following;

    public FollowId getId() { return id; }
    public void setId(FollowId id) { this.id = id; }
    public User getFollower() { return follower; }
    public void setFollower(User follower) { this.follower = follower; }
    public User getFollowing() { return following; }
    public void setFollowing(User following) { this.following = following; }
}
