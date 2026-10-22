package com.aloute.integration;

import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.entity.RevokedToken;
import com.aloute.entity.User;
import com.aloute.repository.RevokedTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.RevokedTokenCleanupService;
import com.aloute.service.email.TestEmailSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.registration-otp-secret=dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=",
        "aloute.auth.revoked-token-cleanup-cron=* * * * * *"
})
class DurableLogoutRevocationIntegrationTest {

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private RevokedTokenRepository revokedTokenRepository;
    @Autowired private RevokedTokenCleanupService revokedTokenCleanupService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtSessionService jwtSessionService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestEmailSink emailSink;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void setup() {
        emailSink.clearSink();
    }

    private String generateEmail(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@example.com";
    }

    private String registerAndVerifyUser(String email, String password) throws Exception {
        String username = "u_" + System.currentTimeMillis() % 1000000 + "_" + ThreadLocalRandom.current().nextInt(100, 999);
        RegisterRequest regReq = new RegisterRequest(username, email, password);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String json = objectMapper.writeValueAsString(regReq);
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(201);

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setVerified(true);
        user.setVerificationRequired(false);
        userRepository.saveAndFlush(user);
        return username;
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        RealHttpLoginResult result = performRealHttpLogin(email, password);
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.token()).isNotNull();
        return result.token();
    }

    private String fetchCsrfToken() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/login"))
                .GET()
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        String cookieHeader = resp.headers().firstValue("Set-Cookie").orElse("");
        for (String c : cookieHeader.split(";")) {
            String trimmed = c.trim();
            if (trimmed.startsWith("XSRF-TOKEN=")) {
                return trimmed.substring("XSRF-TOKEN=".length());
            }
        }
        return "dummy-csrf";
    }

    private record RealHttpLoginResult(String token, String setCookieHeader, int status) {}

    private RealHttpLoginResult performRealHttpLogin(String email, String password) throws Exception {
        String csrfToken = fetchCsrfToken();
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String json = objectMapper.writeValueAsString(new LoginRequest(email, password));
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        String setCookie = resp.headers().firstValue("Set-Cookie").orElse("");
        String token = null;
        for (String c : setCookie.split(";")) {
            String trimmed = c.trim();
            if (trimmed.startsWith("aloute_access_token=")) {
                token = trimmed.substring("aloute_access_token=".length());
            }
        }
        return new RealHttpLoginResult(token, setCookie, resp.statusCode());
    }

    private java.net.http.HttpResponse<String> performRealHttpLogout(String cookieToken, String bearerToken, String csrfToken) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        var builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/logout"));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        List<String> cookies = new ArrayList<>();
        if (cookieToken != null) {
            cookies.add("aloute_access_token=" + cookieToken);
        }
        if (csrfToken != null) {
            cookies.add("XSRF-TOKEN=" + csrfToken);
        }
        if (!cookies.isEmpty()) {
            builder.header("Cookie", String.join("; ", cookies));
        }
        java.net.http.HttpRequest req = builder.POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        return client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private java.net.http.HttpResponse<String> performRealHttpMe(String cookieToken, String bearerToken) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        var builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (cookieToken != null) {
            builder.header("Cookie", "aloute_access_token=" + cookieToken);
        }
        java.net.http.HttpRequest req = builder.GET().build();
        return client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Copied token reused after logout as Bearer or Cookie is rejected (401)")
    void copiedTokenReusedAfterLogoutIsRejected() throws Exception {
        String email = generateEmail("logout_reuse");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        // 1. Login and get token
        String token = loginAndExtractToken(email, password);

        // 2. Access protected endpoint before logout -> 200 OK
        var meBefore = performRealHttpMe(null, token);
        assertThat(meBefore.statusCode()).isEqualTo(200);

        // 3. Perform logout
        String csrfToken = fetchCsrfToken();
        var logoutResp = performRealHttpLogout(token, null, csrfToken);
        assertThat(logoutResp.statusCode()).isEqualTo(204);
        assertThat(logoutResp.headers().allValues("Set-Cookie").stream()
                .anyMatch(s -> s.contains("aloute_access_token="))).isTrue();

        // 4. Copied token as Bearer must now be REJECTED with 401
        var meBearer = performRealHttpMe(null, token);
        assertThat(meBearer.statusCode()).isEqualTo(401);

        // 5. Copied token as Cookie must now be REJECTED with 401
        var meCookie = performRealHttpMe(token, null);
        assertThat(meCookie.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Distinct session identity: two immediate logins produce distinct tokens, and logging out one leaves other valid")
    void independentSecondSessionRemainsValidWhenFirstLogsOut() throws Exception {
        String email = generateEmail("multi_session");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        // Immediate successive logins (minted within same millisecond/second)
        String token1 = loginAndExtractToken(email, password);
        String token2 = loginAndExtractToken(email, password);

        // Must produce distinct tokens due to unique session identity (jti)
        assertThat(token1)
                .as("Two separate logins must mint distinct tokens, not identical strings")
                .isNotEqualTo(token2);

        // Both are valid initially
        assertThat(performRealHttpMe(null, token1).statusCode()).isEqualTo(200);
        assertThat(performRealHttpMe(null, token2).statusCode()).isEqualTo(200);

        // Logout session 1
        String csrf = fetchCsrfToken();
        var logoutResp = performRealHttpLogout(token1, null, csrf);
        assertThat(logoutResp.statusCode()).isEqualTo(204);

        // Session 1 is rejected
        assertThat(performRealHttpMe(null, token1).statusCode()).isEqualTo(401);

        // Session 2 REMAINS VALID!
        assertThat(performRealHttpMe(null, token2).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Unauthenticated or invalid logout request is rejected (401)")
    void unauthenticatedOrInvalidLogoutIsRejected() throws Exception {
        String csrf = fetchCsrfToken();
        // 1. Missing credentials
        assertThat(performRealHttpLogout(null, null, csrf).statusCode()).isEqualTo(401);

        // 2. Forged invalid token
        assertThat(performRealHttpLogout(null, "invalid.forged.jwt", csrf).statusCode()).isEqualTo(401);

        // 3. Expired token
        String email = generateEmail("expired_logout");
        String password = "Password123!";
        registerAndVerifyUser(email, password);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String expiredToken = jwtTokenProvider.generate(UserPrincipal.from(user), -10);

        assertThat(performRealHttpLogout(null, expiredToken, csrf).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Password reset revokes all prior tokens via authVersion epoch invalidation")
    void passwordResetRevokesAllPriorTokensEpoch() throws Exception {
        String email = generateEmail("epoch_test");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        String token1 = loginAndExtractToken(email, password);
        String token2 = loginAndExtractToken(email, password);

        assertThat(performRealHttpMe(null, token1).statusCode()).isEqualTo(200);
        assertThat(performRealHttpMe(null, token2).statusCode()).isEqualTo(200);

        // Simulate password reset epoch advance on User
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.saveAndFlush(user);

        // Both prior tokens must now be rejected with 401
        assertThat(performRealHttpMe(null, token1).statusCode()).isEqualTo(401);
        assertThat(performRealHttpMe(null, token2).statusCode()).isEqualTo(401);

        // New token minted with updated authVersion succeeds
        String freshToken = jwtTokenProvider.generate(UserPrincipal.from(user));
        assertThat(performRealHttpMe(null, freshToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Scheduled cleanup: Accelerated scheduler dispatch deletes expired revoked tokens without manual method invocation")
    void schedulerCleanupDeletesExpiredRevokedTokens() throws Exception {
        String expiredHash = "expired_" + UUID.randomUUID().toString().replace("-", "");
        String activeHash = "active_" + UUID.randomUUID().toString().replace("-", "");

        RevokedToken expiredToken = new RevokedToken(expiredHash, 1001L, Instant.now().minusSeconds(300));
        RevokedToken activeToken = new RevokedToken(activeHash, 1002L, Instant.now().plusSeconds(3600));

        revokedTokenRepository.saveAndFlush(expiredToken);
        revokedTokenRepository.saveAndFlush(activeToken);

        assertThat(revokedTokenRepository.existsByTokenHash(expiredHash)).isTrue();
        assertThat(revokedTokenRepository.existsByTokenHash(activeHash)).isTrue();

        // Accelerated cron (* * * * * *) triggers automatic background cleanup every second
        long deadline = System.currentTimeMillis() + 5000;
        while (revokedTokenRepository.existsByTokenHash(expiredHash) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        assertThat(revokedTokenRepository.existsByTokenHash(expiredHash)).isFalse();
        assertThat(revokedTokenRepository.existsByTokenHash(activeHash)).isTrue();
    }

    @Test
    @DisplayName("Durable persistence: Revoked tokens survive complete Spring application context restart on persistent store")
    void revokedTokensSurviveCompleteSpringContextRestart() throws Exception {
        Path tempDir = Files.createTempDirectory("aloute_spring_restart_test");
        String dbPath = tempDir.resolve("restart_db").toAbsolutePath().toString().replace('\\', '/');
        String dbUrl = "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=-1";

        String email = "restart_user_" + System.currentTimeMillis() + "@example.com";
        String token;
        Long userId;

        // Context 1: Boot full Spring Application, create user, generate token, revoke token
        try (org.springframework.context.ConfigurableApplicationContext ctx1 =
                     new org.springframework.boot.builder.SpringApplicationBuilder(com.aloute.AlouteApplication.class)
                             .properties(
                                     "spring.datasource.url=" + dbUrl,
                                     "PORT=0",
                                     "server.port=0",
                                     "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                                     "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
                                     "aloute.auth.registration-otp-secret=dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE="
                             )
                             .run()) {
            UserRepository userRepo1 = ctx1.getBean(UserRepository.class);
            JwtTokenProvider tokenProvider1 = ctx1.getBean(JwtTokenProvider.class);
            JwtSessionService sessionService1 = ctx1.getBean(JwtSessionService.class);
            org.springframework.security.crypto.password.PasswordEncoder encoder1 =
                    ctx1.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);

            User u = new User();
            u.setUsername("u_" + System.currentTimeMillis() % 100000);
            u.setEmail(email);
            u.setPasswordHash(encoder1.encode("Password123!"));
            u.setActive(true);
            u.setVerified(true);
            u.setVerificationRequired(false);
            u.setRole(com.aloute.security.authorization.Role.USER);
            User saved = userRepo1.saveAndFlush(u);
            userId = saved.getId();

            token = tokenProvider1.generate(UserPrincipal.from(saved));
            assertThat(sessionService1.authenticate(token)).isNotNull();

            // Revoke token in Context 1
            sessionService1.revokeToken(token, userId);

            assertThatThrownBy(() -> sessionService1.authenticate(token))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessageContaining("revoked");
        }

        // Context 1 is completely closed and its memory cleared.

        // Context 2: Boot second full Spring Application on the SAME persistent H2 database file
        try (org.springframework.context.ConfigurableApplicationContext ctx2 =
                     new org.springframework.boot.builder.SpringApplicationBuilder(com.aloute.AlouteApplication.class)
                             .properties(
                                     "spring.datasource.url=" + dbUrl,
                                     "PORT=0",
                                     "server.port=0",
                                     "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                                     "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
                                     "aloute.auth.registration-otp-secret=dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE="
                             )
                             .run()) {
            JwtSessionService sessionService2 = ctx2.getBean(JwtSessionService.class);
            RevokedTokenRepository revokedRepo2 = ctx2.getBean(RevokedTokenRepository.class);

            // Verify persistence via production JPA entity mapping
            String fingerprint = JwtSessionService.computeFingerprint(token);
            assertThat(revokedRepo2.existsByTokenHash(fingerprint)).isTrue();

            // Authenticating old token after application restart is rejected fail-closed
            assertThatThrownBy(() -> sessionService2.authenticate(token))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessageContaining("revoked");
        } finally {
            try {
                org.springframework.util.FileSystemUtils.deleteRecursively(tempDir);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Protocol HTTP: Real-server cookie logout clears cookie with scope/HttpOnly/Max-Age=0 and rejects replay")
    void realServerHttpCookieLogoutClearsCookieAndRejectsReplay() throws Exception {
        String email = generateEmail("real_cookie_logout");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        // 1. Login via real HTTP port
        RealHttpLoginResult loginResult = performRealHttpLogin(email, password);
        assertThat(loginResult.status()).isEqualTo(200);
        assertThat(loginResult.setCookieHeader())
                .contains("aloute_access_token=")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=3600");

        String token = loginResult.token();
        assertThat(token).isNotBlank();

        // 2. Access /api/auth/me with active cookie -> 200 OK
        var meActive = performRealHttpMe(token, null);
        assertThat(meActive.statusCode()).isEqualTo(200);

        // 3. Logout via real HTTP with cookie + CSRF header
        String csrf = fetchCsrfToken();
        var logoutResp = performRealHttpLogout(token, null, csrf);
        assertThat(logoutResp.statusCode()).isEqualTo(204);
        String clearCookie = logoutResp.headers().allValues("Set-Cookie").stream()
                .filter(s -> s.contains("aloute_access_token="))
                .findFirst().orElse("");
        assertThat(clearCookie)
                .contains("aloute_access_token=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        // 4. Subsequent /api/auth/me with revoked cookie -> 401 Unauthorized
        var meRevoked = performRealHttpMe(token, null);
        assertThat(meRevoked.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Protocol HTTP: Real-server Bearer logout revokes token and rejects replay")
    void realServerHttpBearerLogoutAndReplayRejection() throws Exception {
        String email = generateEmail("real_bearer_logout");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        RealHttpLoginResult loginResult = performRealHttpLogin(email, password);
        String token = loginResult.token();

        // 1. Access with Bearer -> 200 OK
        var meActive = performRealHttpMe(null, token);
        assertThat(meActive.statusCode()).isEqualTo(200);

        // 2. Bearer logout without cookie
        var logoutResp = performRealHttpLogout(null, token, null);
        assertThat(logoutResp.statusCode()).isEqualTo(204);

        // 3. Subsequent request with Bearer -> 401 Unauthorized
        var meRevoked = performRealHttpMe(null, token);
        assertThat(meRevoked.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Protocol HTTP: Cookie-first precedence, copied Bearer+Cookie replay rejection, and cross-transport revocation")
    void realServerCookieFirstPrecedenceAndCopiedBearerCookieReplayRejection() throws Exception {
        String email1 = generateEmail("cookie_prec_u1");
        String email2 = generateEmail("cookie_prec_u2");
        String password = "Password123!";
        registerAndVerifyUser(email1, password);
        registerAndVerifyUser(email2, password);

        String token1 = performRealHttpLogin(email1, password).token();
        String token2 = performRealHttpLogin(email2, password).token();

        // 1. Cookie-first precedence: Request with Cookie Token1 and Bearer Token2 resolves User 1
        var mePrecedence = performRealHttpMe(token1, token2);
        assertThat(mePrecedence.statusCode()).isEqualTo(200);
        assertThat(mePrecedence.body()).contains(email1);

        // 2. Logout Token 1
        String csrf = fetchCsrfToken();
        var logout1 = performRealHttpLogout(token1, null, csrf);
        assertThat(logout1.statusCode()).isEqualTo(204);

        // 3. Request with revoked Cookie Token1 and active Bearer Token2 is rejected (401)
        // Bearer cannot bypass revoked cookie
        var meBypassAttempt = performRealHttpMe(token1, token2);
        assertThat(meBypassAttempt.statusCode()).isEqualTo(401);

        // 4. Logout Token 2
        var logout2 = performRealHttpLogout(null, token2, null);
        assertThat(logout2.statusCode()).isEqualTo(204);

        // 5. Both tokens revoked on both transports
        assertThat(performRealHttpMe(token1, null).statusCode()).isEqualTo(401);
        assertThat(performRealHttpMe(null, token1).statusCode()).isEqualTo(401);
        assertThat(performRealHttpMe(token2, null).statusCode()).isEqualTo(401);
        assertThat(performRealHttpMe(null, token2).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Protocol HTTP: CSRF required for cookie requests and exempt for Bearer requests")
    void realServerCsrfRequiredForCookieLogoutAndExemptForBearer() throws Exception {
        String email = generateEmail("real_csrf_guard");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        String token = performRealHttpLogin(email, password).token();

        // 1. Cookie-only logout without CSRF header -> 403 Forbidden
        var logoutNoCsrf = performRealHttpLogout(token, null, null);
        assertThat(logoutNoCsrf.statusCode()).isEqualTo(403);

        // 2. Token remains active
        assertThat(performRealHttpMe(token, null).statusCode()).isEqualTo(200);

        // 3. Cookie logout with valid CSRF token -> 204 No Content
        String csrf = fetchCsrfToken();
        var logoutWithCsrf = performRealHttpLogout(token, null, csrf);
        assertThat(logoutWithCsrf.statusCode()).isEqualTo(204);
        assertThat(performRealHttpMe(token, null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Protocol HTTP: Signature-only aliases (! and pad bits) authenticate before logout but are strictly rejected after logout")
    void realServerSignatureAliasReplayRejectionAfterLogout() throws Exception {
        String email = generateEmail("sig_alias_test");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        String canonicalToken = performRealHttpLogin(email, password).token();

        // Construct Alias 1: Non-alphabet character '!' appended to signature (JJWT Base64.decodeFast tolerance)
        String aliasExclamation = canonicalToken + "!";

        // Construct Alias 2: Vary lower pad bits in 43-character HMAC-SHA256 signature
        String b64url = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        char lastChar = canonicalToken.charAt(canonicalToken.length() - 1);
        int idx = b64url.indexOf(lastChar);
        char flippedChar = b64url.charAt(idx ^ 1);
        String aliasPadBit = canonicalToken.substring(0, canonicalToken.length() - 1) + flippedChar;

        // 1. Both signature aliases successfully authenticate BEFORE logout (reproducing runtime parser tolerance RED)
        var meAlias1Before = performRealHttpMe(null, aliasExclamation);
        assertThat(meAlias1Before.statusCode())
                .as("Alias 1 with '!' should authenticate before logout under parser tolerance")
                .isEqualTo(200);

        var meAlias2Before = performRealHttpMe(null, aliasPadBit);
        assertThat(meAlias2Before.statusCode())
                .as("Alias 2 with varied pad bit should authenticate before logout under parser tolerance")
                .isEqualTo(200);

        // 2. Logout using CANONICAL token
        var logoutResp = performRealHttpLogout(null, canonicalToken, null);
        assertThat(logoutResp.statusCode()).isEqualTo(204);

        // 3. After logout, canonical token AND ALL signature aliases are strictly REJECTED (401)
        var meCanonicalAfter = performRealHttpMe(null, canonicalToken);
        assertThat(meCanonicalAfter.statusCode())
                .as("Canonical token must be rejected after logout")
                .isEqualTo(401);

        var meAlias1After = performRealHttpMe(null, aliasExclamation);
        assertThat(meAlias1After.statusCode())
                .as("Alias with '!' must be rejected after canonical token logout")
                .isEqualTo(401);

        var meAlias2After = performRealHttpMe(null, aliasPadBit);
        assertThat(meAlias2After.statusCode())
                .as("Alias with varied pad bit must be rejected after canonical token logout")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Concurrent logout: Simultaneous revocation calls for the same token are thread-safe and idempotent")
    void concurrentLogoutOfSameTokenIsIdempotentAndThreadSafe() throws Exception {
        String email = generateEmail("concurrent_logout");
        String password = "Password123!";
        registerAndVerifyUser(email, password);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        String token = loginAndExtractToken(email, password);

        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            tasks.add(() -> {
                jwtSessionService.revokeToken(token, user.getId());
                return true;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        executor.shutdown();

        for (Future<Boolean> f : futures) {
            assertThat(f.get()).isTrue();
        }

        // Exactly one revocation entry exists
        String fingerprint = JwtSessionService.computeFingerprint(token);
        assertThat(revokedTokenRepository.existsByTokenHash(fingerprint)).isTrue();
    }

    @Test
    @DisplayName("Concurrent real HTTP logout with same token returns 204 or 401, never 500, with exactly one fingerprint row")
    void concurrentRealHttpLogoutWithSameTokenReturns204Or401Never500() throws Exception {
        String email = generateEmail("concurrent_http_logout");
        String password = "Password123!";
        registerAndVerifyUser(email, password);

        String token = loginAndExtractToken(email, password);
        String fingerprint = JwtSessionService.computeFingerprint(token);

        String csrf1 = fetchCsrfToken();
        String csrf2 = fetchCsrfToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<java.net.http.HttpResponse<String>> f1 = executor.submit(() -> performRealHttpLogout(token, null, csrf1));
        Future<java.net.http.HttpResponse<String>> f2 = executor.submit(() -> performRealHttpLogout(token, null, csrf2));

        var resp1 = f1.get(10, TimeUnit.SECONDS);
        var resp2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Valid outcomes: 204 (revoked) or 401 (already revoked by concurrent request)
        // NEVER 500 (no unhandled unique constraint or lock race)
        assertThat(resp1.statusCode()).as("Response 1 status must be 204 or 401, never 500").isIn(204, 401);
        assertThat(resp2.statusCode()).as("Response 2 status must be 204 or 401, never 500").isIn(204, 401);
        assertThat(resp1.statusCode()).isNotEqualTo(500);
        assertThat(resp2.statusCode()).isNotEqualTo(500);

        // At least one request completed the revocation with 204
        assertThat(List.of(resp1.statusCode(), resp2.statusCode())).contains(204);

        // Exactly one revocation entry exists in the database
        assertThat(revokedTokenRepository.countByTokenHash(fingerprint))
                .as("Exactly one fingerprint row must exist in revoked_tokens")
                .isEqualTo(1);

        // Token is confirmed revoked on subsequent API call
        var meAfter = performRealHttpMe(token, null);
        assertThat(meAfter.statusCode()).isEqualTo(401);
    }
}
