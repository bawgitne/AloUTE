package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.RegistrationOtp;
import com.aloute.entity.User;
import com.aloute.repository.RegistrationOtpRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.RegistrationOtpService;
import com.aloute.service.email.EmailService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class RegistrationOtpServiceImpl implements RegistrationOtpService {

    private final UserRepository userRepository;
    private final RegistrationOtpRepository otpRepository;
    private final EmailService emailService;
    private final Executor emailExecutor;
    private final Environment environment;

    @Value("${aloute.auth.registration-otp-secret:${ALOUTE_REGISTRATION_OTP_SECRET:}}")
    private String secretKey;

    private byte[] decodedSecretKey;

    @Value("${aloute.auth.registration-otp-expiry-minutes:5}")
    private int expiryMinutes = 5;

    @Value("${aloute.auth.registration-otp-cooldown-seconds:60}")
    private int cooldownSeconds = 60;

    @Value("${aloute.auth.registration-otp-max-attempts:5}")
    private int maxAttempts = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private static final List<byte[]> KNOWN_PUBLIC_FIXTURE_BYTES = List.of(
            Base64.getDecoder().decode("dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE="),
            Base64.getDecoder().decode("dGVzdC1vbmx5LXRlbXBvcmFyeS1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmch"),
            Base64.getDecoder().decode("YWxvdXRlLWRldi1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcyEh"),
            Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    );

    private static boolean isKnownPublicFixture(byte[] decoded) {
        if (decoded == null) {
            return false;
        }
        for (byte[] fixture : KNOWN_PUBLIC_FIXTURE_BYTES) {
            if (MessageDigest.isEqual(decoded, fixture)) {
                return true;
            }
        }
        return false;
    }

    // Bounded thread-safe in-process recipient rate limiter (10 requests/minute, max 10,000 tracked keys)
    // Minimally reuses proven lock + stale entry cleanup pattern from PasswordResetServiceImpl
    private static final int MAX_TRACKED_RECIPIENTS = 10_000;
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private final Object rateLimiterLock = new Object();
    private final Map<String, Deque<Instant>> recipientRequestHistory = new HashMap<>();

    public RegistrationOtpServiceImpl(
            UserRepository userRepository,
            RegistrationOtpRepository otpRepository,
            EmailService emailService,
            @Qualifier("emailExecutor") Executor emailExecutor,
            Environment environment) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.emailExecutor = emailExecutor;
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        // Safe limit boundaries validation: expiry <= 5m, attempts <= 5, cooldown >= 60s
        if (expiryMinutes > 5 || expiryMinutes <= 0) {
            throw new IllegalStateException("aloute.auth.registration-otp-expiry-minutes must be between 1 and 5 minutes");
        }
        if (maxAttempts > 5 || maxAttempts <= 0) {
            throw new IllegalStateException("aloute.auth.registration-otp-max-attempts must be between 1 and 5");
        }
        if (cooldownSeconds < 60) {
            throw new IllegalStateException("aloute.auth.registration-otp-cooldown-seconds must be at least 60 seconds");
        }

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must be configured as a Base64-encoded secret");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must be valid Base64", e);
        }

        if (decoded.length < 32) {
            throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must decode to at least 32 bytes");
        }

        boolean allSame = true;
        for (int i = 1; i < decoded.length; i++) {
            if (decoded[i] != decoded[0]) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET cannot be a trivial repeated-byte key");
        }

        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));
        boolean isTest = environment.acceptsProfiles(Profiles.of("test"));
        boolean isSafeTest = isTest && !isProduction;

        if (!isSafeTest && isKnownPublicFixture(decoded)) {
            throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET cannot use known public test fixture outside safe test configuration");
        }

        this.decodedSecretKey = decoded;
    }

    private void checkRecipientRequestLimit(String cleanEmail) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);

        synchronized (rateLimiterLock) {
            // If recipient is new and capacity reached, attempt to reclaim stale entries first
            if (!recipientRequestHistory.containsKey(cleanEmail) && recipientRequestHistory.size() >= MAX_TRACKED_RECIPIENTS) {
                evictStaleEntriesLocked(windowStart);
            }

            // Enforce hard bound at capacity (active keys are never evicted to bypass limits)
            if (!recipientRequestHistory.containsKey(cleanEmail) && recipientRequestHistory.size() >= MAX_TRACKED_RECIPIENTS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Hệ thống đang bận. Vui lòng thử lại sau.");
            }

            Deque<Instant> timestamps = recipientRequestHistory.computeIfAbsent(cleanEmail, k -> new ArrayDeque<>());

            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Quá nhiều yêu cầu. Vui lòng đợi 1 phút trước khi thử lại.");
            }

            // Atomically reserve attempt for ALL recipients (known, unknown, inactive)
            timestamps.addLast(now);
        }
    }

    private void evictStaleEntriesLocked(Instant windowStart) {
        recipientRequestHistory.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
    }

    public int getTrackedRecipientsCount() {
        synchronized (rateLimiterLock) {
            return recipientRequestHistory.size();
        }
    }

    public void clearRateLimiter() {
        synchronized (rateLimiterLock) {
            recipientRequestHistory.clear();
        }
    }

    public void recordManualRequestForTesting(String email, Instant timestamp) {
        synchronized (rateLimiterLock) {
            recipientRequestHistory.computeIfAbsent(email, k -> new ArrayDeque<>()).addLast(timestamp);
        }
    }

    @Override
    @Transactional
    public void initiateRegistrationOtp(User user) {
        issueOtpAndDispatch(user);
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(String email, String otp) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Email không được để trống");
        }
        if (otp == null || !otp.matches("^\\d{6}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Mã xác thực phải gồm đúng 6 chữ số");
        }

        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);

        // Bounded per-recipient request rate limit check BEFORE any account-state branching
        checkRecipientRequestLimit(cleanEmail);

        // Consistent lock order: User row is ALWAYS locked first
        Optional<User> userOpt = userRepository.findByEmailForUpdate(cleanEmail);
        if (userOpt.isEmpty() || !userOpt.get().isActive() || userOpt.get().isVerified() || !userOpt.get().isVerificationRequired()) {
            // Anti-enumeration: uniform error response and constant-time dummy HMAC
            computeHmac("000000", "00000000000000000000000000000000");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
        }

        User user = userOpt.get();

        // Lock RegistrationOtp row second
        Optional<RegistrationOtp> otpOpt = otpRepository.findByUserIdForUpdate(user.getId());
        if (otpOpt.isEmpty()) {
            computeHmac("000000", "00000000000000000000000000000000");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
        }

        RegistrationOtp otpRecord = otpOpt.get();
        Instant now = Instant.now();

        // Check expiration and max attempts
        if (otpRecord.isExpired(now) || otpRecord.isMaxAttemptsReached()) {
            computeHmac("000000", "00000000000000000000000000000000");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
        }

        String computedHash = computeHmac(otp.trim(), otpRecord.getSalt());
        boolean matches = MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                otpRecord.getOtpHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            int newAttempts = otpRecord.getAttempts() + 1;
            otpRecord.setAttempts(newAttempts);
            otpRepository.saveAndFlush(otpRecord);
            // Anti-enumeration: uniform error message without leaking remaining attempt count
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
        }

        // OTP valid: atomically activate user and delete OTP record (single-use enforcement)
        user.setVerified(true);
        user.setVerificationRequired(false);
        userRepository.save(user);

        otpRepository.delete(otpRecord);
        log.info("Registration OTP successfully verified for userId {}", user.getId());
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public void resendOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Email không được để trống");
        }
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);

        // Bounded per-recipient request rate limit check BEFORE any account-state branching
        checkRecipientRequestLimit(cleanEmail);

        // Consistent lock order: User row is ALWAYS locked first
        Optional<User> userOpt = userRepository.findByEmailForUpdate(cleanEmail);
        if (userOpt.isEmpty() || !userOpt.get().isActive() || userOpt.get().isVerified() || !userOpt.get().isVerificationRequired()) {
            // Anti-enumeration: silent generic success without dispatching email
            computeHmac("000000", "00000000000000000000000000000000");
            log.info("Registration OTP resend requested for nonexistent, inactive, already verified, or legacy account");
            return;
        }

        User user = userOpt.get();

        // Lock RegistrationOtp row second
        Optional<RegistrationOtp> existingOpt = otpRepository.findByUserIdForUpdate(user.getId());
        Instant now = Instant.now();

        if (existingOpt.isPresent()) {
            RegistrationOtp existing = existingOpt.get();
            if (!existing.isResendAllowed(now)) {
                // Anti-enumeration: within 60s cooldown, return generic success silently without dispatching email
                log.info("Registration OTP resend requested within active cooldown for userId {}", user.getId());
                return;
            }
        }

        issueOtpAndDispatch(user);
    }

    private void issueOtpAndDispatch(User user) {
        // Generate cryptographically secure 6-digit numeric OTP (100000 - 999999)
        int randomCode = 100_000 + secureRandom.nextInt(900_000);
        String rawOtp = String.valueOf(randomCode);

        // Generate 16 bytes random salt (32 hex characters)
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);

        String otpHash = computeHmac(rawOtp, salt);

        Optional<RegistrationOtp> existingOpt = otpRepository.findByUserIdForUpdate(user.getId());
        RegistrationOtp otpRecord = existingOpt.orElseGet(() -> {
            RegistrationOtp record = new RegistrationOtp();
            record.setUser(user);
            return record;
        });

        Instant now = Instant.now();
        otpRecord.setOtpHash(otpHash);
        otpRecord.setSalt(salt);
        otpRecord.setAttempts(0);
        otpRecord.setMaxAttempts(maxAttempts);
        otpRecord.setExpiresAt(now.plus(Duration.ofMinutes(expiryMinutes)));
        otpRecord.setResendAvailableAt(now.plusSeconds(cooldownSeconds));
        otpRecord.setUpdatedAt(now);

        otpRepository.saveAndFlush(otpRecord);

        final String recipientEmail = user.getEmail();
        final String otpForDispatch = rawOtp;

        Runnable emailTask = () -> {
            try {
                emailService.sendRegistrationOtpEmail(recipientEmail, otpForDispatch);
            } catch (Exception ex) {
                log.warn("Failed to dispatch registration OTP email asynchronously: {}", ex.getClass().getSimpleName());
            }
        };

        Runnable submitTask = () -> {
            try {
                emailExecutor.execute(emailTask);
            } catch (RejectedExecutionException ex) {
                log.warn("Registration OTP email execution rejected by bounded executor: {}", ex.getMessage());
            }
        };

        // Dispatch email ONLY after transaction successfully commits
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
        } else {
            submitTask.run();
        }
    }

    private String computeHmac(String otp, String salt) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(decodedSecretKey, "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal((salt + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 for registration OTP", e);
        }
    }
}
