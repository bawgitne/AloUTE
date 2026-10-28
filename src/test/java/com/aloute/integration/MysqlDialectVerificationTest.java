package com.aloute.integration;

import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the admin-invariant concurrency suite is actually running against a real
 * MySQL 8 (not H2) and that the pessimistic-lock query used by {@code SystemAdminGuard}
 * executes on that engine.
 *
 * Only enabled when the CI job opts in with {@code -Daloute.it.mysql=true}, so the normal
 * H2 test run ignores it.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "aloute.it.mysql", matches = "true")
class MysqlDialectVerificationTest {

    @Autowired private DataSource dataSource;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("JDBC connection is genuinely MySQL 8 and the admin lock query runs on it")
    void runsOnRealMysql8() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            assertThat(meta.getDatabaseProductName()).containsIgnoringCase("MySQL");
            assertThat(meta.getDatabaseProductVersion()).startsWith("8");
        }
        // Exercises SELECT ... FOR UPDATE ... ORDER BY against real InnoDB row locks.
        assertThat(userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN)).isNotNull();
    }
}
