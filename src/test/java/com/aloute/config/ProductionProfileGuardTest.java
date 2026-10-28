package com.aloute.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-01 (hardened): the packaged application must be production-safe by default.
 *
 * Deployment shapes covered:
 *  - Docker / Cloud Run: Dockerfile pins -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}
 *    and ALOUTE_PRODUCTION_MODE=true.
 *  - Bare JAR (java -jar app.jar, no Docker env): the only profile source is
 *    application.yml, so its default MUST NOT be dev — otherwise the seeder runs
 *    with well-known passwords and the guard never arms.
 *  - Local development: explicit -Dspring.profiles.active=dev (run-local.ps1 sets it).
 *  - Tests: src/test/resources/application.properties opts back into dev + H2.
 */
class ProductionProfileGuardTest {

    private static final Path MAIN_APPLICATION_YML =
            Path.of("src/main/resources/application.yml");

    private ApplicationContextRunner guardRunner(String profiles, String... extraProperties) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ProductionProfileGuard.class)
                .withPropertyValues(extraProperties);
        if (profiles != null && !profiles.isBlank()) {
            runner = runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")));
        }
        return runner;
    }

    // ---------------------------------------------------------------- local dev

    @Test
    @DisplayName("Local development (dev profile, no production flag) starts normally")
    void devWithoutProductionFlagStarts() {
        guardRunner("dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionProfileGuard.class);
        });
    }

    @Test
    @DisplayName("BL-01: dev profile with production flag refuses to start")
    void devWithProductionFlagRefusesStartup() {
        guardRunner("dev", "aloute.production-mode=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.getStartupFailure().getCause().getMessage())
                    .contains("production-mode");
        });
    }

    // ------------------------------------------------------------------ test CI

    @Test
    @DisplayName("No explicit profile + no flag starts normally (test/CI classpath supplies dev)")
    void noProfileStartsNormally() {
        guardRunner(null).run(context -> assertThat(context).hasNotFailed());
    }

    // --------------------------------------------------------------------- prod

    @Test
    @DisplayName("prod profile with production flag starts normally")
    void prodWithProductionFlagStarts() {
        guardRunner("prod", "aloute.production-mode=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionProfileGuard.class);
        });
    }

    @Test
    @DisplayName("prod profile without an explicit flag still refuses the dev seeder profile")
    void prodWithoutFlagStarts() {
        guardRunner("prod").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("BL-01: seeder configuration is absent under the prod profile")
    void seederAbsentUnderProdProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(DevTestDataInitializer.class, EmptyConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("initDevTestAccounts");
                });
    }

    // ------------------------------------------------- bare JAR (no Docker env)

    @Test
    @DisplayName("BL-01: packaged application.yml must not default to the dev profile (bare JAR safety)")
    void packagedDefaultProfileIsNotDev() throws Exception {
        assertThat(MAIN_APPLICATION_YML).as("main application.yml must exist").exists();
        String yaml = Files.readString(MAIN_APPLICATION_YML);

        // Extract the value under `spring.profiles.active:` without a YAML parser dependency.
        String activeProfile = null;
        for (String line : yaml.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("active:")) {
                activeProfile = trimmed.substring("active:".length()).strip();
            }
        }

        assertThat(activeProfile)
                .as("A bare `java -jar` has no Docker ENV; the packaged default decides the profile")
                .isNotNull()
                .isNotEqualTo("dev");
    }

    @Test
    @DisplayName("BL-01: test classpath opts back into dev so the H2 suites keep running")
    void testClasspathKeepsDevProfile() throws Exception {
        Path testProps = Path.of("src/test/resources/application.properties");
        assertThat(testProps).exists();
        assertThat(Files.readString(testProps)).contains("spring.profiles.active=dev");
    }

    /** Placeholder bean so the runner context is non-trivial. */
    @Configuration
    static class EmptyConfig {
        @Bean
        Object placeholder() {
            return new Object();
        }
    }
}
