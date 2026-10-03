package com.aloute.service;

import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.principal.UserPrincipal;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthenticatedUser login(LoginRequest request);
    UserResponse getCurrentUser(Long userId);

    record AuthenticatedUser(UserPrincipal principal, UserResponse user) {}
}
