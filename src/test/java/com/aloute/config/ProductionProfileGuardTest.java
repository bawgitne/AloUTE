package com.aloute.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-01: the dev profile (well-known seed passwords) must be isolated from
 * production-flagged instances. ProductionProfileGuard refuses startup when
 * aloute.production-mode=true coexists with the dev profile; the seeder
 * configuration itself is @Profile("dev") and must be absent under prod.
 */
class ProductionProfileGuardTest {

    private ApplicationContextRunner guardRunner(String profiles, String... extraProperties) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionProfileGuard.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withPropertyValues(extraProperties);
    }

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

    @Test
    @DisplayName("prod profile with production flag starts normally")
    void prodWithProductionFlagStarts() {
        guardRunner("prod", "aloute.production-mode=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionProfileGuard.class);
        });
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

    /** Placeholder bean so the runner context is non-trivial; the seeder bean method is never resolved under prod. */
    @Configuration
    static class EmptyConfig {
        @Bean
        Object placeholder() {
            return new Object();
        }
    }
}
