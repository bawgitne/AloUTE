package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "profiles")
@Getter
@Setter
public class Profile {
    @Id @Column(name = "user_id")
    private Long userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_profiles_user"))
    private User user;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    @Column(length = 500)
    private String bio;
    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;
    @Column(length = 2048)
    private String website;
    @Column(name = "followers_count", nullable = false)
    private long followersCount;
    @Column(name = "following_count", nullable = false)
    private long followingCount;
    @Column(name = "posts_count", nullable = false)
    private long postsCount;
}
