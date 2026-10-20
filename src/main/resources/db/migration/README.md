# AloUTE Database Migration Scripts

This directory contains standalone, manual SQL migration scripts for database schema evolution. Flyway / Liquibase is not embedded in the runtime application.

## Scripts

1. **`h2_auth_and_registration_otp.sql`** (Engine: H2 Database)
   - Verified via automated unit test `H2MigrationScriptVerificationTest`.
   - Uses `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.
   - Non-destructively backfills `auth_version = 0`, `version = 0`, `verification_required = FALSE`.
   - Creates `password_reset_tokens` and `registration_otps` tables.

2. **`mysql8_auth_and_registration_otp.sql`** (Engine: MySQL 8.0+)
   - **EXECUTION STATUS: UNVERIFIED** against a live MySQL instance during automated tests (tested runtime uses H2).
   - MySQL 8.0 does not support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`; uses dynamic SQL preflight checks on `information_schema.COLUMNS`.
   - Must be executed manually by a DBA with appropriate DDL permissions before deploying to production.
