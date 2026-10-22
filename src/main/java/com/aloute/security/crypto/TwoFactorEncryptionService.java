package com.aloute.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TwoFactorEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorEncryptionService(
            @Value("${aloute.auth.two-factor-master-key:aloute-dev-master-encryption-key-for-2fa-totp-secrets-32b!}")
            String configuredKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize TwoFactorEncryptionService master key", ex);
        }
    }

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to encrypt two-factor secret", ex);
        }
    }

    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedSecret);
            if (decoded.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted content too short");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to decrypt two-factor secret", ex);
        }
    }
}
