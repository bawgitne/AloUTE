package com.aloute.security.jwt;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtSessionServiceTest {

    private static final String BASE64_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final long EXPIRATION_SECONDS = 3600;

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider tokenProvider;
    private JwtSessionService sessionService;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("ALOUTE_JWT_SECRET", BASE64_SECRET)
                .withProperty("ALOUTE_JWT_EXPIRATION_SECONDS", String.valueOf(EXPIRATION_SECONDS));
        JwtProperties properties = new JwtProperties(env);
        tokenProvider = new JwtTokenProvider(properties);
        sessionService = new JwtSessionService(tokenProvider, userRepository);
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));
    }

    private User createTestUser(Long id, Long authVersion, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setPasswordHash("hashed_pw");
        user.setAuthVersion(authVersion);
        user.setActive(active);
        return user;
    }

    private String buildToken(Long userId, Object versionClaim, Instant issuedAt, Instant expiresAt) {
        return buildTokenWithPurpose(userId, versionClaim, null, issuedAt, expiresAt);
    }

    private String buildTokenWithPurpose(Long userId, Object versionClaim, String purpose, Instant issuedAt, Instant expiresAt) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey);
        if (versionClaim != null) {
            builder.claim("v", versionClaim);
        }
        if (purpose != null) {
            builder.claim("purpose", purpose);
        }
        return builder.compact();
    }

    @Test
    void authenticate_validTokenMatchingAuthVersion_returnsUserPrincipal() {
        User user = createTestUser(100L, 0L, true);
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        UserPrincipal principal = UserPrincipal.from(user);
        String token = tokenProvider.generate(principal);

        UserPrincipal authenticated = sessionService.authenticate(token);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isEqualTo(100L);
        assertThat(authenticated.getAuthVersion()).isEqualTo(0L);
    }

    @Test
    void authenticate_tokenVersionMismatchAfterReset_throwsBadCredentials() {
        User user = createTestUser(100L, 1L, true); // account version incremented to 1
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        // Token minted earlier with v = 0
        String oldToken = buildToken(100L, 0L, Instant.now(), Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> sessionService.authenticate(oldToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Session revoked or expired due to password reset");
    }

    @Test
    void authenticate_tokenWithNewVersion_succeeds() {
        User user = createTestUser(100L, 1L, true);
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        UserPrincipal principal = UserPrincipal.from(user);
        String newToken = tokenProvider.generate(principal);

        UserPrincipal authenticated = sessionService.authenticate(newToken);
        assertThat(authenticated.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void authenticate_legacyNullVersionToken_allowedOnlyWhenUserAuthVersionIsZero() {
        User userV0 = createTestUser(200L, 0L, true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(userV0));

        String legacyToken = buildToken(200L, null, Instant.now(), Instant.now().plusSeconds(3600));
        UserPrincipal principal = sessionService.authenticate(legacyToken);
        assertThat(principal.getId()).isEqualTo(200L);

        // But when account version is incremented, legacy token without 'v' is rejected
        User userV1 = createTestUser(200L, 1L, true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(userV1));
        assertThatThrownBy(() -> sessionService.authenticate(legacyToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Legacy token rejected");
    }

    @Test
    void authenticate_malformedVersionClaims_rejected() {
        User user = createTestUser(300L, 0L, true);
        when(userRepository.findById(300L)).thenReturn(Optional.of(user));

        // Negative version
        String negToken = buildToken(300L, -1L, Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(negToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("negative");

        // Fractional version
        String fracToken = buildToken(300L, 1.5, Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(fracToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("fractional");

        // Non-digit string
        String stringToken = buildToken(300L, "invalid_epoch", Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(stringToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Malformed");
    }

    @Test
    void authenticate_twoFactorChallengeTicket_rejectedEvenIfAuthVersionIsZero() {
        String challengeTicket = tokenProvider.generateTwoFactorTicket(100L, "TOTP", 300);

        assertThatThrownBy(() -> sessionService.authenticate(challengeTicket))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid token purpose");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_purposeTokenWithExplicitAuthVersion_rejectedIndependentlyOfVersionFallback() {
        String token = buildTokenWithPurpose(100L, 5L, "2FA_CHALLENGE", Instant.now(), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> sessionService.authenticate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid token purpose");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_unknownPurposeToken_rejected() {
        String token = buildTokenWithPurpose(100L, 0L, "UNKNOWN_PURPOSE", Instant.now(), Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> sessionService.authenticate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid token purpose");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_inactiveUser_throwsDisabledException() {
        User user = createTestUser(400L, 0L, false);
        when(userRepository.findById(400L)).thenReturn(Optional.of(user));

        String token = buildToken(400L, 0L, Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(token))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void authenticate_userNotFound_throwsBadCredentials() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        String token = buildToken(999L, 0L, Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("User not found or revoked");
    }

    @Test
    void authenticate_databaseError_failsClosedWithBadCredentials() {
        when(userRepository.findById(500L)).thenThrow(new org.springframework.dao.QueryTimeoutException("DB timeout"));

        String token = buildToken(500L, 0L, Instant.now(), Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> sessionService.authenticate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Authentication failed due to database error");
    }
}
