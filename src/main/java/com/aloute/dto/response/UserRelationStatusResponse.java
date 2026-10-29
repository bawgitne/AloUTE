package com.aloute.dto.response;

/**
 * One-call relation snapshot used by the profile follow button and other clients.
 *
 * @param targetUserId         the user being inspected
 * @param following            an approved Follow relationship exists (viewer → target)
 * @param blocked              the viewer blocks the target
 * @param muted                the viewer mutes the target
 * @param restricted           the viewer restricts the target
 * @param followRequestPending a PENDING private-account follow request from the viewer
 *                             toward the target exists; BL-05, issue #92
 */
public record UserRelationStatusResponse(
        Long targetUserId,
        boolean following,
        boolean blocked,
        boolean muted,
        boolean restricted,
        boolean followRequestPending
) {
    /** Compatibility constructor for existing callers: no follow request involved. */
    public UserRelationStatusResponse(Long targetUserId, boolean following, boolean blocked,
                                      boolean muted, boolean restricted) {
        this(targetUserId, following, blocked, muted, restricted, false);
    }
}
