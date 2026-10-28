package com.aloute.security.config;

/**
 * Whitelist bám các operation có security: [] trong skills/aloute-openapi.yaml
 * và các route Web Thymeleaf public.
 */
public final class SecurityWhitelist {
    private SecurityWhitelist() {}

    public static final String[] PUBLIC_POST = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/2fa/verify-login",
            "/api/auth/2fa/send-email-fallback"
    };

    public static final String[] PUBLIC_RECOVERY_POST = {
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify-registration-otp",
            "/api/auth/resend-registration-otp"
    };

    public static final String[] PUBLIC_GET = {
            "/api/auth/me",
            "/api/users/*",
            "/api/users/by-username/*",
            "/api/users/*/posts",
            "/api/users/*/followers",
            "/api/users/*/following",
            "/api/posts",
            "/api/posts/*",
            "/api/posts/*/replies",
            "/api/posts/*/quotes",
            "/api/media/file/**",
            "/api/topics",
            "/api/topics/*",
            "/api/topics/*/posts",
            "/api/polls/*",
            "/api/communities",
            "/api/communities/*",
            "/api/communities/*/members",
            "/api/communities/*/posts",
            "/api/feed",
            "/api/feed/**",
            "/api/feeds",
            "/api/feeds/**"
    };

    public static final String[] PUBLIC_WEB = {
            "/",
            "/feed",
            "/login",
            "/register",
            "/verify-otp",
            "/forgot-password",
            "/reset-password",
            "/explore",
            "/post/**",
            "/posts/**",
            "/p/**",
            "/profile",
            "/profile/**",
            "/@**",
            "/u/**",
            "/user/**",
            "/users/**",
            "/communities",
            "/communities/**",
            "/c/**",
            "/t/**",
            "/topic/**",
            "/topics/**",
            "/saved",
            "/saved/**",
            "/bookmarks",
            "/bookmarks/**",
            "/settings",
            "/settings/**",
            "/friends",
            "/friends/**",
            "/messages",
            "/messages/**",

            "/notifications",
            "/notifications/**",
            "/search",
            "/search/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/webjars/**",
            "/favicon.ico",
            "/*.png",
            "/*.svg",
            "/*.ico",
            "/error",
            "/ws",
            "/ws/**"
    };
}
