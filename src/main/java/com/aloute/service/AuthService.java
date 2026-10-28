package com.aloute.service;

import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.principal.UserPrincipal;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    LoginOutcome login(LoginRequest request);
    UserResponse getCurrentUser(Long userId);
    void logout(String token, Long userId);

    record AuthenticatedUser(UserPrincipal principal, UserResponse user) {}

    record LoginOutcome(
            boolean twoFactorRequired,
            UserPrincipal principal,
            UserResponse user,
            Long userId,
            String twoFactorType,
            String maskedEmail,
            long authVersion
    ) {
        public static LoginOutcome success(UserPrincipal principal, UserResponse user) {
            return new LoginOutcome(false, principal, user, user.id(), null, null, 0L);
        }

        public static LoginOutcome twoFactorRequired(Long userId, String twoFactorType, String maskedEmail,
                                                     long authVersion) {
            return new LoginOutcome(true, null, null, userId, twoFactorType, maskedEmail, authVersion);
        }
    }
}
