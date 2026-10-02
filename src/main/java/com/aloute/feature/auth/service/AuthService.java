package com.aloute.feature.auth.service;

import com.aloute.feature.auth.dto.request.LoginRequest;
import com.aloute.feature.auth.dto.request.RegisterRequest;
import com.aloute.feature.user.dto.response.UserResponse;
import com.aloute.security.principal.UserPrincipal;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthenticatedUser login(LoginRequest request);
    UserResponse getCurrentUser(Long userId);

    record AuthenticatedUser(UserPrincipal principal, UserResponse user) {
    }
}
