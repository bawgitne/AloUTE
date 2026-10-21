package com.aloute.service;

import java.util.List;

public interface TotpService {
    String generateSecretKey();

    String getOtpAuthUri(String accountName, String secretKey);

    String generateQrCodeDataUrl(String otpAuthUri);

    boolean verifyCode(String secretKey, String candidateCode);

    String generateCurrentCode(String secretKey);

    List<String> generateBackupCodes(int count);

    String hashBackupCode(String rawCode);
}
