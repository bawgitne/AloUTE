package com.aloute.integration;

import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.request.ResendRegistrationOtpRequest;
import com.aloute.dto.request.VerifyRegistrationOtpRequest;
import com.aloute.entity.RegistrationOtp;
import com.aloute.entity.User;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.registration-otp-secret=dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=",
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

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private HttpHeaders createCsrfHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginPageResp = restTemplate.getForEntity("http://localhost:" + httpPort + "/login", String.class);
        List<String> setCookies = loginPageResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String cookie : setCookies) {
                if (cookie.startsWith("XSRF-TOKEN=")) {
                    String xsrfToken = cookie.substring("XSRF-TOKEN=".length()).split(";")[0];
                    headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfToken);
                    headers.set("X-XSRF-TOKEN", xsrfToken);
                    break;
                }
            }
        }
        return headers;
    }

    private String generateEmail(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@example.com";
    }

    private String generateUsername(String prefix) {
        return prefix + "_" + (System.currentTimeMillis() % 1000000) + "_" + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private String extractOtpFromEmail(String rawEmail) {
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(rawEmail);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("Cases 1, 2, 3, 4, 5, 6: Full valid lifecycle over real HTTP listener and network SMTP sink")
    void cases1To6_fullLifecycleRealHttpAndNetworkSmtp() throws Exception {
        String email = generateEmail("lifecycle_smtp");
        String username = generateUsername("life");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();

        // Case 1: Register with valid email over real HTTP listener
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        HttpEntity<RegisterRequest> regEntity = new HttpEntity<>(registerReq, headers);
        ResponseEntity<String> regResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/register",
                regEntity,
                String.class
        );
        assertThat(regResp.getStatusCode().value()).isEqualTo(201);

        // Case 2: Account remains unverified in DB
        User registeredUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(registeredUser.isVerified()).isFalse();
        assertThat(registeredUser.isVerificationRequired()).isTrue();

        // Case 3: Login is blocked before verification
        LoginRequest loginReq = new LoginRequest(email, password);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginReq, headers);
        ResponseEntity<String> loginBlockedResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/login",
                loginEntity,
                String.class
        );
        assertThat(loginBlockedResp.getStatusCode().value()).isEqualTo(403);
        assertThat(loginBlockedResp.getBody()).contains("AUTH_ACCOUNT_NOT_VERIFIED");

        // Case 4: Email OTP dispatched over network loopback socket to LocalSmtpServer
        boolean received = localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(localSmtpServer.isAuthenticated()).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        assertThat(rawEmail).contains("To: " + email);
        String extractedOtp = extractOtpFromEmail(rawEmail);
        assertThat(extractedOtp).matches("^\\d{6}$");

        // Case 5: Correct OTP activates account over real HTTP listener
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, extractedOtp);
        HttpEntity<VerifyRegistrationOtpRequest> verifyEntity = new HttpEntity<>(verifyReq, headers);
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                verifyEntity,
                String.class
        );
        assertThat(verifyResp.getStatusCode().value()).isEqualTo(200);

        User verifiedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verifiedUser.isVerified()).isTrue();
        assertThat(verifiedUser.isVerificationRequired()).isFalse();
        assertThat(otpRepository.findByUser(verifiedUser)).isEmpty();

        // Case 6: Login succeeds after verification and issues valid session cookie
        ResponseEntity<String> loginSuccessResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/login",
                loginEntity,
                String.class
        );
        assertThat(loginSuccessResp.getStatusCode().value()).isEqualTo(200);
        List<String> setCookies = loginSuccessResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies).anyMatch(c -> c.contains("aloute_access_token="));
    }

    @Test
    @DisplayName("Case 7: Wrong OTP rejected and increments attempt count over real HTTP")
    void case7_wrongOtpRejectedAndIncrementsAttempts() throws Exception {
        String email = generateEmail("wrong_code");
        String username = generateUsername("wrong");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpBefore = otpRepository.findByUser(user).orElseThrow();
        assertThat(otpBefore.getAttempts()).isEqualTo(0);

        // Submit wrong OTP over real HTTP listener
        VerifyRegistrationOtpRequest wrongReq = new VerifyRegistrationOtpRequest(email, "999999");
        ResponseEntity<String> wrongResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(wrongReq, headers),
                String.class
        );
        assertThat(wrongResp.getStatusCode().value()).isEqualTo(400);
        assertThat(wrongResp.getBody()).contains("INVALID_OTP");

        RegistrationOtp otpAfter = otpRepository.findByUser(user).orElseThrow();
        assertThat(otpAfter.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Case 8: Expired OTP rejected over real HTTP")
    void case8_expiredOtpRejected() throws Exception {
        String email = generateEmail("exp_code");
        String username = generateUsername("exp");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String correctOtp = extractOtpFromEmail(rawEmail);

        // Fast-forward expiration in DB
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setExpiresAt(Instant.now().minusSeconds(10));
        otpRepository.saveAndFlush(otpRecord);

        // Submit over real HTTP
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, correctOtp);
        ResponseEntity<String> expiredResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(verifyReq, headers),
                String.class
        );
        assertThat(expiredResp.getStatusCode().value()).isEqualTo(400);
        assertThat(expiredResp.getBody()).contains("INVALID_OTP");
    }

    @Test
    @DisplayName("Case 9: Reused OTP rejected over real HTTP")
    void case9_reusedOtpRejected() throws Exception {
        String email = generateEmail("reused_code");
        String username = generateUsername("reused");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String correctOtp = extractOtpFromEmail(rawEmail);

        // First verification succeeds
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, correctOtp);
        ResponseEntity<String> firstResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(verifyReq, headers),
                String.class
        );
        assertThat(firstResp.getStatusCode().value()).isEqualTo(200);

        // Reusing same OTP over real HTTP must fail with 400
        ResponseEntity<String> reusedResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(verifyReq, headers),
                String.class
        );
        assertThat(reusedResp.getStatusCode().value()).isEqualTo(400);
        assertThat(reusedResp.getBody()).contains("INVALID_OTP");
    }

    @Test
    @DisplayName("Case 10: Max 5 failed attempts enforced over real HTTP")
    void case10_maxFiveFailedAttemptsEnforced() throws Exception {
        String email = generateEmail("max_attempts");
        String username = generateUsername("maxatt");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String correctOtp = extractOtpFromEmail(rawEmail);

        // 5 consecutive failed attempts
        VerifyRegistrationOtpRequest wrongReq = new VerifyRegistrationOtpRequest(email, "000000");
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                    new HttpEntity<>(wrongReq, headers),
                    String.class
            );
            assertThat(resp.getStatusCode().value()).isEqualTo(400);
            assertThat(resp.getBody()).contains("INVALID_OTP");
        }

        // 6th attempt with CORRECT OTP is still rejected
        VerifyRegistrationOtpRequest correctReq = new VerifyRegistrationOtpRequest(email, correctOtp);
        ResponseEntity<String> lockedResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(correctReq, headers),
                String.class
        );
        assertThat(lockedResp.getStatusCode().value()).isEqualTo(400);
        assertThat(lockedResp.getBody()).contains("INVALID_OTP");

        RegistrationOtp exhaustedOtp = otpRepository.findByUser(userRepository.findByEmailIgnoreCase(email).orElseThrow()).orElseThrow();
        assertThat(exhaustedOtp.getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Case 11: Resend cooldown suppresses dispatch silently with 200 over real HTTP")
    void case11_resendCooldownSuppressesDispatchSilentlyWith200() throws Exception {
        String email = generateEmail("cooldown_http");
        String username = generateUsername("coolh");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        int messagesAfterRegister = localSmtpServer.getReceivedMessages().size();

        // Immediate resend within 60s cooldown returns 200 generic message without dispatching mail
        ResendRegistrationOtpRequest resendReq = new ResendRegistrationOtpRequest(email);
        ResponseEntity<String> resendResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                new HttpEntity<>(resendReq, headers),
                String.class
        );
        assertThat(resendResp.getStatusCode().value()).isEqualTo(200);

        // Verify zero additional emails arrived at SMTP server
        assertThat(localSmtpServer.getReceivedMessages()).hasSize(messagesAfterRegister);
    }

    @Test
    @DisplayName("Case 12: Previous OTP invalidated on resend over real HTTP and SMTP")
    void case12_resendInvalidatesPriorOtp() throws Exception {
        String email = generateEmail("inval_prior");
        String username = generateUsername("invalp");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String firstEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String firstOtp = extractOtpFromEmail(firstEmail);

        // Fast-forward cooldown in DB
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        // Resend OTP over real HTTP
        ResendRegistrationOtpRequest resendReq = new ResendRegistrationOtpRequest(email);
        ResponseEntity<String> resendResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                new HttpEntity<>(resendReq, headers),
                String.class
        );
        assertThat(resendResp.getStatusCode().value()).isEqualTo(200);

        // Await second email over SMTP
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 2, 10, TimeUnit.SECONDS)).isTrue();
        String secondEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String secondOtp = extractOtpFromEmail(secondEmail);

        // First OTP must be rejected
        VerifyRegistrationOtpRequest firstVerifyReq = new VerifyRegistrationOtpRequest(email, firstOtp);
        ResponseEntity<String> firstVerifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(firstVerifyReq, headers),
                String.class
        );
        assertThat(firstVerifyResp.getStatusCode().value()).isEqualTo(400);
        assertThat(firstVerifyResp.getBody()).contains("INVALID_OTP");

        // Second OTP must succeed
        VerifyRegistrationOtpRequest secondVerifyReq = new VerifyRegistrationOtpRequest(email, secondOtp);
        ResponseEntity<String> secondVerifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(secondVerifyReq, headers),
                String.class
        );
        assertThat(secondVerifyResp.getStatusCode().value()).isEqualTo(200);

        User finalUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(finalUser.isVerified()).isTrue();
    }

    @Test
    @DisplayName("Case 13a: Concurrent verification atomic locking over real HTTP")
    void case13a_concurrentVerificationAtomicLocking() throws Exception {
        String email = generateEmail("conc_http_verify");
        String username = generateUsername("chver");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String validOtp = extractOtpFromEmail(rawEmail);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    VerifyRegistrationOtpRequest req = new VerifyRegistrationOtpRequest(email, validOtp);
                    ResponseEntity<String> resp = restTemplate.postForEntity(
                            "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                            new HttpEntity<>(req, headers),
                            String.class
                    );
                    if (resp.getStatusCode().value() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one concurrent verification succeeds over real HTTP listener
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threads - 1);

        User activatedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(activatedUser.isVerified()).isTrue();
    }

    @Test
    @DisplayName("Case 13b: Concurrent correct vs wrong OTP over real HTTP")
    void case13b_concurrentCorrectVsWrongOtp() throws Exception {
        String email = generateEmail("conc_corr_wrong");
        String username = generateUsername("ccw");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String correctOtp = extractOtpFromEmail(rawEmail);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger correctStatus = new AtomicInteger(0);
        AtomicInteger wrongStatus = new AtomicInteger(0);

        // Thread 1: correct OTP
        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                        new HttpEntity<>(new VerifyRegistrationOtpRequest(email, correctOtp), headers),
                        String.class
                );
                correctStatus.set(resp.getStatusCode().value());
            } catch (Exception ignored) {}
        });

        // Thread 2: wrong OTP
        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                        new HttpEntity<>(new VerifyRegistrationOtpRequest(email, "999999"), headers),
                        String.class
                );
                wrongStatus.set(resp.getStatusCode().value());
            } catch (Exception ignored) {}
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Wrong attempt always receives 400
        assertThat(wrongStatus.get()).isEqualTo(400);
        // Correct attempt received 200 (if verified before max attempts exceeded)
        assertThat(correctStatus.get()).isIn(200, 400);

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        if (correctStatus.get() == 200) {
            assertThat(user.isVerified()).isTrue();
        }
    }

    @Test
    @DisplayName("Case 13c: Concurrent verify vs resend over real HTTP")
    void case13c_concurrentVerifyVsResend() throws Exception {
        String email = generateEmail("conc_ver_resend");
        String username = generateUsername("cvr");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        int messagesBefore = localSmtpServer.getReceivedMessages().size();
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("http://localhost:" + httpPort + "/api/auth/register", new HttpEntity<>(registerReq, headers), String.class);
        assertThat(localSmtpServer.awaitMessageCount(messagesBefore + 1, 10, TimeUnit.SECONDS)).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String correctOtp = extractOtpFromEmail(rawEmail);

        // Fast-forward cooldown
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        RegistrationOtp otpRecord = otpRepository.findByUser(user).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger verifyStatus = new AtomicInteger(0);
        AtomicInteger resendStatus = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                        new HttpEntity<>(new VerifyRegistrationOtpRequest(email, correctOtp), headers),
                        String.class
                );
                verifyStatus.set(resp.getStatusCode().value());
            } catch (Exception ignored) {}
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                        new HttpEntity<>(new ResendRegistrationOtpRequest(email), headers),
                        String.class
                );
                resendStatus.set(resp.getStatusCode().value());
            } catch (Exception ignored) {}
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Resend always returns 200
        assertThat(resendStatus.get()).isEqualTo(200);
        // Verify returns either 200 (if executed before resend) or 400 (if executed after resend invalidated prior OTP)
        assertThat(verifyStatus.get()).isIn(200, 400);
    }

    @Test
    @DisplayName("Case 14: Existing legacy users remain compatible over real HTTP")
    void case14_legacyUserCompatibility() throws Exception {
        String email = generateEmail("legacy_http");
        String username = generateUsername("legh");
        String password = "LegacyPassword123!";
        HttpHeaders headers = createCsrfHeaders();

        // Create legacy user: verificationRequired = false, isVerified = false
        User legacyUser = new User();
        legacyUser.setUsername(username);
        legacyUser.setEmail(email);
        legacyUser.setPasswordHash(passwordEncoder.encode(password));
        legacyUser.setVerified(false);
        legacyUser.setVerificationRequired(false);
        legacyUser.setActive(true);
        legacyUser.setRole(Role.USER);
        userRepository.saveAndFlush(legacyUser);

        // Legacy user logs in over real HTTP without verification requirement blocking them
        LoginRequest loginReq = new LoginRequest(email, password);
        ResponseEntity<String> loginResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/login",
                new HttpEntity<>(loginReq, headers),
                String.class
        );
        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);
        List<String> setCookies = loginResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies).anyMatch(c -> c.contains("aloute_access_token="));
    }

    @Test
    @DisplayName("Case 15: CSRF protection enforced on registration OTP endpoints over real HTTP")
    void case15_csrfProtectionEnforced() {
        HttpHeaders headersNoCsrf = new HttpHeaders();
        headersNoCsrf.setContentType(MediaType.APPLICATION_JSON);

        // Verify request without CSRF
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest("csrf_test@example.com", "123456");
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(verifyReq, headersNoCsrf),
                String.class
        );
        assertThat(verifyResp.getStatusCode().value()).isEqualTo(403);

        // Resend request without CSRF
        ResendRegistrationOtpRequest resendReq = new ResendRegistrationOtpRequest("csrf_test@example.com");
        ResponseEntity<String> resendResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                new HttpEntity<>(resendReq, headersNoCsrf),
                String.class
        );
        assertThat(resendResp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("Case 16: Email delivery failure permits safe resend over real HTTP and SMTP")
    void case16_emailFailurePermitsSafeResend() throws Exception {
        String email = generateEmail("email_fail_real");
        String username = generateUsername("efailr");
        String password = "SecurePassword123!";
        HttpHeaders headers = createCsrfHeaders();

        // 1. Simulate SMTP connection failure
        int messagesBeforeRegister = localSmtpServer.getReceivedMessages().size();
        localSmtpServer.setSimulateFailure(true);

        // Registration still returns 201 Created (mail failure does not roll back registration)
        RegisterRequest registerReq = new RegisterRequest(username, email, password);
        ResponseEntity<String> regResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/register",
                new HttpEntity<>(registerReq, headers),
                String.class
        );
        assertThat(regResp.getStatusCode().value()).isEqualTo(201);

        User createdUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(createdUser.isVerificationRequired()).isTrue();
        assertThat(createdUser.isVerified()).isFalse();

        // Wait for asynchronous mail task to attempt connection and fail
        Thread.sleep(600);
        assertThat(localSmtpServer.getReceivedMessages()).hasSize(messagesBeforeRegister);

        // 2. Restore SMTP server and fast-forward cooldown
        localSmtpServer.setSimulateFailure(false);
        RegistrationOtp otpRecord = otpRepository.findByUser(createdUser).orElseThrow();
        otpRecord.setResendAvailableAt(Instant.now().minusSeconds(5));
        otpRepository.saveAndFlush(otpRecord);

        int messagesBeforeResend = localSmtpServer.getReceivedMessages().size();

        // 3. Resend OTP over real HTTP listener
        ResendRegistrationOtpRequest resendReq = new ResendRegistrationOtpRequest(email);
        ResponseEntity<String> resendResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/resend-registration-otp",
                new HttpEntity<>(resendReq, headers),
                String.class
        );
        assertThat(resendResp.getStatusCode().value()).isEqualTo(200);

        // 4. Verify message delivered over real SMTP socket
        boolean received = localSmtpServer.awaitMessageCount(messagesBeforeResend + 1, 10, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        String rawEmail = localSmtpServer.getReceivedMessages().get(localSmtpServer.getReceivedMessages().size() - 1);
        String deliveredOtp = extractOtpFromEmail(rawEmail);

        // 5. Verify account with delivered OTP over real HTTP listener
        VerifyRegistrationOtpRequest verifyReq = new VerifyRegistrationOtpRequest(email, deliveredOtp);
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "http://localhost:" + httpPort + "/api/auth/verify-registration-otp",
                new HttpEntity<>(verifyReq, headers),
                String.class
        );
        assertThat(verifyResp.getStatusCode().value()).isEqualTo(200);

        User verifiedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verifiedUser.isVerified()).isTrue();
        assertThat(verifiedUser.isVerificationRequired()).isFalse();
    }
}
