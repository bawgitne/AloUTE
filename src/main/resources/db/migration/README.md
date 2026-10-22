# AloUTE Database Migration Scripts

This directory contains standalone, manual SQL migration scripts for database schema evolution. Flyway / Liquibase is not embedded in the runtime application.

## Scripts

1. **`h2_auth_and_registration_otp.sql`** (Engine: H2 Database)
   - Verified via automated unit test `H2MigrationScriptVerificationTest`.
   - Uses `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.
   - Non-destructively backfills `auth_version = 0`, `version = 0`, `verification_required = FALSE`.
   - Creates `password_reset_tokens` and `registration_otps` tables.

2. **`mysql8_auth_and_registration_otp.sql`** (Engine: MySQL 8.0+)
   - **EXECUTION STATUS: BLOCKED (ENGINE UNAVAILABLE)** on test host; real MySQL 8 engine not present on local machine.
   - MySQL 8.0 does not support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`; uses dynamic SQL preflight checks on `information_schema.COLUMNS`.
   - Must be executed manually by a DBA with appropriate DDL permissions before deploying to production.

3. **`h2_revoked_tokens.sql`** (Engine: H2 Database)
   - Verified via automated integration tests `H2MigrationScriptVerificationTest` and `DurableLogoutRevocationIntegrationTest`.
   - Creates `revoked_tokens` table and index on `expires_at`.

4. **`mysql8_revoked_tokens.sql`** (Engine: MySQL 8.0+)
   - **EXECUTION STATUS: BLOCKED (ENGINE UNAVAILABLE)** on test host; real MySQL 8 engine not present on local machine.
   - Creates `revoked_tokens` InnoDB table with `token_hash` unique key and `expires_at` index.
   - Must be executed manually by a DBA with appropriate DDL permissions before deploying to production.
