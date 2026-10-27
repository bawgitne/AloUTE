package com.aloute.entity;

/**
 * Moderation lifecycle of a Community post.
 *
 * <p>Null (legacy rows / non-community posts) is treated as APPROVED by every
 * read path: approval only applies to Community posts created after a Community
 * enabled {@code requirePostApproval}.</p>
 */
public enum PostModerationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
