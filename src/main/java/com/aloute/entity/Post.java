package com.aloute.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_posts_user"))
    private User user;
    @Column(nullable = false, length = 4000)
    private String content;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_post_id", foreignKey = @ForeignKey(name = "fk_posts_parent"))
    private Post parentPost;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quoted_post_id", foreignKey = @ForeignKey(name = "fk_posts_quoted"))
    private Post quotedPost;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", foreignKey = @ForeignKey(name = "fk_posts_community"))
    private Community community;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PostVisibility visibility = PostVisibility.PUBLIC;
    @Column(name = "allow_reply", nullable = false)
    private boolean allowReply = true;
    @Column(name = "allow_quote", nullable = false)
    private boolean allowQuote = true;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "reply_count", nullable = false)
    private long replyCount;
    @Column(name = "repost_count", nullable = false)
    private long repostCount;
    @Column(name = "quote_count", nullable = false)
    private long quoteCount;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist void prePersist() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Post getParentPost() { return parentPost; }
    public void setParentPost(Post parentPost) { this.parentPost = parentPost; }
    public Post getQuotedPost() { return quotedPost; }
    public void setQuotedPost(Post quotedPost) { this.quotedPost = quotedPost; }
    public Community getCommunity() { return community; }
    public void setCommunity(Community community) { this.community = community; }
    public PostVisibility getVisibility() { return visibility; }
    public void setVisibility(PostVisibility visibility) { this.visibility = visibility; }
    public boolean isAllowReply() { return allowReply; }
    public void setAllowReply(boolean allowReply) { this.allowReply = allowReply; }
    public boolean isAllowQuote() { return allowQuote; }
    public void setAllowQuote(boolean allowQuote) { this.allowQuote = allowQuote; }
    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    public long getReplyCount() { return replyCount; }
    public void setReplyCount(long replyCount) { this.replyCount = replyCount; }
    public long getRepostCount() { return repostCount; }
    public void setRepostCount(long repostCount) { this.repostCount = repostCount; }
    public long getQuoteCount() { return quoteCount; }
    public void setQuoteCount(long quoteCount) { this.quoteCount = quoteCount; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
