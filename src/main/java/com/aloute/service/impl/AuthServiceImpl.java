package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.response.UserResponse;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.AuthService;
import com.aloute.service.RegistrationOtpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.aloute.config.StompAuthChannelInterceptor;
import com.aloute.security.jwt.JwtSessionService;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RegistrationOtpService registrationOtpService;
    private final JwtSessionService jwtSessionService;

    @Autowired(required = false)
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_USERNAME_ALREADY_EXISTS", "Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EMAIL_ALREADY_EXISTS", "Email already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setVerified(false);
        user.setVerificationRequired(true);
        user.setPrivateAccount(false);
        user.setActive(true);
        user.setRole(Role.USER);
        userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(username);
        profileRepository.save(profile);

        registrationOtpService.initiateRegistrationOtp(user);

        return toResponse(user);
    }

    @Override @Transactional(readOnly = true)
    public LoginOutcome login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                            "AUTH_INVALID_CREDENTIALS", "Invalid email or password"));

            if (user.isVerificationRequired() && !user.isVerified()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_NOT_VERIFIED",
                        "Tài khoản chưa được kích hoạt. Vui lòng xác thực email.");
            }

            if (!user.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DEACTIVATED",
                        "Tài khoản của bạn đang bị vô hiệu hóa. Vui lòng liên hệ ban quản trị để kích hoạt lại.");
            }

            if (user.isTwoFactorEnabled()) {
                String masked = maskEmail(user.getEmail());
                String type = user.getTwoFactorType() != null ? user.getTwoFactorType() : "TOTP";
                return LoginOutcome.twoFactorRequired(user.getId(), type, masked);
            }

            return LoginOutcome.success(principal, toResponse(user));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid email or password");
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Override @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return toResponse(requireUser(userId));
    }

    @Override
    @Transactional
    public void logout(String token, Long userId) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "Authentication required");
        }

        // Validate token before durable revocation
        jwtSessionService.authenticate(token);

        // Durably persist revocation fingerprint
        jwtSessionService.revokeToken(token, userId);

        // Close STOMP WebSocket session bound to this exact token after transaction commit
        if (stompAuthChannelInterceptor != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        stompAuthChannelInterceptor.closeSessionsForToken(token);
                    }
                });
            } else {
                stompAuthChannelInterceptor.closeSessionsForToken(token);
            }
        }
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isVerified(),
                user.isPrivateAccount(), user.isActive(), user.isTwoFactorEnabled(), user.getRole());
    }
}
