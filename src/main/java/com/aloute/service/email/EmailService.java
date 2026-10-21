package com.aloute.service.email;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetUrl);
    void sendRegistrationOtpEmail(String toEmail, String otpCode);
    void sendTwoFactorFallbackOtpEmail(String toEmail, String otpCode);
}
