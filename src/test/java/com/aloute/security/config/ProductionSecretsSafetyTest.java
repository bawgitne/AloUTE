package com.aloute.security.config;

import com.aloute.security.jwt.JwtProperties;
import com.aloute.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Assumptions;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    @DisplayName("Unit: JwtProperties throws when ALOUTE_JWT_SECRET is missing, empty, or blank")
    void jwtPropertiesThrowsWhenSecretMissingOrEmpty() {
        // Missing (null)
        MockEnvironment envNull = new MockEnvironment();
        JwtProperties propsNull = new JwtProperties(envNull);
        assertThatThrownBy(propsNull::requireBase64Secret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: ALOUTE_JWT_SECRET");

        // Empty string
        MockEnvironment envEmpty = new MockEnvironment();
        envEmpty.setProperty("ALOUTE_JWT_SECRET", "");
        JwtProperties propsEmpty = new JwtProperties(envEmpty);
        assertThatThrownBy(propsEmpty::requireBase64Secret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: ALOUTE_JWT_SECRET");

        // Blank whitespace string
        MockEnvironment envBlank = new MockEnvironment();
        envBlank.setProperty("ALOUTE_JWT_SECRET", "   ");
        JwtProperties propsBlank = new JwtProperties(envBlank);
        assertThatThrownBy(propsBlank::requireBase64Secret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: ALOUTE_JWT_SECRET");
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider @PostConstruct fails startup when ALOUTE_JWT_SECRET is missing or empty")
    void jwtTokenProviderFailsStartupWhenSecretMissingOrEmpty() {
        // Missing
        ApplicationContextRunner runnerMissing = createContextRunner()
                .withPropertyValues("ALOUTE_JWT_EXPIRATION_SECONDS=3600");
        runnerMissing.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: ALOUTE_JWT_SECRET");
        });

        // Empty string
        ApplicationContextRunner runnerEmpty = createContextRunner()
                .withPropertyValues("ALOUTE_JWT_SECRET=", "ALOUTE_JWT_EXPIRATION_SECONDS=3600");
        runnerEmpty.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: ALOUTE_JWT_SECRET");
        });

        // Blank string
        ApplicationContextRunner runnerBlank = createContextRunner()
                .withPropertyValues("ALOUTE_JWT_SECRET=   ", "ALOUTE_JWT_EXPIRATION_SECONDS=3600");
        runnerBlank.run(context -> {
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
    @DisplayName("Config: docker-compose.yml requires secrets with fail-fast :? syntax without default fallbacks")
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
            assertThat(line).contains("${MYSQL_PASSWORD:?");
            assertThat(line).doesNotContain(":-");
        });

        // MYSQL_ROOT_PASSWORD is used in db service environment and healthcheck ping
        assertThat(mysqlRootPasswordLines).hasSize(2);
        assertThat(mysqlRootPasswordLines).allSatisfy(line -> {
            assertThat(line).contains("${MYSQL_ROOT_PASSWORD:?");
            assertThat(line).doesNotContain(":-");
        });

        // ALOUTE_JWT_SECRET is used in app service environment
        assertThat(jwtSecretLines).hasSize(1);
        assertThat(jwtSecretLines).allSatisfy(line -> {
            assertThat(line).contains("${ALOUTE_JWT_SECRET:?");
            assertThat(line).doesNotContain(":-");
        });
    }

    @Test
    @DisplayName("Config: deploy/compose/compose.prod.yml requires secrets with fail-fast :? syntax without default fallbacks")
    void deployComposeProdRequiresSecretsWithoutDefaultFallbacks() throws IOException {
        Path composePath = Paths.get("deploy", "compose", "compose.prod.yml");
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

        // MYSQL_PASSWORD is used in db service and app service (SPRING_DATASOURCE_PASSWORD)
        assertThat(mysqlPasswordLines).hasSize(2);
        assertThat(mysqlPasswordLines).allSatisfy(line -> {
            assertThat(line).contains("${MYSQL_PASSWORD:?");
            assertThat(line).doesNotContain(":-");
        });

        // MYSQL_ROOT_PASSWORD is used in db service environment and healthcheck ping
        assertThat(mysqlRootPasswordLines).hasSize(2);
        assertThat(mysqlRootPasswordLines).allSatisfy(line -> {
            assertThat(line).contains("${MYSQL_ROOT_PASSWORD:?");
            assertThat(line).doesNotContain(":-");
        });

        // ALOUTE_JWT_SECRET is used in app service environment
        assertThat(jwtSecretLines).hasSize(1);
        assertThat(jwtSecretLines).allSatisfy(line -> {
            assertThat(line).contains("${ALOUTE_JWT_SECRET:?");
            assertThat(line).doesNotContain(":-");
        });
    }

    private ApplicationContextRunner createProdValidatorContextRunner() {
        return new ApplicationContextRunner(() -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");
            context.setEnvironment(env);
            return context;
        }).withUserConfiguration(ProductionSecretsValidator.class);
    }

    @Test
    @DisplayName("Unit: ProductionSecretsValidator fails startup when spring.datasource.password is missing, empty, or blank")
    void productionSecretsValidatorFailsWhenDatasourcePasswordMissingOrBlank() {
        // Missing
        ApplicationContextRunner runnerMissing = createProdValidatorContextRunner();
        runnerMissing.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: SPRING_DATASOURCE_PASSWORD");
        });

        // Empty string
        ApplicationContextRunner runnerEmpty = createProdValidatorContextRunner()
                .withPropertyValues("spring.datasource.password=");
        runnerEmpty.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: SPRING_DATASOURCE_PASSWORD");
        });

        // Blank whitespace
        ApplicationContextRunner runnerBlank = createProdValidatorContextRunner()
                .withPropertyValues("spring.datasource.password=   ");
        runnerBlank.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: SPRING_DATASOURCE_PASSWORD");
        });

        // Valid password
        ApplicationContextRunner runnerValid = createProdValidatorContextRunner()
                .withPropertyValues("spring.datasource.password=secure_prod_password_123");
        runnerValid.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionSecretsValidator.class);
        });
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider @PostConstruct fails startup when ALOUTE_JWT_SECRET is malformed Base64")
    void jwtTokenProviderFailsStartupWhenSecretMalformedBase64() {
        ApplicationContextRunner runner = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=not_valid_base64!@#",
                        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
                );

        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(io.jsonwebtoken.io.DecodingException.class);
        });
    }

    @Test
    @DisplayName("Unit: JwtTokenProvider @PostConstruct fails startup when expiration is invalid")
    void jwtTokenProviderFailsStartupWhenExpirationInvalid() {
        // Non-numeric
        ApplicationContextRunner runnerNonNumeric = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=" + VALID_TEST_SECRET,
                        "ALOUTE_JWT_EXPIRATION_SECONDS=abc"
                );
        runnerNonNumeric.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(NumberFormatException.class);
        });

        // Zero
        ApplicationContextRunner runnerZero = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=" + VALID_TEST_SECRET,
                        "ALOUTE_JWT_EXPIRATION_SECONDS=0"
                );
        runnerZero.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("ALOUTE_JWT_EXPIRATION_SECONDS must be greater than 0");
        });

        // Negative
        ApplicationContextRunner runnerNegative = createContextRunner()
                .withPropertyValues(
                        "ALOUTE_JWT_SECRET=" + VALID_TEST_SECRET,
                        "ALOUTE_JWT_EXPIRATION_SECONDS=-60"
                );
        runnerNegative.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("ALOUTE_JWT_EXPIRATION_SECONDS must be greater than 0");
        });

        // Missing expiration
        ApplicationContextRunner runnerMissing = createContextRunner()
                .withPropertyValues("ALOUTE_JWT_SECRET=" + VALID_TEST_SECRET);
        runnerMissing.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Missing required configuration: ALOUTE_JWT_EXPIRATION_SECONDS");
        });
    }

    @Test
    @DisplayName("Compose CLI: Validates fail-fast config evaluation for missing and empty secrets via docker compose config")
    void dockerComposeCliValidatesMissingAndEmptySecrets() throws Exception {
        Assumptions.assumeTrue(isDockerComposeAvailable(),
                "Docker Compose CLI is not available in the current environment; skipping CLI validation");

        Path emptyEnv = Files.createTempFile("empty-compose", ".env");
        try {
            Map<String, String> validDummySecrets = Map.of(
                    "MYSQL_PASSWORD", "safe_dummy_password",
                    "MYSQL_ROOT_PASSWORD", "safe_dummy_root_password",
                    "ALOUTE_JWT_SECRET", VALID_TEST_SECRET
            );

            // 1. Success case: all dummy secrets provided
            ProcessResult successResult = runDockerComposeConfig(emptyEnv, validDummySecrets);
            assertThat(successResult.exitCode())
                    .as("docker compose config should succeed when all required secrets are provided: %s", successResult.output())
                    .isEqualTo(0);

            // 2. Missing and empty checks for each required secret
            for (String secretVar : List.of("MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "ALOUTE_JWT_SECRET")) {
                // Missing case
                Map<String, String> missingEnv = new HashMap<>(validDummySecrets);
                missingEnv.remove(secretVar);
                ProcessResult missingResult = runDockerComposeConfig(emptyEnv, missingEnv);
                assertThat(missingResult.exitCode())
                        .as("docker compose config should fail when %s is missing", secretVar)
                        .isNotEqualTo(0);
                assertThat(missingResult.output())
                        .as("docker compose output should mention %s when missing", secretVar)
                        .contains(secretVar);

                // Empty case
                Map<String, String> emptyValEnv = new HashMap<>(validDummySecrets);
                emptyValEnv.put(secretVar, "");
                ProcessResult emptyValResult = runDockerComposeConfig(emptyEnv, emptyValEnv);
                assertThat(emptyValResult.exitCode())
                        .as("docker compose config should fail when %s is empty", secretVar)
                        .isNotEqualTo(0);
                assertThat(emptyValResult.output())
                        .as("docker compose output should mention %s when empty", secretVar)
                        .contains(secretVar);
            }
        } finally {
            Files.deleteIfExists(emptyEnv);
        }
    }

    private record ProcessResult(int exitCode, String output) {}

    private static boolean isDockerComposeAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "compose", "version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ProcessResult runDockerComposeConfig(Path emptyEnvFile, Map<String, String> secretEnv) throws Exception {
        Path composeFile = Paths.get("docker-compose.yml").toAbsolutePath();
        Path tempOutputFile = Files.createTempFile("compose-config-output", ".log");
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose",
                    "--env-file", emptyEnvFile.toAbsolutePath().toString(),
                    "-f", composeFile.toString(),
                    "config", "--quiet"
            );
            pb.directory(composeFile.getParent().toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(tempOutputFile.toFile());

            Map<String, String> env = pb.environment();
            // Sanitize child process environment to prevent host or implicit env from masking missing variables
            env.remove("MYSQL_PASSWORD");
            env.remove("MYSQL_ROOT_PASSWORD");
            env.remove("ALOUTE_JWT_SECRET");
            env.remove("MYSQL_USER");
            env.remove("MYSQL_DATABASE");
            env.remove("MYSQL_PORT");
            env.remove("ALOUTE_JWT_EXPIRATION_SECONDS");

            // Apply only test-specified dummy secrets
            env.putAll(secretEnv);

            process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("docker compose config timed out");
            }
            String output = Files.readString(tempOutputFile, StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), output);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(tempOutputFile);
        }
    }
}
