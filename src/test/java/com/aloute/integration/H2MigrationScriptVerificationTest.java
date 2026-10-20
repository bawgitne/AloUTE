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
}
