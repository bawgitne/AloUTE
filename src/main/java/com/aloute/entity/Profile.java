package com.aloute.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "profiles")
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

    public Long getUserId() { return userId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public long getFollowersCount() { return followersCount; }
    public void setFollowersCount(long followersCount) { this.followersCount = followersCount; }
    public long getFollowingCount() { return followingCount; }
    public void setFollowingCount(long followingCount) { this.followingCount = followingCount; }
    public long getPostsCount() { return postsCount; }
    public void setPostsCount(long postsCount) { this.postsCount = postsCount; }
}
