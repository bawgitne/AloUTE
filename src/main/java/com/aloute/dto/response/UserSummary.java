package com.aloute.dto.response;

public record UserSummary(Long id, String username, String displayName,
                          String avatarUrl, boolean isVerified) {}
