package com.aloute.feature.auth.service;

import com.aloute.common.exception.ApiException;
import com.aloute.feature.auth.dto.request.LoginRequest;
import com.aloute.feature.auth.dto.request.RegisterRequest;
import com.aloute.feature.user.dto.response.UserResponse;
import com.aloute.feature.user.entity.Profile;
import com.aloute.feature.user.entity.User;
import com.aloute.feature.user.mapper.UserMapper;
import com.aloute.feature.user.repository.ProfileRepository;
import com.aloute.feature.user.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_USERNAME_ALREADY_EXISTS",
                    "Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_EMAIL_ALREADY_EXISTS",
                    "Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setVerified(false);
        user.setPrivateAccount(false);
        user.setActive(true);
        userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        // OpenAPI RegisterRequest has no displayName while ProfileResponse requires it.
        // Username is therefore used as the deterministic initial display value.
        profile.setDisplayName(username);
        profileRepository.save(profile);

        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim(), request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.UNAUTHORIZED,
                            "AUTH_INVALID_CREDENTIALS",
                            "Invalid email or password"));
            return new AuthenticatedUser(principal, userMapper.toUserResponse(user));
        } catch (AuthenticationException ex) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_CREDENTIALS",
                    "Invalid email or password");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        return userMapper.toUserResponse(user);
    }
}
