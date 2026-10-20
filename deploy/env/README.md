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
