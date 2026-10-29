package com.aloute.entity;

/**
 * BL-05 (issue #92): lifecycle of a private-account follow request.
 *
 * <p>PENDING — created when the requester asked to follow a private account; grants no
 * visibility and no counter movement. ACCEPTED — the target approved the request and a
 * Follow row now exists. REJECTED — the target declined. CANCELED — the requester
 * withdrew the request (or the account went public and the request was invalidated).</p>
 */
public enum FollowRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED
}
