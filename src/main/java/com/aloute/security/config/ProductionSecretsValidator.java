package com.aloute.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecretsValidator {

    private final Environment environment;

    @PostConstruct
    void validateProductionSecrets() {
        String dbPassword;
        try {
            dbPassword = environment.getProperty("spring.datasource.password");
        } catch (Exception ex) {
            throw new IllegalStateException("Missing required configuration: SPRING_DATASOURCE_PASSWORD", ex);
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("Missing required configuration: SPRING_DATASOURCE_PASSWORD");
        }
    }
}
