package com.aloute.dto.response;

public record UserResponse(Long id, String username, String email,
                           boolean isVerified, boolean isPrivate, boolean isActive) {}
