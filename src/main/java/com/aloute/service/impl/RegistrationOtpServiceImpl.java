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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Bounded thread-safe in-process recipient rate limiter (10 requests/minute, max 10,000 tracked keys)
    private static final int MAX_TRACKED_RECIPIENTS = 10_000;
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 60_000L;

    private static class RequestBucket {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, RequestBucket> recipientRateLimiter = new ConcurrentHashMap<>();

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

        boolean isTest = environment.acceptsProfiles(Profiles.of("test"));
        if (!isTest) {
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
            this.decodedSecretKey = decoded;
        } else {
            if (secretKey != null && !secretKey.isBlank()) {
                byte[] decoded = null;
                try {
                    decoded = Base64.getDecoder().decode(secretKey.trim());
                } catch (IllegalArgumentException ignored) {
                    // Raw UTF-8 bytes fallback for explicit test configuration
                    decoded = secretKey.trim().getBytes(StandardCharsets.UTF_8);
                }
                if (decoded.length < 32) {
                    throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must contain at least 32 bytes");
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
                this.decodedSecretKey = decoded;
            } else {
                // Test profile without explicit key uses test-only Base64 constant key
                this.decodedSecretKey = Base64.getDecoder().decode("dGVzdC1vbmx5LXRlbXBvcmFyeS1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmch");
            }
        }
    }

    private void checkRecipientRequestLimit(String cleanEmail) {
        long now = System.currentTimeMillis();
        // Atomic capacity bounding: reject new admissions if capacity reached and key is absent
        if (recipientRateLimiter.size() >= MAX_TRACKED_RECIPIENTS && !recipientRateLimiter.containsKey(cleanEmail)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.");
        }
        RequestBucket bucket = recipientRateLimiter.computeIfAbsent(cleanEmail, k -> new RequestBucket());
        synchronized (bucket) {
            if (now - bucket.windowStart > RATE_LIMIT_WINDOW_MILLIS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            if (bucket.count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Quá nhiều yêu cầu. Vui lòng đợi 1 phút trước khi thử lại.");
            }
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
