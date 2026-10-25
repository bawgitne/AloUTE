package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter
@Setter
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
    /**
     * Community post approval lifecycle. Null means the post is not subject to
     * community approval (non-community post or legacy row) and reads as APPROVED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 16)
    private PostModerationStatus moderationStatus;
    /** Moderator-supplied reason shown to the author when a submission is REJECTED. */
    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;
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
}
