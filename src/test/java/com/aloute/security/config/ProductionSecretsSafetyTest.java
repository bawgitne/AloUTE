package com.aloute.security.config;

import com.aloute.security.jwt.JwtProperties;
import com.aloute.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsSafetyTest {

    private static final String VALID_TEST_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String SHORT_TEST_SECRET = "c2hvcnQ="; // "short" decodes to 5 bytes

    private ApplicationContextRunner createContextRunner() {
        return new ApplicationContextRunner(() -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.setEnvironment(new MockEnvironment());
            return context;
        }).withUserConfiguration(JwtProperties.class, JwtTokenProvider.class);
    }

    @Test
    @DisplayName("Unit: JwtProperties throws when ALOUTE_JWT_SECRET is missing")
    void jwtPropertiesThrowsWhenSecretMissing() {
        MockEnvironment env = new MockEnvironment();
        JwtProperties properties = new JwtProperties(env);

        assertThatThrownBy(properties::requireBase64Secret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: ALOUTE_JWT_SECRET");
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider @PostConstruct fails when ALOUTE_JWT_SECRET is missing")
    void jwtTokenProviderFailsStartupWhenSecretMissing() {
        ApplicationContextRunner runner = createContextRunner()
                .withPropertyValues("ALOUTE_JWT_EXPIRATION_SECONDS=3600");

        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: ALOUTE_JWT_SECRET");
        });
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider @PostConstruct fails when ALOUTE_JWT_SECRET is shorter than 32 bytes")
    void jwtTokenProviderFailsStartupWhenSecretTooShort() {
        ApplicationContextRunner runner = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=" + SHORT_TEST_SECRET,
                        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
                );

        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("ALOUTE_JWT_SECRET must decode to at least 32 bytes");
        });
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider starts cleanly with valid Base64 secret >= 32 bytes")
    void jwtTokenProviderStartsSuccessfullyWithValidSecret() {
        ApplicationContextRunner runner = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=" + VALID_TEST_SECRET,
                        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
                );

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtTokenProvider.class);
        });
    }

    @Test
    @DisplayName("Config: application-prod.yml defines placeholders without committed fallback values")
    void prodProfileRawConfigurationContainsNoDefaultSecrets() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        assertThat(sources).isNotEmpty();

        PropertySource<?> source = sources.get(0);
        Object dbPassword = source.getProperty("spring.datasource.password");
        Object jwtSecret = source.getProperty("ALOUTE_JWT_SECRET");

        assertThat(dbPassword).isEqualTo("${SPRING_DATASOURCE_PASSWORD}");
        assertThat(jwtSecret).isEqualTo("${ALOUTE_JWT_SECRET}");
    }

    @Test
    @DisplayName("Prod profile: Placeholder resolution fails when SPRING_DATASOURCE_PASSWORD is not provided")
    void prodProfileFailsPlaceholderResolutionWhenDatasourcePasswordMissing() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        MockEnvironment env = new MockEnvironment();
        sources.forEach(env.getPropertySources()::addLast);

        assertThatThrownBy(() -> env.resolveRequiredPlaceholders(env.getProperty("spring.datasource.password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD");

        // When password is provided, resolution succeeds
        MockEnvironment envWithDb = new MockEnvironment();
        envWithDb.getPropertySources().addFirst(new MapPropertySource("explicitDbProps", Map.of("SPRING_DATASOURCE_PASSWORD", "test_db_password")));
        sources.forEach(envWithDb.getPropertySources()::addLast);
        assertThat(envWithDb.resolveRequiredPlaceholders(envWithDb.getProperty("spring.datasource.password"))).isEqualTo("test_db_password");
    }

    @Test
    @DisplayName("Prod profile: Placeholder resolution fails when ALOUTE_JWT_SECRET is not provided")
    void prodProfileFailsPlaceholderResolutionWhenJwtSecretMissing() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        MockEnvironment env = new MockEnvironment();
        sources.forEach(env.getPropertySources()::addLast);

        assertThatThrownBy(() -> env.resolveRequiredPlaceholders(env.getProperty("ALOUTE_JWT_SECRET")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ALOUTE_JWT_SECRET");

        // When secret is provided, resolution succeeds
        MockEnvironment envWithSecret = new MockEnvironment();
        envWithSecret.getPropertySources().addFirst(new MapPropertySource("explicitJwtProps", Map.of("ALOUTE_JWT_SECRET", VALID_TEST_SECRET)));
        sources.forEach(envWithSecret.getPropertySources()::addLast);
        assertThat(envWithSecret.resolveRequiredPlaceholders(envWithSecret.getProperty("ALOUTE_JWT_SECRET"))).isEqualTo(VALID_TEST_SECRET);
    }

    @Test
    @DisplayName("Config: docker-compose.yml requires secrets without committed default fallbacks")
    void dockerComposeRequiresSecretsWithoutDefaultFallbacks() throws IOException {
        Path composePath = Paths.get("docker-compose.yml");
        assertThat(composePath).exists();

        List<String> lines = Files.readAllLines(composePath);

        List<String> mysqlPasswordLines = lines.stream()
                .filter(l -> l.contains("MYSQL_PASSWORD") && !l.contains("MYSQL_ROOT_PASSWORD"))
                .toList();
        List<String> mysqlRootPasswordLines = lines.stream()
                .filter(l -> l.contains("MYSQL_ROOT_PASSWORD"))
                .toList();
        List<String> jwtSecretLines = lines.stream()
                .filter(l -> l.contains("ALOUTE_JWT_SECRET") && !l.contains("ALOUTE_JWT_EXPIRATION_SECONDS"))
                .toList();

        // MYSQL_PASSWORD is used in both db and app services
        assertThat(mysqlPasswordLines).hasSize(2);
        assertThat(mysqlPasswordLines).allSatisfy(line -> {
            assertThat(line).contains("${MYSQL_PASSWORD}");
            assertThat(line).doesNotContain(":-");
        });

        // MYSQL_ROOT_PASSWORD is used in db service environment and healthcheck ping
        assertThat(mysqlRootPasswordLines).hasSize(2);
        assertThat(mysqlRootPasswordLines).allSatisfy(line -> {
            assertThat(line).contains("${MYSQL_ROOT_PASSWORD}");
            assertThat(line).doesNotContain(":-");
        });

        // ALOUTE_JWT_SECRET is used in app service environment
        assertThat(jwtSecretLines).hasSize(1);
        assertThat(jwtSecretLines).allSatisfy(line -> {
            assertThat(line).contains("${ALOUTE_JWT_SECRET}");
            assertThat(line).doesNotContain(":-");
        });
    }
}
