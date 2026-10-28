-- BL-03 login throttle: per-account failed-attempt counter and lockout window.
-- Engine: MySQL 8.0+ (no ADD COLUMN IF NOT EXISTS; run each statement once).
-- ddl-auto:update applies the same columns automatically on dev/H2 environments.
-- Must be executed manually by a DBA before deploying the login-lockout build.

ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP NULL;
