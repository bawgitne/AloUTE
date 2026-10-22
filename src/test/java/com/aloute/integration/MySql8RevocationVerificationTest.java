package com.aloute.integration;

import com.aloute.security.jwt.JwtProperties;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in MySQL 8 Verification Test for PR #51 Durable Logout Integration.
 * Executed only when explicitly enabled via system property (-Daloute.mysql8.enabled=true).
 *
 * Verifies against real MySQL 8 engine:
 * 1. Strict local environment guard and engine 8.0 / InnoDB validation.
 * 2. Baseline schema bootstrap via actual candidate JAR in create mode, data preservation,
 *    execution of mysql8_revoked_tokens.sql, detailed metadata inspection, constraint enforcement,
 *    and rerun idempotency.
 * 3. Actual JAR Child Process A execution with live HTTP pre/post logout revocation.
 * 4. Actual JAR Child Process B execution (process restart on same DB) proving persistence across JVM lifecycle.
 * 5. Live HTTP session security: 2FA challenge rejection and real password reset workflow (canonical DTO fields).
 * 6. Expired revocation cleanup via background scheduler and InnoDB foreign key ON DELETE CASCADE.
 */
@EnabledIfSystemProperty(named = "aloute.mysql8.enabled", matches = "true")
class MySql8RevocationVerificationTest {

    private static final String REQUIRED_SCHEMA = "aloutedb_test";
    private static final String REQUIRED_JWT_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final long JWT_EXPIRATION_SECONDS = 3600L;

    private final List<Process> activeProcesses = new CopyOnWriteArrayList<>();
    private final List<Path> tempLogFiles = new CopyOnWriteArrayList<>();

    private static String getHost() {
        return System.getenv().getOrDefault("MYSQL_HOST", System.getProperty("aloute.mysql8.host", "127.0.0.1"));
    }

    private static String getPort() {
        return System.getenv().getOrDefault("MYSQL_PORT", System.getProperty("aloute.mysql8.port", "3306"));
    }

    private static String getDatabase() {
        return System.getenv().getOrDefault("MYSQL_DATABASE", System.getProperty("aloute.mysql8.database", REQUIRED_SCHEMA));
    }

    private static String getUser() {
        return System.getenv().getOrDefault("MYSQL_USER", System.getProperty("aloute.mysql8.user", "root"));
    }

    private static String getPassword() {
        return System.getenv().getOrDefault("MYSQL_PASSWORD", System.getProperty("aloute.mysql8.password", ""));
    }

