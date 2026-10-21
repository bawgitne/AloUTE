package com.aloute.service.impl;

import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.entity.PasswordResetToken;
import com.aloute.entity.User;
import com.aloute.common.exception.AppException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.repository.PasswordResetTokenRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.PasswordResetService;
import com.aloute.service.email.DefaultEmailService;
import com.aloute.service.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Executor emailExecutor;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Qualifier("emailExecutor") Executor emailExecutor) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.emailExecutor = emailExecutor;
    }

    @Value("${aloute.app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${aloute.auth.reset-token-expiry-minutes:20}")
    private int expiryMinutes = 20;

    @Value("${aloute.auth.reset-request-cooldown-seconds:60}")
    private int cooldownSeconds = 60;

    @Value("${aloute.auth.reset-max-requests-per-window:5}")
    private int maxRequestsPerWindow = 5;

    private static final int MAX_TRACKED_EMAILS = 10000;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Object rateLimiterLock = new Object();
    private final Map<String, Deque<Instant>> emailRequestHistory = new HashMap<>();

    @Override
    @Transactional
    public void initiateForgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // Atomically reserve attempt and enforce abuse limits for ALL emails before lookup
        recordAndCheckAbuseLimits(email);

        Optional<Long> userIdOpt = userRepository.findIdByEmailIgnoreCase(email);
        if (userIdOpt.isEmpty()) {
            // Constant-time / timing mitigation: dummy hash computation
            sha256Hex(UUID.randomUUID().toString());
            log.info("Password reset requested for nonexistent email (silent generic response)");
            return;
        }

        // Consistent lock order: ALWAYS lock User row FIRST (no User entity was loaded into L1)
        User user = userRepository.findByIdForUpdate(userIdOpt.get()).orElse(null);
        if (user == null || !user.isActive()) {
            sha256Hex(UUID.randomUUID().toString());
            log.info("Password reset requested for inactive or deleted email (silent generic response)");
            return;
        }

        // Under user lock: read current token for this user
        Optional<PasswordResetToken> existingTokenOpt = tokenRepository.findByUser(user);

        // Generate 32 bytes cryptographically secure random token (64 hex characters)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken resetToken = existingTokenOpt.orElseGet(() -> {
            PasswordResetToken t = new PasswordResetToken();
            t.setUser(user);
            return t;
        });

        Long userAuthVersion = user.getAuthVersion() != null ? user.getAuthVersion() : 0L;
        resetToken.setTokenHash(tokenHash);
        resetToken.setAuthVersion(userAuthVersion);
        resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
        resetToken.setCreatedAt(Instant.now());

        tokenRepository.save(resetToken);

        String cleanBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "http://localhost:8080";
        String resetUrl = cleanBaseUrl + "/reset-password#token=" + rawToken;

        final String recipientEmail = user.getEmail();
        final String finalResetUrl = resetUrl;

        Runnable emailTask = () -> {
            try {
                emailService.sendPasswordResetEmail(recipientEmail, finalResetUrl);
            } catch (Exception ex) {
                log.error("Failed to dispatch password reset email asynchronously for recipient {}: {}",
                        DefaultEmailService.sanitizeForLog(recipientEmail), ex.getClass().getSimpleName());
            }
        };

        Runnable submitTask = () -> {
            try {
                emailExecutor.execute(emailTask);
            } catch (RejectedExecutionException ex) {
                // ponytail: in-memory bounded queue drops notifications when saturated or on process restart;
                // upgrade path: transactional outbox table with polling worker.
                log.error("Password reset email execution rejected by bounded executor (queue capacity exceeded) for recipient {}: {}",
                        DefaultEmailService.sanitizeForLog(recipientEmail), ex.getMessage());
            }
        };

        // Deliver notification ONLY after durable token commit
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

    @Override
    @Transactional(noRollbackFor = RejectedTokenCleanupException.class)
    public void resetPassword(ResetPasswordRequest request) {
        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Mật khẩu xác nhận không khớp.");
        }

        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 128) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Mật khẩu phải từ 8 đến 128 ký tự.");
        }

        String rawToken = request.getToken() != null ? request.getToken().trim() : "";
        if (rawToken.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt lại mật khẩu không hợp lệ.");
        }

        String presentedTokenHash = sha256Hex(rawToken);

        // Scalar pre-lookup: retrieve ONLY userId by token hash, preventing premature User or Token entity caching in L1
        Optional<Long> userIdOpt = tokenRepository.findUserIdByTokenHash(presentedTokenHash);
        if (userIdOpt.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng.");
        }

        // Consistent lock order: ALWAYS lock User row FIRST (no entities materialized before lock)
        User user = userRepository.findByIdForUpdate(userIdOpt.get())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Tài khoản không tồn tại."));

        // Reject inactive user after acquiring lock
        if (!user.isActive()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản đã bị vô hiệu hóa.");
        }

        // Under user lock: read current token for this user
        Optional<PasswordResetToken> currentTokenOpt = tokenRepository.findByUser(user);
        if (currentTokenOpt.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng.");
        }

        PasswordResetToken currentToken = currentTokenOpt.get();

        // Constant-time digest comparison: verify that the presented token is still current
        if (!MessageDigest.isEqual(
                currentToken.getTokenHash().getBytes(StandardCharsets.UTF_8),
                presentedTokenHash.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Liên kết đặt lại mật khẩu không còn hiệu lực.");
        }

        // Verify token is not expired (now >= expiresAt)
        if (currentToken.isExpired(Instant.now())) {
            tokenRepository.delete(currentToken);
            throw new RejectedTokenCleanupException("Liên kết đặt lại mật khẩu đã hết hạn.");
        }

        // Verify matching authVersion
        Long currentAuthVersion = user.getAuthVersion() != null ? user.getAuthVersion() : 0L;
        if (!Objects.equals(currentToken.getAuthVersion(), currentAuthVersion)) {
            tokenRepository.delete(currentToken);
            throw new RejectedTokenCleanupException("Liên kết đặt lại mật khẩu không còn hiệu lực.");
        }

        // Atomically replace password hash, increment authVersion, and consume reset token
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAuthVersion(currentAuthVersion + 1L);
        userRepository.save(user);

        tokenRepository.delete(currentToken);
        log.info("Password reset completed successfully for userId {}", user.getId());
    }

    // Only rejected-token cleanup commits on rejection; other failures retain normal rollback.
    private static final class RejectedTokenCleanupException extends AppException {
        private RejectedTokenCleanupException(String message) {
            super(ErrorCode.BAD_REQUEST, message);
        }
    }

    private void recordAndCheckAbuseLimits(String email) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofMinutes(15));

        synchronized (rateLimiterLock) {
            // If email is new and capacity reached, attempt to reclaim stale entries first
            if (!emailRequestHistory.containsKey(email) && emailRequestHistory.size() >= MAX_TRACKED_EMAILS) {
                evictStaleEntriesLocked(windowStart);
            }

            // Enforce hard bound at capacity (active keys are never evicted to bypass limits)
            if (!emailRequestHistory.containsKey(email) && emailRequestHistory.size() >= MAX_TRACKED_EMAILS) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Hệ thống đang bận. Vui lòng thử lại sau.");
            }

            Deque<Instant> timestamps = emailRequestHistory.computeIfAbsent(email, k -> new ArrayDeque<>());

            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequestsPerWindow) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Số lần yêu cầu vượt quá giới hạn cho phép. Vui lòng thử lại sau 15 phút.");
            }

            if (!timestamps.isEmpty()) {
                Instant lastRequest = timestamps.peekLast();
                if (now.isBefore(lastRequest.plusSeconds(cooldownSeconds))) {
                    throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED,
                            "Vui lòng chờ trước khi yêu cầu liên kết đặt lại mật khẩu mới.");
                }
            }

            // Atomically reserve attempt for ALL emails (known, unknown, inactive)
            timestamps.addLast(now);
        }
    }

    private void evictStaleEntriesLocked(Instant windowStart) {
        emailRequestHistory.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
