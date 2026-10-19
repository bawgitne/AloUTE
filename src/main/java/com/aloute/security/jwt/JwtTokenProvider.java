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
        Instant now=Instant.now(); long expiration=properties.requireExpirationSeconds();
        List<String> authorities=principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return Jwts.builder().subject(principal.getId().toString())
                .claim("roles", List.of(principal.getRole().name()))
                .claim("authorities", authorities.stream().filter(a -> !a.startsWith("ROLE_")).toList())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expiration)))
                .signWith(signingKey()).compact();
    }
    public Long parseUserId(String token) {
        Claims c=Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(c.getSubject());
    }
    public long expirationSeconds() { return properties.requireExpirationSeconds(); }
    private SecretKey signingKey() {
        byte[] decoded=Decoders.BASE64.decode(properties.requireBase64Secret());
        if (decoded.length<32) throw new IllegalStateException("ALOUTE_JWT_SECRET must decode to at least 32 bytes");
        return Keys.hmacShaKeyFor(decoded);
    }
}
