package com.aloute.service.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.VerifyTwoFactorLoginRequest;
import com.aloute.dto.response.TwoFactorEnableResponse;
import com.aloute.dto.response.TwoFactorSetupResponse;
import com.aloute.dto.response.TwoFactorStatusResponse;
import com.aloute.dto.response.UserResponse;
import com.aloute.entity.User;
import com.aloute.entity.UserBackupCode;
import com.aloute.repository.UserBackupCodeRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.crypto.TwoFactorEncryptionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.TotpService;
import com.aloute.service.TwoFactorService;
import com.aloute.service.email.EmailService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorServiceImpl implements TwoFactorService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long EMAIL_OTP_EXPIRY_SECONDS = 300; // 5 minutes

    private final UserRepository userRepository;
    private final UserBackupCodeRepository userBackupCodeRepository;
    private final TotpService totpService;
    private final TwoFactorEncryptionService twoFactorEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    private final ConcurrentHashMap<String, Integer> ticketFailedAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, EmailOtpEntry> emailOtpCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    private record EmailOtpEntry(String otp, Instant expiresAt) {}

    @Override
    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(Long userId) {
        User user = requireUser(userId);
        String secretKey = totpService.generateSecretKey();
        String encryptedSecret = twoFactorEncryptionService.encrypt(secretKey);

        user.setTwoFactorTempSecret(encryptedSecret);
        userRepository.save(user);

        String accountName = user.getEmail() != null ? user.getEmail() : user.getUsername();
        String otpAuthUri = totpService.getOtpAuthUri(accountName, secretKey);
        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(otpAuthUri);

        return new TwoFactorSetupResponse(secretKey, otpAuthUri, qrCodeDataUrl);
    }

    @Override
    @Transactional
    public TwoFactorEnableResponse enableTwoFactor(Long userId, String code) {
        User user = requireUser(userId);
        if (user.getTwoFactorTempSecret() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_NOT_INITIALIZED",
                    "Chưa khởi tạo thiết lập 2FA. Vui lòng bấm quét mã QR trước.");
        }

        String decryptedSecret = twoFactorEncryptionService.decrypt(user.getTwoFactorTempSecret());
        boolean isValid = totpService.verifyCode(decryptedSecret, code);
        if (!isValid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_INVALID_CODE",
                    "Mã xác thực 6 số không chính xác hoặc đã hết hạn. Vui lòng kiểm tra lại đồng hồ điện thoại.");
        }

        user.setTwoFactorSecret(user.getTwoFactorTempSecret());
        user.setTwoFactorTempSecret(null);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorConfirmedAt(Instant.now());
        user.setTwoFactorType("TOTP");
        userRepository.save(user);

        // Delete old backup codes and generate 8 new backup codes
        userBackupCodeRepository.deleteByUserId(userId);
        List<String> rawBackupCodes = totpService.generateBackupCodes(8);
        for (String rawCode : rawBackupCodes) {
            UserBackupCode backupCode = new UserBackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(totpService.hashBackupCode(rawCode));
            backupCode.setUsed(false);
            userBackupCodeRepository.save(backupCode);
        }

        return new TwoFactorEnableResponse(
                "Đã kích hoạt bảo mật 2 lớp thành công! Vui lòng lưu lại 8 mã dự phòng dưới đây ở nơi an toàn.",
                rawBackupCodes
        );
    }

    @Override
    public void sendEmailSetupOtp(Long userId) {
        User user = requireUser(userId);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_MISSING",
                    "Tài khoản của bạn chưa có địa chỉ email để thiết lập xác thực qua email.");
        }

        int number = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(number);
        emailOtpCache.put(userId, new EmailOtpEntry(otpCode, Instant.now().plusSeconds(EMAIL_OTP_EXPIRY_SECONDS)));

        emailService.sendTwoFactorFallbackOtpEmail(user.getEmail(), otpCode);
        log.info("Dispatched 2FA setup email OTP to user id {}", userId);
    }

    @Override
    @Transactional
    public TwoFactorEnableResponse enableTwoFactorEmail(Long userId, String code) {
        User user = requireUser(userId);
        EmailOtpEntry entry = emailOtpCache.get(userId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_INVALID_CODE",
                    "Mã xác thực email đã hết hạn hoặc chưa được tạo. Vui lòng bấm gửi lại mã.");
        }

        if (!entry.otp().equals(code != null ? code.trim() : "")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_INVALID_CODE",
                    "Mã xác thực email không chính xác. Vui lòng kiểm tra lại hộp thư.");
        }

        emailOtpCache.remove(userId);

        user.setTwoFactorSecret(null);
        user.setTwoFactorTempSecret(null);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorConfirmedAt(Instant.now());
        user.setTwoFactorType("EMAIL");
        userRepository.save(user);

        // Delete old backup codes and generate 8 new backup codes
        userBackupCodeRepository.deleteByUserId(userId);
        List<String> rawBackupCodes = totpService.generateBackupCodes(8);
        for (String rawCode : rawBackupCodes) {
            UserBackupCode backupCode = new UserBackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(totpService.hashBackupCode(rawCode));
            backupCode.setUsed(false);
            userBackupCodeRepository.save(backupCode);
        }

        return new TwoFactorEnableResponse(
                "Đã kích hoạt bảo mật 2 lớp qua Email thành công! Mỗi lần đăng nhập, AloUTE sẽ gửi mã xác thực 6 số về hộp thư của bạn.",
                rawBackupCodes
        );
    }

    @Override
    @Transactional
    public void disableTwoFactor(Long userId, String currentPassword, String code) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD",
                    "Mật khẩu hiện tại không chính xác.");
        }

        boolean codeValid = false;
        if (user.getTwoFactorSecret() != null) {
            String decryptedSecret = twoFactorEncryptionService.decrypt(user.getTwoFactorSecret());
            codeValid = totpService.verifyCode(decryptedSecret, code);
        }

        if (!codeValid) {
            // Check backup code
            String hashed = totpService.hashBackupCode(code);
            List<UserBackupCode> unusedCodes = userBackupCodeRepository.findByUserIdAndUsedFalse(userId);
            for (UserBackupCode ubc : unusedCodes) {
                if (ubc.getCodeHash().equals(hashed)) {
                    codeValid = true;
                    ubc.setUsed(true);
                    ubc.setUsedAt(Instant.now());
                    userBackupCodeRepository.save(ubc);
                    break;
                }
            }
        }

        if (!codeValid) {
            // Check email OTP if active
            EmailOtpEntry entry = emailOtpCache.get(userId);
            if (entry != null && entry.expiresAt().isAfter(Instant.now()) && entry.otp().equals(code != null ? code.trim() : "")) {
                codeValid = true;
                emailOtpCache.remove(userId);
            }
        }

        if (!codeValid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_INVALID_CODE",
                    "Mã xác thực hoặc mã dự phòng không chính xác.");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorTempSecret(null);
        user.setTwoFactorType(null);
        user.setTwoFactorConfirmedAt(null);
        // Increment authVersion to invalidate any other stale sessions across devices
        user.setAuthVersion((user.getAuthVersion() != null ? user.getAuthVersion() : 0L) + 1L);
        userRepository.save(user);

        userBackupCodeRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus(Long userId) {
        User user = requireUser(userId);
        long remainingCodes = userBackupCodeRepository.countByUserIdAndUsedFalse(userId);
        return new TwoFactorStatusResponse(
                user.isTwoFactorEnabled(),
                user.getTwoFactorType() != null ? user.getTwoFactorType() : "TOTP",
                user.getTwoFactorConfirmedAt(),
                remainingCodes
        );
    }

    @Override
    @Transactional
    public List<String> regenerateBackupCodes(Long userId, String currentPassword) {
        User user = requireUser(userId);
        if (!user.isTwoFactorEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_2FA_NOT_ENABLED",
                    "Tài khoản chưa bật bảo mật 2 lớp.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD",
                    "Mật khẩu hiện tại không chính xác.");
        }

        userBackupCodeRepository.deleteByUserId(userId);
        List<String> rawCodes = totpService.generateBackupCodes(8);
        for (String rawCode : rawCodes) {
            UserBackupCode backupCode = new UserBackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(totpService.hashBackupCode(rawCode));
            backupCode.setUsed(false);
            userBackupCodeRepository.save(backupCode);
        }
        return rawCodes;
    }

    @Override
    @Transactional
    public AuthenticatedSession verifyLogin(VerifyTwoFactorLoginRequest request) {
        String ticket = request.twoFactorTicket().trim();
        Claims claims;
        try {
            claims = jwtTokenProvider.parseTwoFactorTicket(ticket);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_2FA_TICKET",
                    "Phiên xác thực 2 bước không hợp lệ hoặc đã hết hạn (5 phút). Vui lòng đăng nhập lại.");
        }

        Long userId = Long.valueOf(claims.getSubject());
        User user = requireUser(userId);

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_INACTIVE", "Tài khoản đang bị vô hiệu hóa.");
        }

        int failed = ticketFailedAttempts.getOrDefault(ticket, 0);
        if (failed >= MAX_FAILED_ATTEMPTS) {
            ticketFailedAttempts.remove(ticket);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_2FA_TICKET_LOCKED",
                    "Bạn đã nhập sai mã quá 5 lần. Phiên xác thực bị hủy để bảo đảm an toàn. Vui lòng đăng nhập lại.");
        }

        boolean verified = false;
        String rawCode = request.code().trim();

        if (request.isBackupCode() || rawCode.contains("-") || rawCode.length() == 8) {
            // Verify backup code
            String hashed = totpService.hashBackupCode(rawCode);
            List<UserBackupCode> unused = userBackupCodeRepository.findByUserIdAndUsedFalse(userId);
            for (UserBackupCode ubc : unused) {
                if (ubc.getCodeHash().equals(hashed)) {
                    verified = true;
                    ubc.setUsed(true);
                    ubc.setUsedAt(Instant.now());
                    userBackupCodeRepository.save(ubc);
                    break;
                }
            }
        } else {
            // Try TOTP first
            if (user.getTwoFactorSecret() != null) {
                String decryptedSecret = twoFactorEncryptionService.decrypt(user.getTwoFactorSecret());
                verified = totpService.verifyCode(decryptedSecret, rawCode);
            }

            // If TOTP failed, check fallback email OTP
            if (!verified) {
                EmailOtpEntry entry = emailOtpCache.get(userId);
                if (entry != null && entry.expiresAt().isAfter(Instant.now()) && entry.otp().equals(rawCode)) {
                    verified = true;
                    emailOtpCache.remove(userId);
                }
            }
        }

        if (!verified) {
            int newFailed = failed + 1;
            ticketFailedAttempts.put(ticket, newFailed);
            int remaining = MAX_FAILED_ATTEMPTS - newFailed;
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_2FA_INVALID_CODE",
                    "Mã xác thực không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // Successfully verified
        ticketFailedAttempts.remove(ticket);
        UserPrincipal principal = UserPrincipal.from(user);
        String sessionToken = jwtTokenProvider.generate(principal);
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.isVerified(),
                user.isPrivateAccount(), user.isActive(), user.isTwoFactorEnabled()
        );

        return new AuthenticatedSession(principal, userResponse, sessionToken);
    }

    @Override
    public void sendEmailFallbackOtp(String twoFactorTicket) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseTwoFactorTicket(twoFactorTicket.trim());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_2FA_TICKET",
                    "Phiên xác thực 2 bước không hợp lệ hoặc đã hết hạn.");
        }

        Long userId = Long.valueOf(claims.getSubject());
        User user = requireUser(userId);

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_NOT_AVAILABLE",
                    "Tài khoản không có email để nhận mã OTP.");
        }

        // Generate 6-digit numeric OTP
        int number = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(number);

        emailOtpCache.put(userId, new EmailOtpEntry(otpCode, Instant.now().plusSeconds(EMAIL_OTP_EXPIRY_SECONDS)));
        emailService.sendTwoFactorFallbackOtpEmail(user.getEmail(), otpCode);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Người dùng không tồn tại"));
    }
}
