package com.aloute.service;

import java.util.List;

public interface TotpService {
    String generateSecretKey();

    String getOtpAuthUri(String accountName, String secretKey);

    String generateQrCodeDataUrl(String otpAuthUri);

    boolean verifyCode(String secretKey, String candidateCode);

    String generateCurrentCode(String secretKey);

    List<String> generateBackupCodes(int count);

    /**
     * Hashes a backup code with a per-code salt (PBKDF2, "v2:" prefixed format).
     * Legacy rows hashed with plain SHA-256 remain verifiable through
     * {@link #verifyBackupCode(String, String)} until regenerated.
     */
    String hashBackupCode(String rawCode);

    /** Constant-shape verification for both v2 (salted PBKDF2) and legacy SHA-256 rows. */
    boolean verifyBackupCode(String rawCode, String storedHash);
}
