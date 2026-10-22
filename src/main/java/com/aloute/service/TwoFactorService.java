package com.aloute.service;

import com.aloute.dto.request.VerifyTwoFactorLoginRequest;
import com.aloute.dto.response.TwoFactorEnableResponse;
import com.aloute.dto.response.TwoFactorSetupResponse;
import com.aloute.dto.response.TwoFactorStatusResponse;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.principal.UserPrincipal;

import java.util.List;

public interface TwoFactorService {
    record AuthenticatedSession(UserPrincipal principal, UserResponse user, String token) {}

    TwoFactorSetupResponse setupTwoFactor(Long userId);

    TwoFactorEnableResponse enableTwoFactor(Long userId, String code);

    void sendEmailSetupOtp(Long userId);

    TwoFactorEnableResponse enableTwoFactorEmail(Long userId, String code);

    void disableTwoFactor(Long userId, String currentPassword, String code);

    TwoFactorStatusResponse getStatus(Long userId);

    List<String> regenerateBackupCodes(Long userId, String currentPassword);

    AuthenticatedSession verifyLogin(VerifyTwoFactorLoginRequest request);

    void sendEmailFallbackOtp(String twoFactorTicket);

    void clearEmailOtpCache();

    int getActiveOtpCount(Long userId);
}
