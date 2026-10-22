package com.aloute.security.jwt;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtSessionService {
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    /**
     * Centralized canonical JWT authentication and session invalidation verification.
     * Runs in its own read-only REQUIRES_NEW transaction to bypass any stale L1 persistence context,
     * loading current typed User state directly from the database.
     * Verifies signature, expiry, subject, user active state, and signed account session version.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public UserPrincipal authenticate(String token) throws AuthenticationException {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Token is required");
        }

        Claims claims;
        try {
            claims = tokenProvider.parseClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        Object purposeClaim = claims.get("purpose");
        if (purposeClaim != null) {
            throw new BadCredentialsException("Invalid token purpose");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Invalid token subject");
        }

        Long userId;
        try {
            userId = Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid token user identifier");
        }

        User user;
        try {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new BadCredentialsException("User not found or revoked"));
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Database lookup failed during authentication: {}", ex.getMessage());
            throw new BadCredentialsException("Authentication failed due to database error");
        }

        if (!user.isActive()) {
            throw new DisabledException("User account is inactive");
        }

        Long userAuthVersion = user.getAuthVersion() != null ? user.getAuthVersion() : 0L;

        Object versionClaim = claims.get("v");
        Long tokenVersion;

        if (versionClaim == null) {
            // Missing-version legacy tokens treated as 0 ONLY while account version is 0
            if (userAuthVersion > 0L) {
                throw new BadCredentialsException("Legacy token rejected for updated session version");
            }
            tokenVersion = 0L;
        } else if (versionClaim instanceof Number numberClaim) {
            long longVal = numberClaim.longValue();
            if (numberClaim.doubleValue() != (double) longVal) {
                throw new BadCredentialsException("Malformed fractional token version");
            }
            if (longVal < 0) {
                throw new BadCredentialsException("Malformed negative token version");
            }
            tokenVersion = longVal;
        } else if (versionClaim instanceof String stringClaim) {
            if (!stringClaim.matches("^\\d+$")) {
                throw new BadCredentialsException("Malformed non-integer token version string");
            }
            try {
                tokenVersion = Long.parseLong(stringClaim);
            } catch (NumberFormatException ex) {
                throw new BadCredentialsException("Malformed token version number format");
            }
        } else {
            throw new BadCredentialsException("Malformed token version claim type");
        }

        if (!tokenVersion.equals(userAuthVersion)) {
            throw new BadCredentialsException("Session revoked or expired due to password reset");
        }

        user.setAuthVersion(userAuthVersion);
        return UserPrincipal.from(user);
    }
}
