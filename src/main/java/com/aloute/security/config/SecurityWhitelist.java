package com.aloute.security.config;

/**
 * Whitelist bám các operation có security: [] trong skills/aloute-openapi.yaml.
 * Các pattern chỉ khai báo route public; endpoint chưa được module khác implement vẫn không tự sinh ra.
 */
public final class SecurityWhitelist {
    private SecurityWhitelist() {}

    public static final String[] PUBLIC_POST = {
            "/api/auth/register",
            "/api/auth/login"
    };

    public static final String[] PUBLIC_GET = {
            "/api/users/*",
            "/api/users/by-username/*",
            "/api/users/*/posts",
            "/api/users/*/followers",
            "/api/users/*/following",
            "/api/posts/*",
            "/api/posts/*/replies",
            "/api/media/file/**",
            "/api/topics",
            "/api/topics/*",
            "/api/topics/*/posts",
            "/api/polls/*",
            "/api/communities",
            "/api/communities/*",
            "/api/communities/*/posts",
            "/api/feed/explore"
    };

    public static final String[] PUBLIC_WEB = {
            "/",
            "/login",
            "/register",
            "/css/**",
            "/js/**",
            "/images/**",
            "/webjars/**",
            "/favicon.ico",
            "/error",
            "/khoi-test",
            "/khoi-test/**"
    };
}
