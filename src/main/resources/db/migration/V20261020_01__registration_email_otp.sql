-- =========================================================================
-- AloUTE Safe Migration: Registration Email OTP Verification Support
-- Compatible with MySQL 8.0 (via preflight) and H2 (dev/test runtime)
-- For full MySQL 8.0 script with preflight information_schema checks,
-- see mysql8_auth_and_registration_otp.sql in this directory.
-- For H2 dev/test script, see h2_auth_and_registration_otp.sql.
-- =========================================================================

-- 1. Add verification_required column to users table with DEFAULT FALSE
-- Legacy account compatibility policy:
-- Existing accounts have verification_required = FALSE and are NOT locked out,
-- preserving full access for existing demo and seed accounts.
-- Newly registered accounts have verification_required = TRUE until OTP is verified.
-- Note: MySQL 8.0 requires preflight column check; H2 supports IF NOT EXISTS.
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_required BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Create registration_otps table for storing secure keyed OTP hashes
-- Stores HMAC-SHA256 non-reversible verifier (otp_hash) and random per-record salt.
-- Plaintext OTP is NEVER stored or logged.
CREATE TABLE IF NOT EXISTS registration_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    salt VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    expires_at TIMESTAMP NOT NULL,
    resend_available_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_registration_otps_user UNIQUE (user_id),
    CONSTRAINT fk_registration_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
