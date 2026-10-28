package com.aloute.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BL-07: the TOTP master key must never be the public dev default (or trivially
 * short) once the instance is flagged as production. Dev keeps the default so
 * existing encrypted secrets remain decryptable.
 */
class TwoFactorEncryptionKeyGuardTest {

    private static final String DEV_DEFAULT = "aloute-dev-master-encryption-key-for-2fa-totp-secrets-32b!";

    private MockEnvironment envWith(String productionMode) {
        MockEnvironment env = new MockEnvironment();
        if (productionMode != null) {
            env.setProperty("aloute.production-mode", productionMode);
        }
        return env;
    }

    @Test
    @DisplayName("Dev default key still initializes outside production mode")
    void devDefaultAllowedOutsideProduction() {
        assertThatCode(() -> new TwoFactorEncryptionService(DEV_DEFAULT, envWith(null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new TwoFactorEncryptionService(DEV_DEFAULT, envWith("false")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BL-07: dev default key refuses startup in production mode")
    void devDefaultRefusedInProduction() {
        assertThatThrownBy(() -> new TwoFactorEncryptionService(DEV_DEFAULT, envWith("true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("two-factor-master-key");
    }

    @Test
    @DisplayName("BL-07: blank or short keys refuse startup in production mode")
    void weakKeysRefusedInProduction() {
        assertThatThrownBy(() -> new TwoFactorEncryptionService("", envWith("true")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TwoFactorEncryptionService("short", envWith("true")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Strong key in production mode initializes and round-trips secrets")
    void strongKeyRoundTrips() {
        TwoFactorEncryptionService service =
                new TwoFactorEncryptionService("unique-strong-production-master-key-9f2c", envWith("true"));
        String encrypted = service.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(service.decrypt(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
    }
}
