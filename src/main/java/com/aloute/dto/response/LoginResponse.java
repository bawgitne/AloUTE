package com.aloute.dto.response;

public record LoginResponse(
        Long id,
        String username,
        String email,
        boolean isVerified,
        boolean isPrivate,
        boolean isActive,
        boolean isTwoFactorEnabled,
        UserResponse user,
        String token,
        boolean twoFactorRequired,
        String twoFactorTicket,
        String twoFactorType,
        String maskedEmail
) {
    public static LoginResponse success(UserResponse user, String token) {
        return new LoginResponse(
                user.id(), user.username(), user.email(), user.isVerified(), user.isPrivate(), user.isActive(), user.isTwoFactorEnabled(),
                user, token, false, null, null, null
        );
    }

    public static LoginResponse twoFactorRequired(String twoFactorTicket, String twoFactorType, String maskedEmail) {
        return new LoginResponse(
                null, null, null, false, false, false, true,
                null, null, true, twoFactorTicket, twoFactorType, maskedEmail
        );
    }
}
