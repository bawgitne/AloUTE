package com.aloute.security.jwt;

import com.aloute.entity.RevokedToken;
import com.aloute.entity.User;
import com.aloute.repository.RevokedTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

@Service
@Slf4j
public class JwtSessionService {
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    @Autowired
    public JwtSessionService(JwtTokenProvider tokenProvider,
                             UserRepository userRepository,
                             RevokedTokenRepository revokedTokenRepository) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.revokedTokenRepository = Objects.requireNonNull(revokedTokenRepository, "revokedTokenRepository must not be null");
    }

    public static String computeFingerprint(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be blank");
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot < 0 || lastDot == token.length() - 1) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        String signaturePart = token.substring(lastDot + 1);
        byte[] signatureBytes = io.jsonwebtoken.io.Decoders.BASE64URL.decode(signaturePart);
        if (signatureBytes == null || signatureBytes.length == 0) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signatureBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Centralized canonical JWT authentication and session invalidation verification.
     * Runs in its own read-only REQUIRES_NEW transaction to bypass any stale L1 persistence context,
     * loading current typed User state directly from the database.
     * Verifies signature, expiry, subject, user active state, signed account session version,
     * and durable token revocation blacklist.
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

        String tokenHash = computeFingerprint(token);
        if (revokedTokenRepository.existsByTokenHash(tokenHash)) {
            throw new BadCredentialsException("Token has been revoked");
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

    /**
     * Durably revokes the specific token by storing its SHA-256 fingerprint and expiration time.
     */
    @Transactional
    public void revokeToken(String token, Long userId) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Token is required for revocation");
        }
        Claims claims;
        try {
            claims = tokenProvider.parseClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        Long tokenUserId = null;
        try {
            if (claims.getSubject() != null) {
                tokenUserId = Long.valueOf(claims.getSubject());
            }
        } catch (NumberFormatException ignored) {}

        if (userId != null && tokenUserId != null && !userId.equals(tokenUserId)) {
            throw new BadCredentialsException("Token does not belong to user");
        }

        Long finalUserId = userId != null ? userId : tokenUserId;

        // Consistent database-backed lock order: acquire row-level lock on User row FIRST
        // to serialize concurrent revocation requests across transactions/threads/replicas through surrounding commit
        if (finalUserId != null) {
            userRepository.findByIdForUpdate(finalUserId);
        }

        String tokenHash = computeFingerprint(token);
        if (!revokedTokenRepository.existsByTokenHash(tokenHash)) {
            Instant expiresAt = claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : Instant.now().plusSeconds(tokenProvider.expirationSeconds());
            RevokedToken revokedToken = new RevokedToken(tokenHash, finalUserId, expiresAt);
            revokedTokenRepository.saveAndFlush(revokedToken);
        }
    }
}
