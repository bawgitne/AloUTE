package com.aloute.integration;

import com.aloute.config.DevTestDataInitializer;
import com.aloute.dto.request.LoginRequest;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto",
                "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For"
        }
)
class DeploymentBoundaryAndForwardedHeadersIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Trusted forwarded HTTPS: login sets Secure, HttpOnly, SameSite=Lax, Path=/, Max-Age")
    void trustedForwardedHttpsLoginSetsSecureCookie() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-For", "127.0.0.1");

        LoginRequest req = new LoginRequest(DevTestDataInitializer.USER_EMAIL, DevTestDataInitializer.USER_PASSWORD);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String authCookie = cookies.stream()
                .filter(c -> c.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Auth cookie not found in response"));

        assertThat(authCookie).contains("Secure");
        assertThat(authCookie).contains("HttpOnly");
        assertThat(authCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(authCookie).contains("Path=/");
        assertThat(authCookie).contains("Max-Age=");
    }

    @Test
    @DisplayName("Plain HTTP without forwarded proto: login omits Secure flag")
    void plainHttpLoginOmitsSecureCookie() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest req = new LoginRequest(DevTestDataInitializer.USER_EMAIL, DevTestDataInitializer.USER_PASSWORD);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String authCookie = cookies.stream()
                .filter(c -> c.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Auth cookie not found in response"));

        assertThat(authCookie).doesNotContain("Secure");
        assertThat(authCookie).contains("HttpOnly");
        assertThat(authCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(authCookie).contains("Path=/");
    }

    @Test
    @DisplayName("Trusted forwarded HTTPS: logout clears cookie with Max-Age=0 and Secure flag")
    void trustedForwardedHttpsLogoutClearsCookieWithSecureFlag() {
        // 1. Login to obtain token
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Forwarded-Proto", "https");
        loginHeaders.set("X-Forwarded-For", "127.0.0.1");

        LoginRequest req = new LoginRequest(DevTestDataInitializer.USER_EMAIL, DevTestDataInitializer.USER_PASSWORD);
        ResponseEntity<String> loginRes = restTemplate.postForEntity("/api/auth/login", new HttpEntity<>(req, loginHeaders), String.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> loginCookies = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(loginCookies).isNotNull();

        String rawCookie = loginCookies.stream()
                .filter(c -> c.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        String token = rawCookie.split(";")[0].substring((JwtAuthenticationFilter.COOKIE_NAME + "=").length());

        // 2. Logout behind trusted proxy with token
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.set("X-Forwarded-Proto", "https");
        logoutHeaders.set("X-Forwarded-For", "127.0.0.1");
        logoutHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        logoutHeaders.set(HttpHeaders.COOKIE, JwtAuthenticationFilter.COOKIE_NAME + "=" + token);

        ResponseEntity<Void> logoutRes = restTemplate.postForEntity("/api/auth/logout", new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<String> cookies = logoutRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String clearedCookie = cookies.stream()
                .filter(c -> c.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cleared auth cookie not found"));

        assertThat(clearedCookie).contains("Max-Age=0");
        assertThat(clearedCookie).contains("Secure");
        assertThat(clearedCookie).contains("HttpOnly");
        assertThat(clearedCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(clearedCookie).contains("Path=/");
    }

    @Test
    @DisplayName("Trusted forwarded HTTPS: CSRF cookie receives Secure flag")
    void trustedForwardedHttpsCsrfCookieSetsSecure() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-For", "127.0.0.1");

        ResponseEntity<String> response = restTemplate.exchange("/api/posts", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String csrfCookie = cookies.stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN cookie not found"));

        assertThat(csrfCookie).contains("Secure");
        assertThat(csrfCookie).contains("Path=/");
    }

    @Test
    @DisplayName("Plain HTTP without forwarded proto: CSRF cookie omits Secure flag")
    void plainHttpCsrfCookieOmitsSecure() {
        ResponseEntity<String> response = restTemplate.exchange("/api/posts", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String csrfCookie = cookies.stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN cookie not found"));

        assertThat(csrfCookie).doesNotContain("Secure");
        assertThat(csrfCookie).contains("Path=/");
    }

    @Test
    @DisplayName("CSRF regression: Cookie-authenticated mutating request without CSRF token is rejected with 403 Forbidden")
    void cookieBasedMutatingRequestWithoutCsrfIsRejectedWithForbidden() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Forwarded-Proto", "https");
        loginHeaders.set("X-Forwarded-For", "127.0.0.1");

        LoginRequest req = new LoginRequest(DevTestDataInitializer.USER_EMAIL, DevTestDataInitializer.USER_PASSWORD);
        ResponseEntity<String> loginRes = restTemplate.postForEntity("/api/auth/login", new HttpEntity<>(req, loginHeaders), String.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> loginCookies = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        String rawCookie = loginCookies.stream()
                .filter(c -> c.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        String token = rawCookie.split(";")[0].substring((JwtAuthenticationFilter.COOKIE_NAME + "=").length());

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        postHeaders.set("X-Forwarded-Proto", "https");
        postHeaders.set("X-Forwarded-For", "127.0.0.1");
        postHeaders.set(HttpHeaders.COOKIE, JwtAuthenticationFilter.COOKIE_NAME + "=" + token);

        ResponseEntity<String> postRes = restTemplate.postForEntity("/api/posts", new HttpEntity<>("{\"content\":\"csrf attack\"}", postHeaders), String.class);
        assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Static deployment audit: compose files, Nginx templates, and application-prod.yml boundaries")
    @SuppressWarnings("unchecked")
    void staticDeploymentAudit() throws Exception {
        Yaml yaml = new Yaml();

        // 1. Verify application-prod.yml
        Map<String, Object> prodYml;
        try (FileInputStream fis = new FileInputStream("src/main/resources/application-prod.yml")) {
            prodYml = yaml.load(fis);
        }

        Map<String, Object> server = (Map<String, Object>) prodYml.get("server");
        assertThat(server).isNotNull();
        assertThat(server.get("forward-headers-strategy")).isEqualTo("native");

        Map<String, Object> tomcat = (Map<String, Object>) server.get("tomcat");
        assertThat(tomcat).isNotNull();
        Map<String, Object> remoteip = (Map<String, Object>) tomcat.get("remoteip");
        assertThat(remoteip).isNotNull();
        assertThat(remoteip.get("protocol-header")).isEqualTo("X-Forwarded-Proto");
        assertThat(remoteip.get("remote-ip-header")).isEqualTo("X-Forwarded-For");

        String internalProxies = (String) remoteip.get("internal-proxies");
        assertThat(internalProxies).doesNotContain(".*");
        assertThat(internalProxies).contains("127\\.");
        assertThat(internalProxies).contains("10\\.");
        assertThat(internalProxies).contains("172\\.");
        assertThat(internalProxies).contains("192\\.168\\.");

        Map<String, Object> spring = (Map<String, Object>) prodYml.get("spring");
        Map<String, Object> jpa = (Map<String, Object>) spring.get("jpa");
        Map<String, Object> hibernate = (Map<String, Object>) jpa.get("hibernate");
        assertThat(hibernate.get("ddl-auto")).isEqualTo("update"); // preserved per instruction

        // 2. Verify root docker-compose.yml
        Map<String, Object> rootYaml;
        try (FileInputStream fis = new FileInputStream("docker-compose.yml")) {
            rootYaml = yaml.load(fis);
        }
        Map<String, Object> rootServices = (Map<String, Object>) rootYaml.get("services");
        Map<String, Object> rootMysql = (Map<String, Object>) rootServices.get("mysql");
        assertThat(rootMysql.get("ports")).isNull(); // root MySQL publish removed

        Map<String, Object> rootApp = (Map<String, Object>) rootServices.get("app");
        Map<String, Object> rootAppEnv = (Map<String, Object>) rootApp.get("environment");
        assertThat(String.valueOf(rootAppEnv.get("ALOUTE_MAIL_HOST"))).contains("?ALOUTE_MAIL_HOST must be set");
        assertThat(String.valueOf(rootAppEnv.get("ALOUTE_MAIL_USERNAME"))).contains("?ALOUTE_MAIL_USERNAME must be set");
        assertThat(String.valueOf(rootAppEnv.get("ALOUTE_MAIL_PASSWORD"))).contains("?ALOUTE_MAIL_PASSWORD must be set");
        assertThat(String.valueOf(rootAppEnv.get("ALOUTE_APP_BASE_URL"))).contains("?ALOUTE_APP_BASE_URL must be set");

        Map<String, Object> rootNginx = (Map<String, Object>) rootServices.get("nginx");
        List<String> rootNginxVolumes = (List<String>) rootNginx.get("volumes");
        assertThat(rootNginxVolumes).anyMatch(v -> v.contains("aloute.conf.template") && v.contains("/etc/nginx/templates/aloute.conf.template:ro"));
        assertThat(rootNginxVolumes).anyMatch(v -> v.contains("render-nginx.sh") && v.contains("/deploy/scripts/render-nginx.sh:ro"));
        assertThat(rootNginxVolumes).anyMatch(v -> v.contains("/etc/letsencrypt:/etc/letsencrypt:ro"));
        assertThat(rootNginxVolumes).noneMatch(v -> v.contains("/etc/nginx/conf.d:ro")); // conf.d must be writable ephemeral

        List<String> rootEntrypoint = (List<String>) rootNginx.get("entrypoint");
        assertThat(rootEntrypoint).contains("/deploy/scripts/render-nginx.sh");

        Map<String, Object> rootNginxEnv = (Map<String, Object>) rootNginx.get("environment");
        assertThat(String.valueOf(rootNginxEnv.get("ALOUTE_DOMAIN"))).contains("?ALOUTE_DOMAIN must be set");

        // 3. Verify deploy/compose/compose.prod.yml
        Map<String, Object> prodComposeYaml;
        try (FileInputStream fis = new FileInputStream("deploy/compose/compose.prod.yml")) {
            prodComposeYaml = yaml.load(fis);
        }
        Map<String, Object> prodServices = (Map<String, Object>) prodComposeYaml.get("services");
        Map<String, Object> prodMysql = (Map<String, Object>) prodServices.get("mysql");
        assertThat(prodMysql.get("ports")).isNull(); // prod MySQL has no host publish

        Map<String, Object> prodApp = (Map<String, Object>) prodServices.get("app");
        Map<String, Object> prodAppEnv = (Map<String, Object>) prodApp.get("environment");
        assertThat(String.valueOf(prodAppEnv.get("ALOUTE_MAIL_HOST"))).contains("?ALOUTE_MAIL_HOST must be set");
        assertThat(String.valueOf(prodAppEnv.get("ALOUTE_MAIL_USERNAME"))).contains("?ALOUTE_MAIL_USERNAME must be set");
        assertThat(String.valueOf(prodAppEnv.get("ALOUTE_MAIL_PASSWORD"))).contains("?ALOUTE_MAIL_PASSWORD must be set");
        assertThat(String.valueOf(prodAppEnv.get("ALOUTE_APP_BASE_URL"))).contains("?ALOUTE_APP_BASE_URL must be set");

        Map<String, Object> prodNginx = (Map<String, Object>) prodServices.get("nginx");
        List<String> prodNginxVolumes = (List<String>) prodNginx.get("volumes");
        assertThat(prodNginxVolumes).anyMatch(v -> v.contains("aloute.conf.template") && v.contains("/etc/nginx/templates/aloute.conf.template:ro"));
        assertThat(prodNginxVolumes).anyMatch(v -> v.contains("render-nginx.sh") && v.contains("/deploy/scripts/render-nginx.sh:ro"));
        assertThat(prodNginxVolumes).anyMatch(v -> v.contains("/etc/letsencrypt:/etc/letsencrypt:ro"));
        assertThat(prodNginxVolumes).noneMatch(v -> v.contains("/etc/nginx/conf.d:ro")); // conf.d must be writable ephemeral

        List<String> prodEntrypoint = (List<String>) prodNginx.get("entrypoint");
        assertThat(prodEntrypoint).contains("/deploy/scripts/render-nginx.sh");

        Map<String, Object> prodNginxEnv = (Map<String, Object>) prodNginx.get("environment");
        assertThat(String.valueOf(prodNginxEnv.get("ALOUTE_DOMAIN"))).contains("?ALOUTE_DOMAIN must be set");

        // 4. Verify deploy/nginx/conf.d/aloute.conf.template
        String tlsTemplate = Files.readString(Path.of("deploy/nginx/conf.d/aloute.conf.template"));
        assertThat(tlsTemplate).contains("client_max_body_size 40M;");
        assertThat(tlsTemplate).contains("proxy_set_header Upgrade $http_upgrade;");
        assertThat(tlsTemplate).contains("proxy_set_header Connection \"upgrade\";");
        assertThat(tlsTemplate).contains("proxy_set_header X-Forwarded-Proto $scheme;");
        assertThat(tlsTemplate).contains("proxy_set_header X-Forwarded-Host $host;");

        // 5. Verify deploy/scripts/render-nginx.sh
        String renderScript = Files.readString(Path.of("deploy/scripts/render-nginx.sh"));
        assertThat(renderScript).contains(": \"${ALOUTE_DOMAIN:?ALOUTE_DOMAIN is required}\"");
        assertThat(renderScript).contains("nginx -t");
        assertThat(renderScript).contains("exec");
    }
}
