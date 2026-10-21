package com.aloute.dto.response;

public record UserResponse(Long id, String username, String email,
                           boolean isVerified, boolean isPrivate, boolean isActive,
                           boolean isTwoFactorEnabled) {
    public UserResponse(Long id, String username, String email,
                        boolean isVerified, boolean isPrivate, boolean isActive) {
        this(id, username, email, isVerified, isPrivate, isActive, false);
    }
}
