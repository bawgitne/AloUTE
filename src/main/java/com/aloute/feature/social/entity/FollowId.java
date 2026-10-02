package com.aloute.feature.social.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FollowId implements Serializable {

    private Long followerId;
    private Long followingId;

    protected FollowId() {
    }

    public FollowId(Long followerId, Long followingId) {
        this.followerId = followerId;
        this.followingId = followingId;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public Long getFollowingId() {
        return followingId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FollowId that)) return false;
        return Objects.equals(followerId, that.followerId)
                && Objects.equals(followingId, that.followingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followingId);
    }
}
