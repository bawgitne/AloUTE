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

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RegistrationOtpService registrationOtpService;
    private final JwtSessionService jwtSessionService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /** BL-03: consecutive failed logins tolerated before the account locks. */
    @org.springframework.beans.factory.annotation.Value("${aloute.auth.login-max-failed-attempts:5}")
    private int loginMaxFailedAttempts;

    /** BL-03: how long a locked account refuses login attempts. */
    @org.springframework.beans.factory.annotation.Value("${aloute.auth.login-lockout-minutes:15}")
    private int loginLockoutMinutes;

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

    @Override
    public LoginOutcome login(LoginRequest request) {
        String email = request.email().trim();

        // BL-03: per-account brute-force lockout, evaluated before credential
        // verification. The response is uniform and carries no account detail.
        // Writes go through TransactionTemplate: this method is intentionally not
        // @Transactional, so failure-path updates survive the ApiException that
        // follows instead of being rolled back with it.
        User lookup = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (lookup != null && lookup.getLockedUntil() != null && lookup.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_TOO_MANY_ATTEMPTS",
                    "Too many failed login attempts. Please try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
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

            // Success clears prior failure state; the conditional update touches
            // the row only when the user actually carries failure accounting.
            if (lookup != null && (lookup.getFailedLoginAttempts() > 0 || lookup.getLockedUntil() != null)) {
                transactionTemplate.executeWithoutResult(status ->
                        userRepository.resetLoginFailureState(user.getId()));
            }

            if (user.isTwoFactorEnabled()) {
                String masked = maskEmail(user.getEmail());
                String type = user.getTwoFactorType() != null ? user.getTwoFactorType() : "TOTP";
                return LoginOutcome.twoFactorRequired(user.getId(), type, masked, user.getAuthVersion() != null ? user.getAuthVersion() : 0L);
            }

            return LoginOutcome.success(principal, toResponse(user));
        } catch (AuthenticationException ex) {
            Instant lockUntil = Instant.now().plus(Duration.ofMinutes(loginLockoutMinutes));
            transactionTemplate.executeWithoutResult(status ->
                    userRepository.registerFailedLogin(email, loginMaxFailedAttempts, lockUntil));
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
