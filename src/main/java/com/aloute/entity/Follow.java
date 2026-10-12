package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "follows")
@Getter
@Setter
public class Follow {
    @EmbeddedId
    private FollowId id;
    @MapsId("followerId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_follows_follower"))
    private User follower;
    @MapsId("followingId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false, foreignKey = @ForeignKey(name = "fk_follows_following"))
    private User following;
}
