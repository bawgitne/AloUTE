package com.aloute.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class H2MigrationScriptVerificationTest {

    @Test
    @DisplayName("H2 migration script executes safely on legacy schema and preserves existing users")
    void verifyH2MigrationScript() throws Exception {
        String dbUrl = "jdbc:h2:mem:legacy_migration_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            Statement stmt = conn.createStatement();

            // 1. Create baseline legacy users table without auth_version, version, or verification_required
            stmt.execute("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    is_private BOOLEAN NOT NULL DEFAULT FALSE,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    global_role VARCHAR(32) NOT NULL DEFAULT 'USER',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // 2. Insert legacy user records (with NULL auth_version, NULL version implicitly)
            stmt.execute("""
                INSERT INTO users (username, email, password_hash, is_verified, is_active)
                VALUES ('legacy_alice', 'alice@example.com', 'hashed_pwd_alice', TRUE, TRUE),
                       ('legacy_bob', 'bob@example.com', 'hashed_pwd_bob', FALSE, TRUE);
            """);

            // 3. Load and execute h2_auth_and_registration_otp.sql via Spring ScriptUtils
            ClassPathResource scriptResource = new ClassPathResource("db/migration/h2_auth_and_registration_otp.sql");
            ScriptUtils.executeSqlScript(conn, scriptResource);

            // 4. Verify columns exist and legacy users are preserved and backfilled
            ResultSet rs = stmt.executeQuery("SELECT username, auth_version, version, verification_required FROM users ORDER BY username ASC");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("username")).isEqualTo("legacy_alice");
            assertThat(rs.getLong("auth_version")).isEqualTo(0L);
            assertThat(rs.getLong("version")).isEqualTo(0L);
            assertThat(rs.getBoolean("verification_required")).isFalse();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("username")).isEqualTo("legacy_bob");
            assertThat(rs.getLong("auth_version")).isEqualTo(0L);
            assertThat(rs.getLong("version")).isEqualTo(0L);
            assertThat(rs.getBoolean("verification_required")).isFalse();
            assertThat(rs.next()).isFalse();

            // 5. Verify created tables
            ResultSet tableRs = stmt.executeQuery("SELECT COUNT(*) FROM password_reset_tokens");
            assertThat(tableRs.next()).isTrue();
            assertThat(tableRs.getInt(1)).isEqualTo(0);

            ResultSet otpRs = stmt.executeQuery("SELECT COUNT(*) FROM registration_otps");
            assertThat(otpRs.next()).isTrue();
            assertThat(otpRs.getInt(1)).isEqualTo(0);

            // 6. Test idempotency: executing script a second time must succeed without errors
            ScriptUtils.executeSqlScript(conn, scriptResource);
        }
    }

    @Test
    @DisplayName("H2 migration script for revoked_tokens executes safely and idempotently")
    void verifyH2RevokedTokensMigrationScript() throws Exception {
        String dbUrl = "jdbc:h2:mem:revoked_tokens_migration_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL
                );
            """);

            ClassPathResource scriptResource = new ClassPathResource("db/migration/h2_revoked_tokens.sql");
            ScriptUtils.executeSqlScript(conn, scriptResource);

            ResultSet tableRs = stmt.executeQuery("SELECT COUNT(*) FROM revoked_tokens");
            assertThat(tableRs.next()).isTrue();
            assertThat(tableRs.getInt(1)).isZero();

            // Test idempotency
            ScriptUtils.executeSqlScript(conn, scriptResource);
        }
    }

    @Test
    @DisplayName("H2 migration script for follow requests executes safely and is idempotent")
    void verifyH2FollowRequestsMigrationScript() throws Exception {
        String dbUrl = "jdbc:h2:mem:follow_requests_migration_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            Statement stmt = conn.createStatement();

            // Minimal legacy users table the follow-request foreign keys point at.
            stmt.execute("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL
                );
                INSERT INTO users (username, email, password_hash) VALUES
                    ('legacy_requester', 'req@example.com', 'h'),
                    ('legacy_target', 'tgt@example.com', 'h');
            """);

            ClassPathResource scriptResource = new ClassPathResource("db/migration/h2_follow_requests.sql");
            ScriptUtils.executeSqlScript(conn, scriptResource);

            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM follow_requests");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();

            // A pending pair row round-trips through the new table.
            stmt.execute("INSERT INTO follow_requests (requester_id, target_id, status, created_at, updated_at) "
                    + "SELECT MIN(id), MAX(id), 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM users");
            ResultSet pairRs = stmt.executeQuery("SELECT status FROM follow_requests");
            assertThat(pairRs.next()).isTrue();
            assertThat(pairRs.getString("status")).isEqualTo("PENDING");

            // Idempotency: a second run must not corrupt or duplicate anything.
            ScriptUtils.executeSqlScript(conn, scriptResource);
            ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM follow_requests");
            assertThat(countRs.next()).isTrue();
            assertThat(countRs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("H2 migration script for community post moderation executes safely, backfills and is idempotent")
    void verifyH2CommunityPostModerationMigrationScript() throws Exception {
        String dbUrl = "jdbc:h2:mem:community_moderation_migration_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            Statement stmt = conn.createStatement();

            // Legacy schema without approval columns
            stmt.execute("""
                CREATE TABLE communities (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    owner_id BIGINT NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    slug VARCHAR(100) NOT NULL UNIQUE,
                    is_private BOOLEAN NOT NULL DEFAULT FALSE,
                    member_count BIGINT NOT NULL DEFAULT 0,
                    post_count BIGINT NOT NULL DEFAULT 0
                );
            """);
            stmt.execute("""
                CREATE TABLE posts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    content VARCHAR(4000) NOT NULL,
                    community_id BIGINT,
                    deleted_at TIMESTAMP
                );
            """);
            stmt.execute("""
                CREATE TABLE moderation_actions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    actor_user_id BIGINT NOT NULL,
                    target_type VARCHAR(32) NOT NULL,
                    target_id BIGINT NOT NULL,
                    action_type VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("INSERT INTO communities (owner_id, name, slug) VALUES (1, 'Legacy Comm', 'legacy-comm')");
            stmt.execute("INSERT INTO posts (user_id, content, community_id) VALUES (1, 'legacy community post', 1)");
            stmt.execute("INSERT INTO posts (user_id, content, community_id) VALUES (1, 'legacy standalone post', NULL)");
            stmt.execute("INSERT INTO moderation_actions (actor_user_id, target_type, target_id, action_type) VALUES (1, 'POST', 1, 'POST_REMOVE')");

            ClassPathResource scriptResource = new ClassPathResource("db/migration/h2_community_post_moderation.sql");
            ScriptUtils.executeSqlScript(conn, scriptResource);

            // Approval switch defaults to FALSE (behavior preserved)
            ResultSet commRs = stmt.executeQuery("SELECT require_post_approval FROM communities WHERE id = 1");
            assertThat(commRs.next()).isTrue();
            assertThat(commRs.getBoolean("require_post_approval")).isFalse();

            // Legacy community posts backfilled to APPROVED; standalone posts stay NULL
            ResultSet postRs = stmt.executeQuery(
                    "SELECT community_id, moderation_status FROM posts ORDER BY id ASC");
            assertThat(postRs.next()).isTrue();
            assertThat(postRs.getLong("community_id")).isEqualTo(1L);
            assertThat(postRs.getString("moderation_status")).isEqualTo("APPROVED");
            assertThat(postRs.next()).isTrue();
            assertThat(postRs.getLong("community_id")).isEqualTo(0L);
            assertThat(postRs.getString("moderation_status")).isNull();

            // moderation_actions gained the community scope column
            ResultSet actionRs = stmt.executeQuery(
                    "SELECT community_id FROM moderation_actions WHERE id = 1");
            assertThat(actionRs.next()).isTrue();
            assertThat(actionRs.getObject("community_id")).isNull();

            // Idempotency: running the script a second time must succeed
            ScriptUtils.executeSqlScript(conn, scriptResource);
        }
    }

    @Test
    @DisplayName("H2 login-lockout script adds failure accounting columns idempotently")
    void verifyH2LoginLockoutScript() throws Exception {
        String dbUrl = "jdbc:h2:mem:login_lockout_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(dbUrl, "sa", "")) {
            Statement stmt = conn.createStatement();

            stmt.execute("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    is_private BOOLEAN NOT NULL DEFAULT FALSE,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    global_role VARCHAR(32) NOT NULL DEFAULT 'USER',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                INSERT INTO users (username, email, password_hash, is_verified, is_active)
                VALUES ('lockout_legacy', 'lockout@example.com', 'hashed_pwd', TRUE, TRUE);
            """);

            ClassPathResource scriptResource = new ClassPathResource("db/migration/h2_login_lockout.sql");
            ScriptUtils.executeSqlScript(conn, scriptResource);

            ResultSet rs = stmt.executeQuery(
                    "SELECT failed_login_attempts, locked_until FROM users WHERE username = 'lockout_legacy'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("failed_login_attempts")).isEqualTo(0);
            assertThat(rs.getObject("locked_until")).isNull();

            // Idempotency: running the script a second time must succeed
            ScriptUtils.executeSqlScript(conn, scriptResource);
        }
    }
}
