package com.aloute.service.email;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEmailServiceTest {

    private SSLContext createTestSslContext(String keystorePath) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream(keystorePath)) {
            if (in == null) {
                throw new IllegalStateException("Keystore resource not found: " + keystorePath);
            }
            trustStore.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    @Test
    void unconfiguredSmtpSimulatesDeliveryWithoutException() {
        DefaultEmailService service = new DefaultEmailService();
        ReflectionTestUtils.setField(service, "smtpHost", "");
        ReflectionTestUtils.setField(service, "starttlsEnable", true);
        ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

        assertThatCode(() -> service.validateConfiguration()).doesNotThrowAnyException();
        assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset-password#token=abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void configuredHostMissingCredentialsRejectsConfiguration() {
        DefaultEmailService service = new DefaultEmailService();
        ReflectionTestUtils.setField(service, "smtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "smtpUsername", "");
        ReflectionTestUtils.setField(service, "smtpPassword", "");
        ReflectionTestUtils.setField(service, "starttlsEnable", true);
        ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and password must be configured");

        assertThatThrownBy(() -> service.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset-password#token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configuredHostMissingPasswordRejectsConfiguration() {
        DefaultEmailService service = new DefaultEmailService();
        ReflectionTestUtils.setField(service, "smtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "smtpUsername", "validUser");
        ReflectionTestUtils.setField(service, "smtpPassword", "");
        ReflectionTestUtils.setField(service, "starttlsEnable", true);
        ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and password must be configured");

        assertThatThrownBy(() -> service.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset-password#token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsafeConfigurationRejectsStarttlsDisabled() {
        DefaultEmailService service = new DefaultEmailService();
        ReflectionTestUtils.setField(service, "smtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "smtpUsername", "user");
        ReflectionTestUtils.setField(service, "smtpPassword", "pass");
        ReflectionTestUtils.setField(service, "starttlsEnable", false);
        ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STARTTLS and SSL server identity verification must be enabled");

        assertThatThrownBy(() -> service.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset-password#token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsafeConfigurationRejectsSslCheckServerIdentityDisabled() {
        DefaultEmailService service = new DefaultEmailService();
        ReflectionTestUtils.setField(service, "smtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "smtpUsername", "user");
        ReflectionTestUtils.setField(service, "smtpPassword", "pass");
        ReflectionTestUtils.setField(service, "starttlsEnable", true);
        ReflectionTestUtils.setField(service, "sslCheckServerIdentity", false);

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STARTTLS and SSL server identity verification must be enabled");

        assertThatThrownBy(() -> service.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset-password#token=abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void realLoopbackSmtpServerWithStarttlsTransmitsPasswordResetEmail() throws Exception {
        SSLContext origSslContext = SSLContext.getDefault();
        SSLContext testContext = createTestSslContext("/test-smtp-keystore.p12");

        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            SSLContext.setDefault(testContext);

            DefaultEmailService service = new DefaultEmailService();
            ReflectionTestUtils.setField(service, "smtpHost", "localhost");
            ReflectionTestUtils.setField(service, "smtpPort", smtpServer.getPort());
            ReflectionTestUtils.setField(service, "smtpUsername", "testuser");
            ReflectionTestUtils.setField(service, "smtpPassword", "testpass");
            ReflectionTestUtils.setField(service, "smtpFrom", "noreply@aloute.edu.vn");
            ReflectionTestUtils.setField(service, "starttlsEnable", true);
            ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

            service.validateConfiguration();

            String token = "TEST_TOKEN_SMTP_" + System.currentTimeMillis();
            String resetUrl = "http://localhost:8080/reset-password#token=" + token;

            service.sendPasswordResetEmail("alice@example.com", resetUrl);

            boolean received = smtpServer.awaitMessage(10, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(smtpServer.isAuthenticated()).isTrue();
            assertThat(smtpServer.getReceivedMessages()).hasSize(1);
            String rawEmail = smtpServer.getReceivedMessages().get(0);
            assertThat(rawEmail).contains("To: alice@example.com");
            assertThat(rawEmail).contains(resetUrl);
        } finally {
            SSLContext.setDefault(origSslContext);
        }
    }

    @Test
    void untrustedCertificateChainFailsDelivery() throws Exception {
        // Default standard truststore without test keystore -> untrusted self-signed certificate rejected
        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            DefaultEmailService service = new DefaultEmailService();
            ReflectionTestUtils.setField(service, "smtpHost", "localhost");
            ReflectionTestUtils.setField(service, "smtpPort", smtpServer.getPort());
            ReflectionTestUtils.setField(service, "smtpUsername", "testuser");
            ReflectionTestUtils.setField(service, "smtpPassword", "testpass");
            ReflectionTestUtils.setField(service, "smtpFrom", "noreply@aloute.edu.vn");
            ReflectionTestUtils.setField(service, "starttlsEnable", true);
            ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

            service.validateConfiguration();

            String resetUrl = "http://localhost:8080/reset-password#token=untrusted_test";

            service.sendPasswordResetEmail("alice@example.com", resetUrl);

            boolean received = smtpServer.awaitMessage(1, TimeUnit.SECONDS);
            assertThat(received).isFalse();
            assertThat(smtpServer.isAuthenticated()).isFalse();
            assertThat(smtpServer.getReceivedMessages()).isEmpty();
        }
    }

    @Test
    void wrongHostnameFailsDelivery() throws Exception {
        SSLContext origSslContext = SSLContext.getDefault();
        // Server certificate is trusted for wronghost.example.com, but client connects to localhost
        SSLContext wrongHostContext = createTestSslContext("/test-smtp-wronghost-keystore.p12");

        try (LocalSmtpServer smtpServer = new LocalSmtpServer("/test-smtp-wronghost-keystore.p12")) {
            SSLContext.setDefault(wrongHostContext);

            DefaultEmailService service = new DefaultEmailService();
            ReflectionTestUtils.setField(service, "smtpHost", "localhost");
            ReflectionTestUtils.setField(service, "smtpPort", smtpServer.getPort());
            ReflectionTestUtils.setField(service, "smtpUsername", "testuser");
            ReflectionTestUtils.setField(service, "smtpPassword", "testpass");
            ReflectionTestUtils.setField(service, "smtpFrom", "noreply@aloute.edu.vn");
            ReflectionTestUtils.setField(service, "starttlsEnable", true);
            ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

            service.validateConfiguration();

            String resetUrl = "http://localhost:8080/reset-password#token=wronghost_test";

            service.sendPasswordResetEmail("alice@example.com", resetUrl);

            boolean received = smtpServer.awaitMessage(1, TimeUnit.SECONDS);
            assertThat(received).isFalse();
            assertThat(smtpServer.isAuthenticated()).isFalse();
            assertThat(smtpServer.getReceivedMessages()).isEmpty();
        } finally {
            SSLContext.setDefault(origSslContext);
        }
    }

    @Test
    void invalidCredentialsFailsAuthenticationAndDelivery() throws Exception {
        SSLContext origSslContext = SSLContext.getDefault();
        SSLContext testContext = createTestSslContext("/test-smtp-keystore.p12");

        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            SSLContext.setDefault(testContext);

            DefaultEmailService service = new DefaultEmailService();
            ReflectionTestUtils.setField(service, "smtpHost", "localhost");
            ReflectionTestUtils.setField(service, "smtpPort", smtpServer.getPort());
            ReflectionTestUtils.setField(service, "smtpUsername", "testuser");
            ReflectionTestUtils.setField(service, "smtpPassword", "wrongpassword");
            ReflectionTestUtils.setField(service, "smtpFrom", "noreply@aloute.edu.vn");
            ReflectionTestUtils.setField(service, "starttlsEnable", true);
            ReflectionTestUtils.setField(service, "sslCheckServerIdentity", true);

            service.validateConfiguration();

            String resetUrl = "http://localhost:8080/reset-password#token=invalid_auth_test";

            service.sendPasswordResetEmail("alice@example.com", resetUrl);

            boolean received = smtpServer.awaitMessage(1, TimeUnit.SECONDS);
            assertThat(received).isFalse();
            assertThat(smtpServer.isAuthenticated()).isFalse();
            assertThat(smtpServer.getReceivedMessages()).isEmpty();
        } finally {
            SSLContext.setDefault(origSslContext);
        }
    }
}
