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

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RegistrationOtpService registrationOtpService;

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
    public AuthenticatedUser login(LoginRequest request) {
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

            return new AuthenticatedUser(principal, toResponse(user));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid email or password");
        }
    }

    @Override @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return toResponse(requireUser(userId));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isVerified(),
                user.isPrivateAccount(), user.isActive());
    }
}
