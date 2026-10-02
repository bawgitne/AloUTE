package com.aloute.security.jwt;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtProperties {

    private static final String SECRET = "ALOUTE_JWT_SECRET";
    private static final String EXPIRATION_SECONDS = "ALOUTE_JWT_EXPIRATION_SECONDS";

    private final Environment environment;

    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    public String requireBase64Secret() {
        String value = environment.getProperty(SECRET);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + SECRET);
        }
        return value.trim();
    }

    public long requireExpirationSeconds() {
        String raw = environment.getProperty(EXPIRATION_SECONDS);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + EXPIRATION_SECONDS);
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new IllegalStateException(EXPIRATION_SECONDS + " must be greater than 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(EXPIRATION_SECONDS + " must be a positive integer", ex);
        }
    }
}
