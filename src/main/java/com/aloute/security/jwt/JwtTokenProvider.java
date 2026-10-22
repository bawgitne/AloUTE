package com.aloute.security.jwt;

import com.aloute.security.principal.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties properties;

    @PostConstruct
    void validateConfiguration() {
        signingKey();
        properties.requireExpirationSeconds();
    }

    public String generate(UserPrincipal principal) {
        return generate(principal, properties.requireExpirationSeconds());
    }

    public String generate(UserPrincipal principal, long expirationSeconds) {
        Instant now = Instant.now();
        List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        Long authVersion = principal.getAuthVersion() != null ? principal.getAuthVersion() : 0L;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getId().toString())
                .claim("v", authVersion)
                .claim("roles", List.of(principal.getRole().name()))
                .claim("authorities", authorities.stream().filter(a -> !a.startsWith("ROLE_")).toList())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey()).compact();
    }

    public String generateTwoFactorTicket(Long userId, String type, long expirationSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("purpose", "2FA_CHALLENGE")
                .claim("type", type != null ? type : "TOTP")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseTwoFactorTicket(String ticket) {
        Claims claims = parseClaims(ticket);
        if (!"2FA_CHALLENGE".equals(claims.get("purpose"))) {
            throw new IllegalArgumentException("Invalid 2FA challenge ticket purpose");
        }
        return claims;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public long expirationSeconds() {
        return properties.requireExpirationSeconds();
    }

    private SecretKey signingKey() {
        byte[] decoded = Decoders.BASE64.decode(properties.requireBase64Secret());
        if (decoded.length < 32) throw new IllegalStateException("ALOUTE_JWT_SECRET must decode to at least 32 bytes");
        return Keys.hmacShaKeyFor(decoded);
    }
}
