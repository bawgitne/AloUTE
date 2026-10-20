-- =========================================================================
-- AloUTE Safe Migration: MySQL 8.0 DDL for Auth Prerequisites & Registration OTP
-- Target Engine: MySQL 8.0+
-- MySQL 8.0 does NOT support ALTER TABLE ... ADD COLUMN IF NOT EXISTS syntax.
-- Dynamic SQL / preflight information_schema checks are utilized below.
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. Prerequisites on users table: auth_version, version, verification_required
-- -------------------------------------------------------------------------

-- 1.1 Column: auth_version (Prerequisite for session revocation & password reset)
SET @dbname = DATABASE();
SET @tablename = 'users';
SET @columnname = 'auth_version';
SET @precheck = (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @dbname
                   AND TABLE_NAME = @tablename
                   AND COLUMN_NAME = @columnname);
SET @sql = IF(@precheck = 0,
              'ALTER TABLE users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0',
              'SELECT "Column auth_version already exists in users" AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 Column: version (Prerequisite for optimistic locking)
SET @columnname = 'version';
SET @precheck = (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @dbname
                   AND TABLE_NAME = @tablename
                   AND COLUMN_NAME = @columnname);
SET @sql = IF(@precheck = 0,
              'ALTER TABLE users ADD COLUMN version BIGINT DEFAULT 0',
              'SELECT "Column version already exists in users" AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 Column: verification_required (Registration OTP gate)
SET @columnname = 'verification_required';
SET @precheck = (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @dbname
                   AND TABLE_NAME = @tablename
                   AND COLUMN_NAME = @columnname);
SET @sql = IF(@precheck = 0,
              'ALTER TABLE users ADD COLUMN verification_required BOOLEAN NOT NULL DEFAULT FALSE',
              'SELECT "Column verification_required already exists in users" AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------
-- 2. Backfill & Legacy Account Non-Destructive Rollout Policy
-- -------------------------------------------------------------------------
-- Existing accounts have verification_required = FALSE and are NOT locked out,
-- preserving full access for existing demo, seed, and registered accounts.
UPDATE users SET auth_version = 0 WHERE auth_version IS NULL;
UPDATE users SET version = 0 WHERE version IS NULL;
UPDATE users SET verification_required = FALSE WHERE verification_required IS NULL;

-- -------------------------------------------------------------------------
-- 3. Table: password_reset_tokens (Prerequisite for recovery flow)
-- -------------------------------------------------------------------------
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

-- -------------------------------------------------------------------------
-- 4. Table: registration_otps (Registration Email OTP Verification)
-- -------------------------------------------------------------------------
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_registration_otps_user UNIQUE (user_id),
    CONSTRAINT fk_registration_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
