package com.aloute.security.jwt;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtProperties {
    private static final String SECRET="ALOUTE_JWT_SECRET";
    private static final String EXPIRATION="ALOUTE_JWT_EXPIRATION_SECONDS";
    private final Environment environment;
    public JwtProperties(Environment environment) { this.environment=environment; }
    public String requireBase64Secret() {
        String v=environment.getProperty(SECRET); if (v==null||v.isBlank()) throw new IllegalStateException("Missing required configuration: "+SECRET); return v.trim();
    }
    public long requireExpirationSeconds() {
        String raw=environment.getProperty(EXPIRATION); if (raw==null||raw.isBlank()) throw new IllegalStateException("Missing required configuration: "+EXPIRATION);
        try { long v=Long.parseLong(raw.trim()); if (v<=0) throw new IllegalStateException(EXPIRATION+" must be greater than 0"); return v; }
        catch (NumberFormatException ex) { throw new IllegalStateException(EXPIRATION+" must be a positive integer", ex); }
    }
}
