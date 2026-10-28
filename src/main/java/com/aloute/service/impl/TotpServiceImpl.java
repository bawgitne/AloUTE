package com.aloute.service.impl;

import com.aloute.service.TotpService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class TotpServiceImpl implements TotpService {

    private static final String ISSUER = "AloUTE";
    private static final int SECRET_BYTE_LENGTH = 20; // 160 bits RFC 6238
    private static final int TIME_STEP_SECONDS = 30;
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String BACKUP_CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // No ambiguous 0/O, 1/I
    private static final int PBKDF2_ITERATIONS = 60_000;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateSecretKey() {
        byte[] buffer = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    @Override
    public String getOtpAuthUri(String accountName, String secretKey) {
        String cleanAccount = (accountName != null && !accountName.isBlank()) ? accountName.trim() : "SinhVien";
        String encodedAccount = URLEncoder.encode(cleanAccount, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedIssuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount + "?secret=" + secretKey + "&issuer=" + encodedIssuer;
    }

    @Override
    public String generateQrCodeDataUrl(String otpAuthUri) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUri, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate QR code", ex);
        }
    }

    @Override
    public boolean verifyCode(String secretKey, String candidateCode) {
        if (secretKey == null || candidateCode == null) {
            return false;
        }
        String cleanCode = candidateCode.replaceAll("\\s+", "").trim();
        if (cleanCode.length() != 6) {
            return false;
        }

        try {
            byte[] keyBytes = decodeBase32(secretKey);
            long currentTimeStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;

            // Check tolerance window [-1, 0, +1] (clock skew tolerance of ±30 seconds)
            for (int i = -1; i <= 1; i++) {
                String expected = generateCodeForStep(keyBytes, currentTimeStep + i);
                if (expected.equals(cleanCode)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public String generateCurrentCode(String secretKey) {
        try {
            byte[] keyBytes = decodeBase32(secretKey);
            long step = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
            return generateCodeForStep(keyBytes, step);
        } catch (Exception ex) {
            throw new RuntimeException("Error generating TOTP code", ex);
        }
    }

    @Override
    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                if (j == 4) sb.append('-');
                int idx = secureRandom.nextInt(BACKUP_CODE_CHARS.length());
                sb.append(BACKUP_CODE_CHARS.charAt(idx));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    @Override
    public String hashBackupCode(String rawCode) {
        if (rawCode == null) return null;
        String clean = rawCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase().trim();
        try {
            // BL-08: per-code salted PBKDF2 ("v2:" prefix). Legacy unsalted
            // SHA-256 rows stay verifiable via verifyBackupCode until the user
            // regenerates their codes.
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            byte[] derived = pbkdf2(clean.getBytes(StandardCharsets.UTF_8), salt);
            return "v2:" + Base64.getEncoder().encodeToString(salt)
                    + ":" + Base64.getEncoder().encodeToString(derived);
        } catch (Exception ex) {
            throw new RuntimeException("Error hashing backup code", ex);
        }
    }

    @Override
    public boolean verifyBackupCode(String rawCode, String storedHash) {
        if (rawCode == null || storedHash == null) return false;
        String clean = rawCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase().trim();
        try {
            if (storedHash.startsWith("v2:")) {
                String[] parts = storedHash.split(":", 3);
                if (parts.length != 3) return false;
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                byte[] derived = pbkdf2(clean.getBytes(StandardCharsets.UTF_8), salt);
                return MessageDigest.isEqual(expected, derived);
            }
            // Legacy unsalted SHA-256 rows (pre-BL-08 accounts keep working).
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] legacy = sha256.digest(clean.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getDecoder().decode(storedHash);
            return MessageDigest.isEqual(expected, legacy);
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] pbkdf2(byte[] input, byte[] salt) throws Exception {
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                new String(input, StandardCharsets.UTF_8).toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] derived = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return derived;
    }

    private String generateCodeForStep(byte[] keyBytes, long step) throws Exception {
        byte[] data = new byte[8];
        long value = step;
        for (int i = 8; i-- > 0; value >>>= 8) {
            data[i] = (byte) value;
        }

        SecretKeySpec signKey = new SecretKeySpec(keyBytes, HMAC_ALGO);
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;
        long truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;

        return String.format("%06d", truncatedHash);
    }

    private String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;
        while (next < data.length) {
            buffer = (buffer << 8) | (data[next++] & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            buffer <<= (5 - bitsLeft);
            result.append(BASE32_CHARS.charAt(buffer & 0x1F));
        }
        return result.toString();
    }

    private byte[] decodeBase32(String base32) {
        String clean = base32.replaceAll("[^A-Za-z2-7]", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
