package com.aloute.dto.response;

import com.aloute.security.authorization.Role;

public record UserResponse(Long id, String username, String email,
                           boolean isVerified, boolean isPrivate, boolean isActive,
                           boolean isTwoFactorEnabled, Role role) {
    public UserResponse(Long id, String username, String email,
                        boolean isVerified, boolean isPrivate, boolean isActive) {
        this(id, username, email, isVerified, isPrivate, isActive, false, Role.USER);
    }
    public UserResponse(Long id, String username, String email,
                        boolean isVerified, boolean isPrivate, boolean isActive,
                        boolean isTwoFactorEnabled) {
        this(id, username, email, isVerified, isPrivate, isActive, isTwoFactorEnabled, Role.USER);
    }
}
