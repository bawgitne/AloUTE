package com.aloute.dto.response;

/**
 * Result of a follow/unfollow/follow-request mutation.
 *
 * @param targetUserId         the user the action was aimed at
 * @param active               true when a Follow relationship exists after the action
 * @param followRequestPending true when a PENDING follow request exists after the
 *                             action (private accounts); BL-05, issue #92
 */
public record SocialRelationResponse(Long targetUserId, boolean active, boolean followRequestPending) {

    /** Compatibility constructor for existing callers: no follow request involved. */
    public SocialRelationResponse(Long targetUserId, boolean active) {
        this(targetUserId, active, false);
    }
}
