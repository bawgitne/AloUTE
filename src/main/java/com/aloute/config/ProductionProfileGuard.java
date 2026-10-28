package com.aloute.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fail-closed operational guard (BL-01): the dev profile seeds accounts with
 * well-known passwords (DevTestDataInitializer), so it must never coexist with
 * a production deployment. The guard is enabled by {@code aloute.production-mode}
 * (true by default in the prod profile and in the production Docker image) and
 * refuses startup when the dev profile is active at the same time. Operators who
 * legitimately need dev-on-a-prod-flagged instance must explicitly set
 * {@code ALOUTE_PRODUCTION_MODE=false} on top of selecting the profile.
 */
@Component
public class ProductionProfileGuard {

    static final String PRODUCTION_MODE_PROPERTY = "aloute.production-mode";

    private final Environment environment;

    ProductionProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void enforceDevProfileIsolation() {
        boolean productionMode = environment.getProperty(PRODUCTION_MODE_PROPERTY, Boolean.class, false);
        boolean devProfileActive = environment.acceptsProfiles(Profiles.of("dev"));
        if (productionMode && devProfileActive) {
            throw new IllegalStateException(
                    "Refusing to start: the dev profile is active while aloute.production-mode=true. "
                            + "The dev profile seeds accounts with well-known passwords. "
                            + "Set SPRING_PROFILES_ACTIVE=prod for production deployments.");
        }
    }
}
