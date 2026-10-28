# AloUTE Environment and SMTP Configuration Guide

## 1. Quick Setup (Local Development)

1. Initial Environment Setup:
   If `.env` already exists in the repository root, existing configuration, credentials, and secrets are preserved without modification. Missing keys should be configured manually rather than rotating secrets.

   For first-time setup (when `.env` does not yet exist), execute the following PowerShell command in the repository root to create `.env` atomically from `deploy/env/.env.example` and generate cryptographically secure local secrets without echoing them to stdout:
   ```powershell
   $envPath = Join-Path (Get-Location) ".env"
   if (Test-Path -LiteralPath $envPath) {
       Write-Host "[AloUTE] .env already exists. Preserving existing configuration and secrets without changes."
       Write-Host "[AloUTE] If any required keys are missing, add them manually without rotating existing secrets."
   } else {
       $template = Get-Content "deploy/env/.env.example" -Raw
       $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
       $b1 = New-Object byte[] 32; $rng.GetBytes($b1); $jwt = [Convert]::ToBase64String($b1)
       $b2 = New-Object byte[] 32; $rng.GetBytes($b2); $otp = [Convert]::ToBase64String($b2)
       $content = $template -replace '(?m)^ALOUTE_JWT_SECRET=.*$', "ALOUTE_JWT_SECRET=$jwt" `
                            -replace '(?m)^ALOUTE_REGISTRATION_OTP_SECRET=.*$', "ALOUTE_REGISTRATION_OTP_SECRET=$otp"
       $fs = [System.IO.File]::Open($envPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
       try {
           $bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
           $fs.Write($bytes, 0, $bytes.Length)
       } finally {
           $fs.Close()
       }
       Write-Host "[AloUTE] Created new .env with cryptographically secure local secrets."
   }
   ```

2. Run the application via the safe literal `.env` loader:
   ```powershell
   .\scripts\run-local.ps1
   ```
   **Overrides:**
   - To specify a custom port: `.\scripts\run-local.ps1 -Port 18138`
   - To specify a custom profile: `.\scripts\run-local.ps1 -Profile prod`
   - Command-line parameters take precedence over `.env` file settings.

## 2. SMTP Delivery Configuration

- **Local Development / QA**:
  - Point `ALOUTE_MAIL_HOST=localhost` and `ALOUTE_MAIL_PORT=2525` to a local test SMTP sink (e.g., Mailpit or test sink).
  - If `ALOUTE_MAIL_HOST` is left blank, email delivery is simulated in runner logs without failing.
- **Production / External Gmail**:
  - `ALOUTE_MAIL_HOST=smtp.gmail.com`
  - `ALOUTE_MAIL_PORT=587`
  - `ALOUTE_MAIL_USERNAME=your-account@gmail.com`
  - `ALOUTE_MAIL_PASSWORD=your-16-character-app-password`
  - `ALOUTE_MAIL_FROM=your-account@gmail.com` (must correspond to the authenticated account)
  - *Note:* No external Gmail credentials are bundled or assumed available in runner environments. External delivery tests are marked BLOCKED when private credentials are not configured.

## 3. Security Invariants

- Never commit `.env` or echo secrets in console output, logs, or command-line arguments.
- `scripts/run-local.ps1` parses `.env` literally line-by-line without executing code (`eval` / `Invoke-Expression`).
- If launched in `prod` profile, missing `ALOUTE_JWT_SECRET` or `ALOUTE_REGISTRATION_OTP_SECRET` fails closed immediately.

## 4. Registration OTP on Cloud Run

Registration and resend attempts commit the account/OTP first, then finish the SMTP
attempt in the request thread before returning HTTP. This supports Cloud Run
request-based billing without relying on CPU after the response. Delivery failures
leave the committed OTP available for recovery through resend after its 60-second
cooldown. A successful API response is not a guarantee of inbox delivery.

Keep request-based billing and minimum instances at 0 to allow scaling to zero.
If instance-based billing was enabled during troubleshooting, switch back with:

```bash
gcloud run services update aloute-app \
  --project=cnpm-f1a39 \
  --region=asia-southeast1 \
  --cpu-throttling
```

Set mail variables on the Cloud Run service; exporting them in Cloud Shell alone
does not configure the deployed container. Gmail supports port 465 (implicit TLS)
and 587 (STARTTLS); set `ALOUTE_MAIL_FROM` to the authenticated Gmail account or an
authorized sender address.

After deployment, request an OTP or wait 60 seconds and use **Resend**. Opening the
verification page alone does not send email. Inspect container logs for
`Registration OTP notification dispatched` or `Failed to deliver external email`.
SMTP acceptance does not guarantee inbox placement; check the spam folder as well.

The password reset flow still uses a background executor and is outside this
registration OTP fix.

## 5. Two-Factor Master Key (`ALOUTE_TWO_FACTOR_MASTER_KEY`)

`TwoFactorEncryptionService` encrypts stored TOTP secrets with AES-GCM keyed by
this value (SHA-256 derived). Behavior enforced by code, not convention:

- **prod profile refuses to start** when the key is missing, shorter than 16
  characters, or still the public dev default (BL-07 startup guard, armed by
  `aloute.production-mode`, which `application-prod.yml` sets to true by default).
- **dev keeps the built-in default** so local H2 databases stay decryptable.

### Rotation policy (no old-key re-encryption support)

The service holds exactly one key; it cannot decrypt material made with a
previous key. Therefore **rotating the key invalidates every stored TOTP
secret**. There is intentionally no bulk migration endpoint and 2FA is never
silently removed by the application. After a rotation:

1. Totp login: the old secret no longer decrypts. The user gets the normal
   `AUTH_2FA_INVALID_CODE` 401 and can use the **email OTP fallback** or a
   **backup code** (neither depends on the master key) to finish signing in.
2. Self-service recovery: from Settings, disable 2FA with password + backup
   code or email OTP, then scan a fresh QR (setup/enable encrypt under the new
   key). All surfaces answer 4xx, never 500.
3. Account support: only if a user has neither backup codes nor a reachable
   mailbox may an operator reset them through the normal forgot-password flow
   (which proves email ownership) and then re-enable 2FA. Do not clear
   `two_factor_secret` in the database directly; use the disable flow so
   `authVersion` bumps and stale sessions die.

The same rule applies to a half-finished setup: a temp secret QR-issued under
the old key answers `AUTH_2FA_SETUP_STALE` (400) and the user re-scans the QR.
