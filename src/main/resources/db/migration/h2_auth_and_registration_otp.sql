-- =========================================================================
-- AloUTE Safe Migration: H2 DDL for Auth Prerequisites & Registration OTP
-- Target Engine: H2 In-Memory / Dev / Test Runtime
-- H2 natively supports ALTER TABLE ... ADD COLUMN IF NOT EXISTS syntax.
-- =========================================================================

-- 1. Prerequisites on users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_required BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Non-destructive legacy account compatibility
UPDATE users SET auth_version = 0 WHERE auth_version IS NULL;
UPDATE users SET version = 0 WHERE version IS NULL;
UPDATE users SET verification_required = FALSE WHERE verification_required IS NULL;

-- 3. Table: password_reset_tokens
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_password_reset_tokens_user UNIQUE (user_id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 4. Table: registration_otps
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
