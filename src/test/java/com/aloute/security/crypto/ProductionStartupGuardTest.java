package com.aloute.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deployment gate: in production mode the master key must be validated at startup and
 * the packaged prod configuration must arm that mode itself, so no operator has to
 * remember to opt in.
 *
 * <p>The context here is deliberately disposable: it wires only the encryption service
 * and feeds it fake keys, so no database and no real secret is involved. A full
 * {@code SpringApplication} boot with the prod profile is not used because it requires a
 * live MySQL 8; the datasource wiring is covered by the MySQL 8 CI jobs instead.
 */
class ProductionStartupGuardTest {

    private static final String DEV_DEFAULT = "aloute-dev-master-encryption-key-for-2fa-totp-secrets-32b!";
    private static final String STRONG_KEY = "unique-strong-production-master-key-9f2c";
    private static final Path PROD_YML = Path.of("src/main/resources/application-prod.yml");

    /** Boots a disposable production-mode context whose master key is {@code masterKey}. */
    private ApplicationContextRunner productionModeRunner(String masterKey) {
        return new ApplicationContextRunner()
                .withUserConfiguration(EncryptionConfig.class)
                .withPropertyValues(
                        "aloute.production-mode=true",
                        "aloute.auth.two-factor-master-key=" + (masterKey == null ? "" : masterKey));
    }

    private void expectRefusal(String masterKey) {
        productionModeRunner(masterKey).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                    .as("the startup failure must name the missing/weak master key")
                    .contains("two-factor-master-key");
        });
    }

    @Test
    @DisplayName("A valid strong master key starts the guarded production context")
    void strongKeyStarts() {
        productionModeRunner(STRONG_KEY).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TwoFactorEncryptionService.class);
        });
    }

    @Test
    @DisplayName("A missing master key refuses production startup")
    void missingKeyRefusesStartup() {
        expectRefusal(null);
    }

    @Test
    @DisplayName("A blank master key refuses production startup")
    void blankKeyRefusesStartup() {
        expectRefusal("   ");
    }

    @Test
    @DisplayName("A weak master key refuses production startup")
    void weakKeyRefusesStartup() {
        expectRefusal("short");
    }

    @Test
    @DisplayName("The public dev default key refuses production startup")
    void devDefaultKeyRefusesStartup() {
        expectRefusal(DEV_DEFAULT);
    }

    @Test
    @DisplayName("The dev default key still starts outside production mode")
    void devDefaultAllowedOutsideProduction() {
        new ApplicationContextRunner()
                .withUserConfiguration(EncryptionConfig.class)
                .withPropertyValues("aloute.auth.two-factor-master-key=" + DEV_DEFAULT)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("BL-07: application-prod.yml sources the master key from the environment with no baked-in default")
    void prodYmlSourcesKeyFromEnvironment() throws Exception {
        assertThat(PROD_YML).exists();
        String yaml = Files.readString(PROD_YML);
        assertThat(yaml)
                .as("the master key must come from the environment with no baked-in default")
                .contains("two-factor-master-key: ${ALOUTE_TWO_FACTOR_MASTER_KEY:}");
        // Whether `aloute.production-mode` is armed by the prod profile is asserted by
        // ProductionProfileGuardTest, which owns that contract on master.
    }

    @Configuration
    static class EncryptionConfig {
        @Bean
        TwoFactorEncryptionService twoFactorEncryptionService(
                @Value("${aloute.auth.two-factor-master-key:}") String key,
                Environment environment) {
            return new TwoFactorEncryptionService(key, environment);
        }
    }
}
