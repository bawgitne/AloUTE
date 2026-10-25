package com.aloute.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // --- COMMON & REQUEST-LEVEL ---
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "Request validation failed"),
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "Bad request"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Requested resource not found"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type not supported"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "Access is denied"),
    CONFLICT("CONFLICT", HttpStatus.CONFLICT, "Request conflicts with current state"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server failure"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable"),

    // --- AUTH ---
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_UNAUTHENTICATED("AUTH_UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "Authentication required or credential invalid"),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "Authentication token has expired"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", HttpStatus.FORBIDDEN, "Operation is not permitted"),
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", HttpStatus.FORBIDDEN, "Account is locked or suspended"),
    AUTH_ACCOUNT_NOT_VERIFIED("AUTH_ACCOUNT_NOT_VERIFIED", HttpStatus.FORBIDDEN, "Account is not verified"),
    INVALID_OTP("INVALID_OTP", HttpStatus.BAD_REQUEST, "Invalid or expired OTP"),
    OTP_EXPIRED("OTP_EXPIRED", HttpStatus.BAD_REQUEST, "OTP has expired"),
    OTP_MAX_ATTEMPTS_EXCEEDED("OTP_MAX_ATTEMPTS_EXCEEDED", HttpStatus.BAD_REQUEST, "Maximum verification attempts exceeded"),
    ACCOUNT_ALREADY_VERIFIED("ACCOUNT_ALREADY_VERIFIED", HttpStatus.BAD_REQUEST, "Account is already verified"),

    // --- USER ---
    USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found"),
    USER_INACTIVE("USER_INACTIVE", HttpStatus.FORBIDDEN, "User account is inactive"),
    USER_USERNAME_ALREADY_EXISTS("USER_USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username already exists"),
    USER_EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email already exists"),
    USER_SELF_ACTION_NOT_ALLOWED("USER_SELF_ACTION_NOT_ALLOWED", HttpStatus.CONFLICT, "Cannot perform this action on yourself"),

    // --- SOCIAL (Follow, Block, Mute, Restrict) ---
    FOLLOW_SELF_NOT_ALLOWED("FOLLOW_SELF_NOT_ALLOWED", HttpStatus.CONFLICT, "Cannot follow yourself"),
    FOLLOW_ALREADY_EXISTS("FOLLOW_ALREADY_EXISTS", HttpStatus.CONFLICT, "Already following this user"),
    FOLLOW_NOT_FOUND("FOLLOW_NOT_FOUND", HttpStatus.NOT_FOUND, "Follow relationship not found"),
    FOLLOW_PRIVATE_ACCOUNT("FOLLOW_PRIVATE_ACCOUNT", HttpStatus.CONFLICT, "Target account is private"),
    BLOCK_SELF_NOT_ALLOWED("BLOCK_SELF_NOT_ALLOWED", HttpStatus.CONFLICT, "A user cannot block themself"),
    BLOCK_ALREADY_EXISTS("BLOCK_ALREADY_EXISTS", HttpStatus.CONFLICT, "Block relationship already exists"),
    BLOCK_NOT_FOUND("BLOCK_NOT_FOUND", HttpStatus.NOT_FOUND, "Block relationship not found"),
    MUTE_SELF_NOT_ALLOWED("MUTE_SELF_NOT_ALLOWED", HttpStatus.CONFLICT, "A user cannot mute themself"),
    MUTE_ALREADY_EXISTS("MUTE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mute relationship already exists"),
    MUTE_NOT_FOUND("MUTE_NOT_FOUND", HttpStatus.NOT_FOUND, "Mute relationship not found"),
    RESTRICT_SELF_NOT_ALLOWED("RESTRICT_SELF_NOT_ALLOWED", HttpStatus.CONFLICT, "A user cannot restrict themself"),
    RESTRICTION_ALREADY_EXISTS("RESTRICTION_ALREADY_EXISTS", HttpStatus.CONFLICT, "Restriction already exists"),
    RESTRICTION_NOT_FOUND("RESTRICTION_NOT_FOUND", HttpStatus.NOT_FOUND, "Restriction not found"),

    // --- POST ---
    POST_NOT_FOUND("POST_NOT_FOUND", HttpStatus.NOT_FOUND, "Post not found"),
    POST_ALREADY_DELETED("POST_ALREADY_DELETED", HttpStatus.CONFLICT, "Post has already been deleted"),
    POST_REPLY_NOT_ALLOWED("POST_REPLY_NOT_ALLOWED", HttpStatus.FORBIDDEN, "Reply is disabled for this post"),
    REPLY_NOT_ALLOWED("REPLY_NOT_ALLOWED", HttpStatus.FORBIDDEN, "Reply is disabled for this post"),
    POST_QUOTE_NOT_ALLOWED("POST_QUOTE_NOT_ALLOWED", HttpStatus.FORBIDDEN, "Quote is disabled for this post"),
    QUOTE_NOT_ALLOWED("QUOTE_NOT_ALLOWED", HttpStatus.FORBIDDEN, "Quote is disabled for this post"),
    POST_OWNER_REQUIRED("POST_OWNER_REQUIRED", HttpStatus.FORBIDDEN, "Only post owner can modify or delete this post"),
    POST_ACCESS_DENIED("POST_ACCESS_DENIED", HttpStatus.FORBIDDEN, "You do not have permission to access this post"),

    // --- POLL ---
    POLL_NOT_FOUND("POLL_NOT_FOUND", HttpStatus.NOT_FOUND, "Poll not found"),
    POLL_CLOSED("POLL_CLOSED", HttpStatus.BAD_REQUEST, "This poll is closed"),
    POLL_ALREADY_VOTED("POLL_ALREADY_VOTED", HttpStatus.CONFLICT, "You have already voted in this poll"),
    POLL_NOT_MULTIPLE_CHOICE("POLL_NOT_MULTIPLE_CHOICE", HttpStatus.BAD_REQUEST, "This poll only allows voting for one option"),
    POLL_OPTION_NOT_FOUND("POLL_OPTION_NOT_FOUND", HttpStatus.NOT_FOUND, "Poll option not found or does not belong to poll"),

    // --- COMMUNITY ---
    COMMUNITY_NOT_FOUND("COMMUNITY_NOT_FOUND", HttpStatus.NOT_FOUND, "Community not found"),
    COMMUNITY_SLUG_EXISTS("COMMUNITY_SLUG_EXISTS", HttpStatus.CONFLICT, "Community slug already exists"),
    COMMUNITY_MEMBERSHIP_REQUIRED("COMMUNITY_MEMBERSHIP_REQUIRED", HttpStatus.FORBIDDEN, "Community membership required"),
    COMMUNITY_MEMBER_NOT_FOUND("COMMUNITY_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "Community member not found"),
    COMMUNITY_ALREADY_JOINED("COMMUNITY_ALREADY_JOINED", HttpStatus.CONFLICT, "Already a member of this community"),
    COMMUNITY_ACCESS_DENIED("COMMUNITY_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Community access denied"),
    COMMUNITY_ADMIN_REQUIRED("COMMUNITY_ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "Admin or owner privileges required"),
    COMMUNITY_OWNER_REQUIRED("COMMUNITY_OWNER_REQUIRED", HttpStatus.FORBIDDEN, "Only community owner can perform this action"),
    OWNER_CANNOT_LEAVE("OWNER_CANNOT_LEAVE", HttpStatus.CONFLICT, "Owner cannot leave community without transferring ownership"),
    COMMUNITY_OWNER_CANNOT_LEAVE("COMMUNITY_OWNER_CANNOT_LEAVE", HttpStatus.CONFLICT, "Owner cannot leave community without transferring ownership"),
    COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED("COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED", HttpStatus.FORBIDDEN, "You cannot remove this community member"),
    COMMUNITY_ROLE_CHANGE_NOT_ALLOWED("COMMUNITY_ROLE_CHANGE_NOT_ALLOWED", HttpStatus.FORBIDDEN, "You cannot change this member's role"),

    // --- CONVERSATION & MESSAGE ---
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Conversation not found"),
    CONVERSATION_MEMBERSHIP_REQUIRED("CONVERSATION_MEMBERSHIP_REQUIRED", HttpStatus.FORBIDDEN, "You are not a member of this conversation"),
    CONVERSATION_MEMBER_NOT_FOUND("CONVERSATION_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "Conversation member not found"),
    CONVERSATION_SELF_DIRECT_NOT_ALLOWED("CONVERSATION_SELF_DIRECT_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "Cannot create a direct conversation with yourself"),
    CONVERSATION_PARTICIPANTS_REQUIRED("CONVERSATION_PARTICIPANTS_REQUIRED", HttpStatus.BAD_REQUEST, "At least one participant required"),
    MESSAGE_NOT_FOUND("MESSAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "Message not found"),
    INVALID_REPLY_CONVERSATION("INVALID_REPLY_CONVERSATION", HttpStatus.BAD_REQUEST, "Target reply message belongs to a different conversation"),
    REPLY_MESSAGE_NOT_FOUND("REPLY_MESSAGE_NOT_FOUND", HttpStatus.BAD_REQUEST, "Target reply message not found"),
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "Member not found"),

    // --- MEDIA ---
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND", HttpStatus.NOT_FOUND, "Media not found"),
    MEDIA_FILE_REQUIRED("MEDIA_FILE_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY, "Media file is required"),
    MEDIA_TOO_LARGE("MEDIA_TOO_LARGE", HttpStatus.UNPROCESSABLE_ENTITY, "Media file exceeds the configured limit"),
    MEDIA_QUOTA_EXCEEDED("MEDIA_QUOTA_EXCEEDED", HttpStatus.CONFLICT, "User media quota exceeded"),
    MEDIA_ALREADY_ATTACHED("MEDIA_ALREADY_ATTACHED", HttpStatus.CONFLICT, "Attached media cannot be deleted here"),
    MEDIA_TYPE_UNSUPPORTED("MEDIA_TYPE_UNSUPPORTED", HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported media type"),
    MEDIA_STORAGE_UNAVAILABLE("MEDIA_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Media storage service is unavailable"),
    MEDIA_UPLOAD_FAILED("MEDIA_UPLOAD_FAILED", HttpStatus.SERVICE_UNAVAILABLE, "Unable to upload media to storage"),

    // --- TOPIC ---
    TOPIC_NOT_FOUND("TOPIC_NOT_FOUND", HttpStatus.NOT_FOUND, "Topic not found"),

    // --- REPORT ---
    REPORT_NOT_FOUND("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "Report not found"),
    REPORT_TARGET_REQUIRED("REPORT_TARGET_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY, "Exactly one of reportedUserId or reportedPostId is required"),
    REPORT_SELF_NOT_ALLOWED("REPORT_SELF_NOT_ALLOWED", HttpStatus.CONFLICT, "Cannot report yourself"),
    REPORT_ALREADY_IN_REVIEW("REPORT_ALREADY_IN_REVIEW", HttpStatus.CONFLICT, "Report is already being reviewed by another administrator"),
    REPORT_INVALID_TRANSITION("REPORT_INVALID_TRANSITION", HttpStatus.CONFLICT, "Invalid report state transition"),
    REPORT_VERSION_CONFLICT("REPORT_VERSION_CONFLICT", HttpStatus.CONFLICT, "Report was modified by another transaction"),
    REPORT_REVIEWER_REQUIRED("REPORT_REVIEWER_REQUIRED", HttpStatus.FORBIDDEN, "Only the assigned reviewer can decide this report"),

    // --- MODERATION ---
    MODERATION_ACTION_NOT_FOUND("MODERATION_ACTION_NOT_FOUND", HttpStatus.NOT_FOUND, "Moderation action not found"),
    POST_MODERATION_STATE_INVALID("POST_MODERATION_STATE_INVALID", HttpStatus.CONFLICT, "Post is not in a state that allows this moderation action"),
    POST_RESTORE_NOT_ALLOWED("POST_RESTORE_NOT_ALLOWED", HttpStatus.CONFLICT, "Cannot restore post that was author-deleted or not removed by moderation"),
    POST_REMOVAL_NOT_FOUND("POST_REMOVAL_NOT_FOUND", HttpStatus.NOT_FOUND, "Active post moderation removal record not found"),
    USER_SUSPENSION_ALREADY_ACTIVE("USER_SUSPENSION_ALREADY_ACTIVE", HttpStatus.CONFLICT, "User already has an active suspension"),
    USER_LAST_ADMIN_PROTECTED("USER_LAST_ADMIN_PROTECTED", HttpStatus.CONFLICT, "Cannot suspend or demote the last remaining system administrator"),
    COMMUNITY_OWNER_TRANSFER_INVALID("COMMUNITY_OWNER_TRANSFER_INVALID", HttpStatus.CONFLICT, "Invalid community owner transfer"),
    BROADCAST_INVALID_STATE("BROADCAST_INVALID_STATE", HttpStatus.CONFLICT, "System broadcast is not in a deliverable state"),

    // --- FEED ---
    FEED_NOT_FOUND("FEED_NOT_FOUND", HttpStatus.NOT_FOUND, "Feed not found"),
    FEED_ACCESS_DENIED("FEED_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied to private feed"),

    // --- NOTIFICATION ---
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Notification not found");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
