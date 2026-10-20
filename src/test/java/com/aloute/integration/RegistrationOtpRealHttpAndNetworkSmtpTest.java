package com.aloute.integration;

import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.request.ResendRegistrationOtpRequest;
import com.aloute.dto.request.VerifyRegistrationOtpRequest;
import com.aloute.entity.User;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.email.LocalSmtpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.registration-otp-secret=test-registration-otp-secret-key-32b",
        "aloute.auth.registration-otp-expiry-minutes=5",
        "aloute.auth.registration-otp-cooldown-seconds=60",
        "aloute.auth.registration-otp-max-attempts=5"
})
class RegistrationOtpRealHttpAndNetworkSmtpTest {

    private static LocalSmtpServer localSmtpServer;
    private static int smtpPort;
    private static SSLContext originalSslContext;

    @LocalServerPort
    private int httpPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegistrationOtpRepository otpRepository;

    @BeforeAll
    static void initSmtpServer() throws Exception {
        originalSslContext = SSLContext.getDefault();
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = RegistrationOtpRealHttpAndNetworkSmtpTest.class.getResourceAsStream("/test-smtp-keystore.p12")) {
            if (in == null) {
                throw new IllegalStateException("Test keystore /test-smtp-keystore.p12 not found on classpath");
            }
            trustStore.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        SSLContext.setDefault(sslContext);

        localSmtpServer = new LocalSmtpServer();
        smtpPort = localSmtpServer.getPort();
    }

    @AfterAll
    static void tearDownSmtpServer() throws Exception {
        if (originalSslContext != null) {
            SSLContext.setDefault(originalSslContext);
        }
        if (localSmtpServer != null) {
            localSmtpServer.close();
        }
    }

    @DynamicPropertySource
    static void configureSmtpServer(DynamicPropertyRegistry registry) {
        registry.add("aloute.mail.host", () -> "localhost");
        registry.add("aloute.mail.port", () -> smtpPort);
        registry.add("aloute.mail.username", () -> "testuser");
        registry.add("aloute.mail.password", () -> "testpass");
        registry.add("aloute.mail.from", () -> "noreply@aloute.edu.vn");
        registry.add("aloute.mail.starttls.enable", () -> "true");
        registry.add("aloute.mail.ssl.checkserveridentity", () -> "true");
    }

    private String getCsrfTokenAndCookie(HttpHeaders outHeaders) {
        ResponseEntity<String> loginPageResp = restTemplate.getForEntity("http://localhost:" + httpPort + "/login", String.class);
        List<String> setCookies = loginPageResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        String xsrfToken = null;
        if (setCookies != null) {
            for (String cookie : setCookies) {
                if (cookie.startsWith("XSRF-TOKEN=")) {
                    xsrfToken = cookie.substring("XSRF-TOKEN=".length()).split(";")[0];
                    outHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfToken);
                    outHeaders.set("X-XSRF-TOKEN", xsrfToken);
                    break;
                }
            }
        }
        return xsrfToken;
    }

    @Test
    @DisplayName("End-to-End: Actual HTTP socket listener and local network SMTP sink verify OTP transmission and activation")
    void realHttpListenerAndNetworkSmtpSinkE2E() throws Exception {
        String email = "realhttp_smtp_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@example.com";
        String username = "httpuser_" + (System.currentTimeMillis() % 100000) + "_" + ThreadLocalRandom.current().nextInt(100, 999);
        String password = "SecurePassword123!";

        // 1. Establish CSRF session over real HTTP listener
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        getCsrfTokenAndCookie(headers);

        // 2. Perform registration via real HTTP loopback socket
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        HttpEntity<RegisterRequest> regEntity = new HttpEntity<>(registerReq, headers);
        ResponseEntity<String> regResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/register",
                regEntity,
                String.class
        );
        assertThat(regResp.getStatusCode().value()).isEqualTo(201);

        // 3. Verify user created in unverified state
        User registeredUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(registeredUser.isVerified()).isFalse();
        assertThat(registeredUser.isVerificationRequired()).isTrue();

        // 4. Verify transmission over actual TCP loopback socket to LocalSmtpServer
        boolean received = localSmtpServer.awaitMessage(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(localSmtpServer.isAuthenticated()).isTrue();
        assertThat(localSmtpServer.getReceivedMessages()).isNotEmpty();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        assertThat(rawEmail).contains("To: " + email);
        assertThat(rawEmail).contains("[AloUTE] Ma xac thuc tai khoan");

        // 5. Extract 6-digit numeric OTP code in-memory without logging or printing
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(rawEmail);
        assertThat(matcher.find()).isTrue();
        String extractedOtp = matcher.group(1);
        assertThat(extractedOtp).matches("^\\d{6}$");

        // 6. Submit verification via real HTTP loopback socket
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, extractedOtp);
        HttpEntity<VerifyRegistrationOtpRequest> verifyEntity = new HttpEntity<>(verifyReq, headers);
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                verifyEntity,
                String.class
        );
        assertThat(verifyResp.getStatusCode().value()).isEqualTo(200);

        // 7. Verify atomic DB user activation and OTP record deletion
        User verifiedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verifiedUser.isVerified()).isTrue();
        assertThat(verifiedUser.isVerificationRequired()).isFalse();
        assertThat(otpRepository.findByUser(verifiedUser)).isEmpty();

        // 8. Re-verification attempt fails with 400 Bad Request
        ResponseEntity<String> reverifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                verifyEntity,
                String.class
        );
        assertThat(reverifyResp.getStatusCode().value()).isEqualTo(400);

        // 9. Resend for already verified user returns 200 silently with 0 additional mail
        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        ResendRegistrationOtpRequest resendReq = new ResendRegistrationOtpRequest(email);
        HttpEntity<ResendRegistrationOtpRequest> resendEntity = new HttpEntity<>(resendReq, headers);
        ResponseEntity<String> resendResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                resendEntity,
                String.class
        );
        assertThat(resendResp.getStatusCode().value()).isEqualTo(200);
        assertThat(localSmtpServer.getReceivedMessages()).hasSize(messagesBefore);
    }
}
