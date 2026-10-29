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

10. **`h2_follow_requests.sql` / `mysql8_follow_requests.sql`** (BL-05 private-account follow requests, issue #92, 2026-10-29)
    - Creates the `follow_requests` InnoDB table (requester/target pair unique key,
      target+status index, FKs to `users`). Additive only; no existing rows touched.
    - H2 variant verified by `H2MigrationScriptVerificationTest`; MySQL 8 variant is
      proven on a disposable MySQL 8 by the `MySQL 8 Migration Proof` workflow.
    - Must be executed manually by a DBA before deploying the BL-05 build.

11. **`h2_backfill_post_counters.sql` / `mysql8_backfill_post_counters.sql`** (BL-15 lifetime post counters, issue #92, 2026-10-29)
    - **Manual one-off ops script** (never run by automation): recomputes
      `profiles.posts_count` and `communities.post_count` from the lifetime root-post
      definition. Idempotent by construction. Contains backup, pre/post checks and
      rollback instructions in the script header.
    - H2 twin executed against the real schema by
      `ProfilePostCounterIntegrationTest#backfillScriptRecomputesAndIsIdempotent`.
    - Production backfill is a deployment gate; stale counters persist until an
      operator runs it.

9. **`h2_login_lockout.sql` / `mysql8_login_lockout.sql`** (BL-03 login throttle, 2026-10-28)
   - Adds `users.failed_login_attempts` (NOT NULL DEFAULT 0) and `users.locked_until` (NULL).
   - H2 variant verified via `H2MigrationScriptVerificationTest`; MySQL 8 variant must be run
     manually by a DBA before deploying the login-lockout build (no ADD COLUMN IF NOT EXISTS).
   - Non-destructive: additive columns only; existing rows keep working (0 attempts, unlocked).

10. **`h2_used_two_factor_tickets.sql` / `mysql8_used_two_factor_tickets.sql`** (BL-08 2FA hardening, 2026-10-28)
    - Creates `used_two_factor_tickets` (one-time store for consumed 2FA login tickets,
      unique `ticket_hash`, housekept after 10 minutes).
    - H2 variant verified via `H2MigrationScriptVerificationTest`; MySQL 8 variant must be
      executed manually by a DBA before deploying the 2FA-hardening build.
