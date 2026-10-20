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
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
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

    @Value("${aloute.auth.registration-otp-expiry-minutes:5}")
    private int expiryMinutes = 5;

    @Value("${aloute.auth.registration-otp-cooldown-seconds:60}")
    private int cooldownSeconds = 60;

    @Value("${aloute.auth.registration-otp-max-attempts:5}")
    private int maxAttempts = 5;

    private final SecureRandom secureRandom = new SecureRandom();

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
    public void validateSecret() {
        boolean isTest = environment.acceptsProfiles(Profiles.of("test"));
        if (!isTest) {
            if (secretKey == null || secretKey.isBlank() || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must be configured and contain at least 32 bytes in non-test environments");
            }
        } else {
            if (secretKey != null && !secretKey.isBlank() && secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("ALOUTE_REGISTRATION_OTP_SECRET must contain at least 32 bytes");
            }
            if (secretKey == null || secretKey.isBlank()) {
                secretKey = "test-only-temporary-secret-key-at-least-32-bytes-long";
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

        // Consistent lock order: User row is ALWAYS locked first
        Optional<User> userOpt = userRepository.findByEmailForUpdate(cleanEmail);
        if (userOpt.isEmpty() || !userOpt.get().isActive() || userOpt.get().isVerified() || !userOpt.get().isVerificationRequired()) {
            // Anti-enumeration: indistinguishable response and constant-time dummy HMAC for nonexistent, inactive, already verified, or legacy accounts
            computeHmac("000000", "00000000000000000000000000000000");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã được sử dụng");
        }

        User user = userOpt.get();

        // Lock RegistrationOtp row second
        Optional<RegistrationOtp> otpOpt = otpRepository.findByUserIdForUpdate(user.getId());
        if (otpOpt.isEmpty()) {
            computeHmac("000000", "00000000000000000000000000000000");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không hợp lệ hoặc đã được sử dụng");
        }

        RegistrationOtp otpRecord = otpOpt.get();
        Instant now = Instant.now();

        if (otpRecord.isExpired(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        if (otpRecord.isMaxAttemptsReached()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS_EXCEEDED", "Đã vượt quá số lần thử tối đa (5 lần). Vui lòng yêu cầu mã xác thực mới.");
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
            int remaining = Math.max(0, otpRecord.getMaxAttempts() - newAttempts);
            if (newAttempts >= otpRecord.getMaxAttempts()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS_EXCEEDED", "Mã xác thực không chính xác. Đã vượt quá 5 lần thử tối đa. Vui lòng gửi lại mã mới.");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực không chính xác. Bạn còn " + remaining + " lần thử.");
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

        // Consistent lock order: User row is ALWAYS locked first
        Optional<User> userOpt = userRepository.findByEmailForUpdate(cleanEmail);
        if (userOpt.isEmpty() || !userOpt.get().isActive() || userOpt.get().isVerified() || !userOpt.get().isVerificationRequired()) {
            // Anti-enumeration: indistinguishable generic success for nonexistent, inactive, already verified, or legacy accounts
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
                long secondsLeft = Math.max(1, Duration.between(now, existing.getResendAvailableAt()).getSeconds() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Vui lòng đợi " + secondsLeft + " giây trước khi yêu cầu gửi lại mã.");
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
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal((salt + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 for registration OTP", e);
        }
    }
}
