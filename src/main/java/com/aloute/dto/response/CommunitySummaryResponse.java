package com.aloute.dto.response;

/**
 * Minimal Community identity attached to PostResponse for Facebook-style
 * "community above author" post headers. Only ever attached when the post
 * itself is already visible to the viewer, so it never leaks hidden metadata.
 */
public record CommunitySummaryResponse(Long id, String name, String slug, String avatarUrl) {}