    private static String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), getUser(), getPassword());
    }

    private static void enforceStrictLocalEnvironmentGuard() {
        String host = getHost().trim().toLowerCase(Locale.ROOT);
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            throw new SecurityException("SAFETY GUARD: MySQL verification is restricted strictly to loopback (127.0.0.1/localhost). Rejected host: " + host);
        }
        String database = getDatabase().trim();
        if (!database.equals(REQUIRED_SCHEMA)) {
            throw new SecurityException("SAFETY GUARD: MySQL verification is restricted strictly to test database '" + REQUIRED_SCHEMA + "'. Rejected database: " + database);
        }
        String password = getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SAFETY GUARD: MYSQL_PASSWORD must be non-empty when aloute.mysql8.enabled=true.");
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static File findCandidateJar() {
        File targetDir = new File("target");
        if (!targetDir.isDirectory()) {
            throw new IllegalStateException("target/ directory not found. Please run 'mvn package -DskipTests' first.");
        }
        File[] jars = targetDir.listFiles((dir, name) -> name.startsWith("aloute-app-") && name.endsWith(".jar") && !name.endsWith("-sources.jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("Candidate JAR not found in target/. Please run 'mvn package -DskipTests' before running MySQL verification.");
        }
        return jars[0];
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private Process startChildJarProcess(File jarFile, int port, String ddlAuto) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Path logFile = Files.createTempFile("aloute-child-process-port-" + port + "-", ".log");
        tempLogFiles.add(logFile);

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-jar",
                jarFile.getAbsolutePath(),
                "--server.port=" + port,
                "--PORT=" + port,
                "--spring.datasource.url=" + getJdbcUrl(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.datasource.username=" + getUser(),
                "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
                "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                "--aloute.firebase.enabled=false",
                "--aloute.auth.revoked-token-cleanup-cron=*/2 * * * * *"
        );
        pb.environment().put("SPRING_DATASOURCE_PASSWORD", getPassword());
        pb.environment().put("ALOUTE_JWT_SECRET", REQUIRED_JWT_SECRET);
        pb.environment().put("ALOUTE_JWT_EXPIRATION_SECONDS", String.valueOf(JWT_EXPIRATION_SECONDS));
        pb.environment().put("aloute.auth.registration-otp-secret", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        // Redirect child process stdout/stderr directly to private temp logfile (no unjoined background thread)
        pb.redirectOutput(logFile.toFile());
        pb.redirectError(logFile.toFile());

        Process process = pb.start();
        activeProcesses.add(process);

        // Bounded readiness check via live HTTP ping
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest pingReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        long deadline = System.currentTimeMillis() + 45000;
        boolean ready = false;
        IOException lastIoException = null;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                stopProcess(process);
                throw new IllegalStateException("Child JAR process on port " + port + " terminated prematurely with exit code " + process.exitValue());
            }
            try {
                HttpResponse<String> resp = client.send(pingReq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 || resp.statusCode() == 401) {
                    ready = true;
                    break;
                }
            } catch (IOException e) {
                lastIoException = e;
                // Retry expected connection or handshake failure until child server is accepting HTTP
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopProcess(process);
                throw new RuntimeException("Readiness ping interrupted for port " + port, e);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopProcess(process);
                throw new RuntimeException("Readiness wait interrupted for port " + port, e);
            }
        }
        if (!ready) {
            stopProcess(process);
            String msg = "Child JAR process on port " + port + " failed to become ready within 45s";
            if (lastIoException != null) {
                throw new IllegalStateException(msg + ": " + lastIoException.getMessage(), lastIoException);
            } else {
                throw new IllegalStateException(msg + ".");
            }
        }
        return process;
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        if (!process.isAlive()) {
            activeProcesses.remove(process);
            return;
        }
        process.destroy();
        boolean interrupted = false;
        boolean exited = false;
        try {
            exited = process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
            // Interrupt cleared by catch; do not restore yet so second bounded wait can run
        }
        if (!exited && process.isAlive()) {
            process.destroyForcibly();
            try {
                exited = process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        try {
            if (process.isAlive() || !exited) {
                throw new IllegalStateException("Failed to terminate child process PID " + process.pid() + " within bounded timeout");
            }
            activeProcesses.remove(process);
            if (interrupted) {
                throw new IllegalStateException("Process termination wait was interrupted for PID " + process.pid());
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @AfterEach
    void tearDownAllProcesses() {
        List<Throwable> errors = new ArrayList<>();

        // Attempt closing all tracked processes (CopyOnWriteArrayList allows safe concurrent iteration)
        for (Process p : activeProcesses) {
            try {
                stopProcess(p);
            } catch (Throwable t) {
                errors.add(t);
            }
        }

        // Verify if any child is still alive or unproven exit
        boolean anyAlive = false;
        for (Process p : activeProcesses) {
            if (p != null && p.isAlive()) {
                anyAlive = true;
                break;
            }
        }

        // Do not delete log files or clear unresponsive process ownership if exit unproven
        // Only delete private logs once all children exited
        if (!anyAlive && activeProcesses.isEmpty()) {
            for (Path logFile : tempLogFiles) {
                try {
                    if (Files.deleteIfExists(logFile)) {
                        tempLogFiles.remove(logFile);
                    }
                } catch (IOException e) {
                    errors.add(new IOException("Failed to delete private log file: " + logFile + " - " + e.getMessage(), e));
                }
            }
        }

        // Accumulate all errors with suppressed causes; JUnit preserves original test body failure
        if (!errors.isEmpty()) {
            RuntimeException teardownException = new RuntimeException(
                    "Process teardown / cleanup encountered " + errors.size() + " failure(s)");
            for (Throwable err : errors) {
                teardownException.addSuppressed(err);
            }
            throw teardownException;
        }
    }

    private static String extractTokenFromResponse(String responseBody) {
        Pattern pattern = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Unable to extract token from login response body");
    }

    private static String extractCsrfTokenFromCookie(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String c : cookies) {
            if (c.startsWith("XSRF-TOKEN=")) {
                int end = c.indexOf(';');
                if (end == -1) end = c.length();
                return c.substring("XSRF-TOKEN=".length(), end);
            }
        }
        return "";
    }

    @Test
    @DisplayName("Comprehensive Real MySQL 8 Lifecycle: DDL, Metadata, Real JAR Restart & Live HTTP Verification")
    void verifyRealMySql8LifecycleAndRevocation() throws Exception {
        // ====================================================================
        // STAGE 1: Strict Local Environment Guard & Engine 8.0 Verification
        // ====================================================================
        enforceStrictLocalEnvironmentGuard();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Assert MySQL engine is 8.0.x
            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                assertThat(rs.next()).isTrue();
                String version = rs.getString(1);
                assertThat(version)
                        .as("Engine must be real MySQL 8.0.x, found: %s", version)
                        .startsWith("8.0");
            }
            // Assert storage engine is InnoDB
            try (ResultSet rs = stmt.executeQuery("SELECT @@default_storage_engine")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("Default engine must be InnoDB").isEqualToIgnoringCase("InnoDB");
            }
        }

        // ====================================================================
        // STAGE 2: Baseline Bootstrap, Migration DDL, Metadata & Constraints
        // ====================================================================
        File jarFile = findCandidateJar();

        // Bootstrap baseline tables with the actual candidate JAR child process in create mode
        int bootstrapPort = findFreePort();
        Process bootstrapProcess = startChildJarProcess(jarFile, bootstrapPort, "create");
        stopProcess(bootstrapProcess);

        String baselineUsername = "baseline_user_" + System.currentTimeMillis();
        String baselineEmail = "baseline_" + System.currentTimeMillis() + "@aloute.edu.vn";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Insert baseline user BEFORE applying migration DDL to prove data preservation
            // Include created_at and updated_at (NOT NULL columns)
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO users (username, email, password_hash, is_verified, verification_required, is_private, is_active, global_role, auth_version, two_factor_enabled, created_at, updated_at)
                VALUES (?, ?, 'dummy_hash_baseline', TRUE, FALSE, FALSE, TRUE, 'USER', 0, FALSE, NOW(6), NOW(6))
            """)) {
                ps.setString(1, baselineUsername);
                ps.setString(2, baselineEmail);
                ps.executeUpdate();
            }

            // If Hibernate autogenerated revoked_tokens, prove it is empty and drop ONLY revoked_tokens
            DatabaseMetaData dbMeta = conn.getMetaData();
            boolean tableExists = false;
            try (ResultSet rs = dbMeta.getTables(REQUIRED_SCHEMA, null, "revoked_tokens", null)) {
                if (rs.next()) tableExists = true;
            }
            if (tableExists) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM revoked_tokens")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong(1)).as("Autogenerated table must be empty").isZero();
                }
                stmt.execute("DROP TABLE revoked_tokens");
            }

            // Apply actual unchanged src/main/resources/db/migration/mysql8_revoked_tokens.sql
            ClassPathResource migrationResource = new ClassPathResource("db/migration/mysql8_revoked_tokens.sql");
            assertThat(migrationResource.exists()).as("mysql8_revoked_tokens.sql must exist on classpath").isTrue();
            String migrationSql;
            try (InputStream is = migrationResource.getInputStream()) {
                migrationSql = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }

            stmt.execute(migrationSql);

            // Assert baseline user row was preserved through migration
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
                ps.setString(1, baselineEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Baseline user row must survive migration").isEqualTo(1);
                }
            }

            // Assert rerun idempotency
            stmt.execute(migrationSql);

            // Detailed metadata verification
            // Table engine must be InnoDB
            try (ResultSet rs = stmt.executeQuery("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("ENGINE")).isEqualToIgnoringCase("InnoDB");
            }

            // Columns, types, and nullability
            try (ResultSet rs = stmt.executeQuery("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME").toLowerCase();
                    cols.add(col);
                    if (col.equals("id")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("bigint");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                        assertThat(rs.getString("EXTRA")).contains("auto_increment");
                    } else if (col.equals("token_hash")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("varchar");
                        assertThat(rs.getLong("CHARACTER_MAXIMUM_LENGTH")).isEqualTo(64L);
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    } else if (col.equals("user_id")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("bigint");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("YES");
                    } else if (col.equals("expires_at")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("datetime");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    } else if (col.equals("created_at")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("datetime");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    }
                }
                assertThat(cols).containsExactlyInAnyOrder("id", "token_hash", "user_id", "expires_at", "created_at");
            }

            // Unique constraint on token_hash
            try (ResultSet rs = stmt.executeQuery("""
                SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens' AND CONSTRAINT_TYPE = 'UNIQUE'
            """.formatted(REQUIRED_SCHEMA))) {
                boolean foundUk = false;
                while (rs.next()) {
                    if ("uk_revoked_tokens_token_hash".equalsIgnoreCase(rs.getString("CONSTRAINT_NAME"))) {
                        foundUk = true;
                    }
                }
                assertThat(foundUk).as("Unique constraint uk_revoked_tokens_token_hash must exist").isTrue();
            }

            // Foreign Key constraint referencing users(id) ON DELETE CASCADE
            try (ResultSet rs = stmt.executeQuery("""
                SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                boolean foundFk = false;
                while (rs.next()) {
                    if ("fk_revoked_tokens_user".equalsIgnoreCase(rs.getString("CONSTRAINT_NAME"))) {
                        foundFk = true;
                        assertThat(rs.getString("REFERENCED_TABLE_NAME")).isEqualToIgnoringCase("users");
                        assertThat(rs.getString("DELETE_RULE")).isEqualToIgnoringCase("CASCADE");
                    }
                }
                assertThat(foundFk).as("Foreign key fk_revoked_tokens_user with CASCADE must exist").isTrue();
            }

            // Index on expires_at
            try (ResultSet rs = stmt.executeQuery("""
                SELECT INDEX_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens' AND INDEX_NAME = 'idx_revoked_tokens_expires_at'
            """.formatted(REQUIRED_SCHEMA))) {
                assertThat(rs.next()).as("Index idx_revoked_tokens_expires_at must exist").isTrue();
            }

            // Test constraint behavior with invalid inserts
            // 1. Unique constraint: duplicate token_hash insert must fail
            stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('uk_test_hash_unique', NULL, NOW() + INTERVAL 1 HOUR)");
            assertThatThrownBy(() -> stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('uk_test_hash_unique', NULL, NOW() + INTERVAL 1 HOUR)"))
                    .isInstanceOf(SQLException.class);

            // 2. Foreign Key constraint: referencing non-existent user must fail
            assertThatThrownBy(() -> stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('fk_test_invalid_user', 999999999, NOW() + INTERVAL 1 HOUR)"))
                    .isInstanceOf(SQLException.class);
        }

        // ====================================================================
        // STAGE 3: Actual JAR Child Process A & Live HTTP Pre/Post Logout
        // ====================================================================
        int portA = findFreePort();
        Process processA = null;
        Process processB = null;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String testUsername = "live_user_" + System.currentTimeMillis();
        String testEmail = "live_" + System.currentTimeMillis() + "@aloute.edu.vn";
        String initialPassword = "Password123!";
        String newPassword = "NewSecurePassword456!";

        String tokenA;
        String tokenB;
        String fingerprintA;

        try {
            processA = startChildJarProcess(jarFile, portA, "validate");

            // 1. Live HTTP Register
            String registerJson = "{\"username\":\"" + testUsername + "\",\"email\":\"" + testEmail + "\",\"password\":\"" + initialPassword + "\"}";
            HttpRequest regReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(registerJson))
                    .build();
            HttpResponse<String> regResp = httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());
            assertThat(regResp.statusCode()).as("Registration must return 201 Created").isEqualTo(201);

            // Mark user verified and verificationRequired=false via direct JDBC
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_verified = TRUE, verification_required = FALSE WHERE email = ?")) {
                ps.setString(1, testEmail);
                ps.executeUpdate();
            }

            // 2. Live HTTP Login 1 -> Token A
            String loginJson = "{\"email\":\"" + testEmail + "\",\"password\":\"" + initialPassword + "\"}";
            HttpRequest loginReq1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                    .build();
            HttpResponse<String> loginResp1 = httpClient.send(loginReq1, HttpResponse.BodyHandlers.ofString());
            assertThat(loginResp1.statusCode()).as("Login 1 must return 200 OK").isEqualTo(200);
            tokenA = extractTokenFromResponse(loginResp1.body());
            fingerprintA = JwtSessionService.computeFingerprint(tokenA);
            assertThat(tokenA != null && !tokenA.isBlank()).as("Token A must be non-empty").isTrue();

            // 3. Live HTTP Login 2 -> Token B (Sibling session)
            HttpResponse<String> loginResp2 = httpClient.send(loginReq1, HttpResponse.BodyHandlers.ofString());
            assertThat(loginResp2.statusCode()).as("Login 2 must return 200 OK").isEqualTo(200);
            tokenB = extractTokenFromResponse(loginResp2.body());
            assertThat(tokenB != null && !tokenB.isBlank()).as("Token B must be non-empty").isTrue();

            // Sibling session tokens must be distinct (assert boolean without printing JWTs)
            boolean tokensDistinct = !tokenA.equals(tokenB);
            assertThat(tokensDistinct).as("Sibling session tokens must be distinct").isTrue();

            // 4. Verify both Token A and Token B work before logout
            HttpRequest reqProfileA = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenA)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileA, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            HttpRequest reqProfileB = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenB)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileB, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            // 5. Live HTTP Logout Token A
            HttpRequest logoutReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/logout"))
                    .header("Authorization", "Bearer " + tokenA)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> logoutResp = httpClient.send(logoutReq, HttpResponse.BodyHandlers.ofString());
            assertThat(logoutResp.statusCode()).as("Logout must return 204 No Content").isEqualTo(204);

            // 6. Live HTTP Assertions: Token A rejected 401, Sibling Token B valid 200
            assertThat(httpClient.send(reqProfileA, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(httpClient.send(reqProfileB, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            // 7. Direct JDBC check: Revoked token row exists in MySQL
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Revoked token row must exist in MySQL").isEqualTo(1);
                }
            }

            // Stop Process A cleanly
            stopProcess(processA);
            processA = null;

            // ====================================================================
            // STAGE 4: Actual JAR Child Process B & Persistence Across Restart
            // ====================================================================
            int portB = findFreePort();
            processB = startChildJarProcess(jarFile, portB, "validate");

            // 1. Live HTTP Assertions on Process B: Token A STILL rejected 401, Token B STILL valid 200
            HttpRequest reqProfileA_B = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenA)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileA_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Token A must remain rejected 401 across JAR process restart")
                    .isEqualTo(401);

            HttpRequest reqProfileB_B = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenB)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileB_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Sibling Token B must remain valid 200 across JAR process restart")
                    .isEqualTo(200);

            // 2. Direct JDBC check: Revoked row still persisted in MySQL
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // ====================================================================
            // STAGE 5: Live HTTP 2FA Purpose Isolation & Real Password Reset Flow
            // ====================================================================
            // 1. 2FA challenge ticket rejected as access credential via HTTP -> 401
            Long testUserId;
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE email = ?")) {
                ps.setString(1, testEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    testUserId = rs.getLong(1);
                }
            }

            MockEnvironment mockEnv = new MockEnvironment();
            mockEnv.setProperty("ALOUTE_JWT_SECRET", REQUIRED_JWT_SECRET);
            mockEnv.setProperty("ALOUTE_JWT_EXPIRATION_SECONDS", String.valueOf(JWT_EXPIRATION_SECONDS));
            JwtProperties jwtProps = new JwtProperties(mockEnv);
            JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProps);
            String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUserId, "TOTP", 300);

            HttpRequest reqChallenge = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + challengeTicket)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqChallenge, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("2FA challenge ticket must be rejected with 401 Unauthorized")
                    .isEqualTo(401);

            // 2. Real Password Reset Workflow via live HTTP with canonical DTO fields
            String rawResetToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String resetTokenHash = sha256Hex(rawResetToken);

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO password_reset_tokens (token_hash, user_id, auth_version, expires_at, created_at)
                     VALUES (?, ?, 0, NOW() + INTERVAL 1 HOUR, NOW())
                 """)) {
                ps.setString(1, resetTokenHash);
                ps.setLong(2, testUserId);
                ps.executeUpdate();
            }

            // Fetch CSRF token from /api/auth/me
            HttpRequest meReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/me"))
                    .GET()
                    .build();
            HttpResponse<String> meResp = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
            String csrfToken = extractCsrfTokenFromCookie(meResp);

            // Execute live HTTP POST /api/auth/reset-password with canonical fields: token, password, confirmPassword
            String resetBody = "{\"token\":\"" + rawResetToken + "\",\"password\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}";
            HttpRequest.Builder resetReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/reset-password"))
                    .header("Content-Type", "application/json");
            if (!csrfToken.isEmpty()) {
                resetReqBuilder.header("X-XSRF-TOKEN", csrfToken);
                resetReqBuilder.header("Cookie", "XSRF-TOKEN=" + csrfToken);
            }
            HttpRequest resetReq = resetReqBuilder.POST(HttpRequest.BodyPublishers.ofString(resetBody)).build();
            HttpResponse<String> resetResp = httpClient.send(resetReq, HttpResponse.BodyHandlers.ofString());
            assertThat(resetResp.statusCode()).as("Password reset must return 200 OK").isEqualTo(200);

            // 3. Old Token B now invalidated (stale authVersion) -> HTTP 401
            assertThat(httpClient.send(reqProfileB_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Old Session Token B must be invalidated following password reset")
                    .isEqualTo(401);

            // 4. Login with new password succeeds -> HTTP 200
            String newLoginJson = "{\"email\":\"" + testEmail + "\",\"password\":\"" + newPassword + "\"}";
            HttpRequest newLoginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(newLoginJson))
                    .build();
            HttpResponse<String> newLoginResp = httpClient.send(newLoginReq, HttpResponse.BodyHandlers.ofString());
            assertThat(newLoginResp.statusCode()).as("Login with new password must return 200 OK").isEqualTo(200);

            // ====================================================================
            // STAGE 6: Expired Cleanup Scheduler & FK ON DELETE CASCADE
            // ====================================================================
            // 1. Expired token cleanup via running child process scheduler
            String expiredHash = "expired_hash_mysql8_" + System.currentTimeMillis();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO revoked_tokens (token_hash, user_id, expires_at, created_at)
                     VALUES (?, ?, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR)
                 """)) {
                ps.setString(1, expiredHash);
                ps.setLong(2, testUserId);
                ps.executeUpdate();
            }

            // Wait for background cron (*/2 * * * * *) in Child Process B to execute cleanup
            long cleanupDeadline = System.currentTimeMillis() + 8000;
            boolean expiredCleaned = false;
            while (System.currentTimeMillis() < cleanupDeadline) {
                try (Connection conn = getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                    ps.setString(1, expiredHash);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            expiredCleaned = true;
                            break;
                        }
                    }
                }
                Thread.sleep(1000);
            }
            assertThat(expiredCleaned).as("Expired revoked token must be removed by background cleanup scheduler").isTrue();

            // Active revoked token (Token A) remains intact in DB
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Active revoked token must be retained during expired cleanup").isEqualTo(1);
                }
            }

            // 2. Foreign Key ON DELETE CASCADE test
            long cascadeUserId;
            String cascadeEmail = "cascade_" + System.currentTimeMillis() + "@aloute.edu.vn";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO users (username, email, password_hash, is_verified, verification_required, is_private, is_active, global_role, auth_version, two_factor_enabled, created_at, updated_at)
                     VALUES (?, ?, 'hash', TRUE, FALSE, FALSE, TRUE, 'USER', 0, FALSE, NOW(6), NOW(6))
                 """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "cascade_user_" + System.currentTimeMillis());
                ps.setString(2, cascadeEmail);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertThat(rs.next()).isTrue();
                    cascadeUserId = rs.getLong(1);
                }
            }

            String cascadeTokenHash = "cascade_token_hash_" + System.currentTimeMillis();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES (?, ?, NOW() + INTERVAL 1 HOUR)")) {
                ps.setString(1, cascadeTokenHash);
                ps.setLong(2, cascadeUserId);
                ps.executeUpdate();
            }

            // Delete user row directly via JDBC
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setLong(1, cascadeUserId);
                ps.executeUpdate();
            }

            // Verify revoked token was cascaded and deleted automatically by InnoDB FK constraint
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, cascadeTokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Revoked token row must be deleted by FK ON DELETE CASCADE").isZero();
                }
            }
        } finally {
            try {
                if (processA != null) {
                    stopProcess(processA);
                }
            } finally {
                if (processB != null) {
                    stopProcess(processB);
                }
            }
        }
    }
}
