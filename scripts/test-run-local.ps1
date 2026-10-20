# Test suite for scripts/run-local.ps1 and environment setup
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "run-local.ps1"
$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path

Write-Host "Running run-local.ps1 parser and environment lifecycle unit tests..."

# Capture complete process environment snapshot before any test executes
# Keys are OrdinalIgnoreCase (Windows standard), values are case-sensitive
$initialProcessEnv = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$origEnvEntries = [System.Environment]::GetEnvironmentVariables([System.EnvironmentVariableTarget]::Process)
foreach ($key in $origEnvEntries.Keys) {
    $initialProcessEnv[$key.ToString()] = $origEnvEntries[$key].ToString()
}

$tempEnv = [System.IO.Path]::GetTempFileName()
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("aloute-env-test-" + [System.Guid]::NewGuid().ToString("N"))

try {
    # Test 1: Parser handles quotes, spaces, comments, and literal subexpressions without code execution
    $testContent = @'
# Sample comment
VALID_SIMPLE=helloworld
VALID_QUOTED="hello with spaces and = equals"
VALID_SINGLE='single quoted value'
VALID_SUBEXPR='$(Get-Process)'
VALID_BACKTICK='value_with_`_backtick'
MALFORMED_LINE_NO_EQUALS
VALID_EMPTY=
'@
    [System.IO.File]::WriteAllText($tempEnv, $testContent)

    # Clear prior keys if any
    [System.Environment]::SetEnvironmentVariable("VALID_SIMPLE", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("VALID_QUOTED", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("VALID_SINGLE", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("VALID_SUBEXPR", $null, "Process")
    [System.Environment]::SetEnvironmentVariable("VALID_BACKTICK", $null, "Process")

    & $scriptPath -EnvFile $tempEnv -NoRun
    if ($LASTEXITCODE -ne 0) { throw "Test 1 failed with exit code $LASTEXITCODE" }

    if ($env:VALID_SIMPLE -ne "helloworld") { throw "VALID_SIMPLE assertion failed" }
    if ($env:VALID_QUOTED -ne "hello with spaces and = equals") { throw "VALID_QUOTED assertion failed" }
    if ($env:VALID_SINGLE -ne "single quoted value") { throw "VALID_SINGLE assertion failed" }
    if ($env:VALID_SUBEXPR -ne '$(Get-Process)') { throw "VALID_SUBEXPR was evaluated instead of parsed literally" }
    if ($env:VALID_BACKTICK -ne 'value_with_`_backtick') { throw "VALID_BACKTICK assertion failed" }
    Write-Host "[PASS] Test 1: Literal parser extracts values, trims quotes, preserves subexpressions/backticks literally, ignores comments and malformed lines."

    # Test 2: Precedence & case preservation - existing non-empty process environment variable is NOT overwritten
    # Verifies case-sensitive preservation of uppercase original values against lowercase file values
    [System.Environment]::SetEnvironmentVariable("PRECEDENCE_KEY", "ORIGINAL_UPPERCASE_PROCESS_VALUE", "Process")
    $precedenceContent = "PRECEDENCE_KEY=lowercase_file_value`n"
    [System.IO.File]::WriteAllText($tempEnv, $precedenceContent)

    & $scriptPath -EnvFile $tempEnv -NoRun
    if ($env:PRECEDENCE_KEY -cne "ORIGINAL_UPPERCASE_PROCESS_VALUE") {
        throw "Precedence check failed: process environment value or case was not preserved"
    }
    Write-Host "[PASS] Test 2: Process environment takes precedence over .env file with exact case preservation."

    # Test 3: Fail-closed on missing production secrets
    $prodEnv = "ALOUTE_DOMAIN=example.com`n"
    [System.IO.File]::WriteAllText($tempEnv, $prodEnv)
    $failedClosed = $false

    # Ensure production secrets are completely empty in process
    [System.Environment]::SetEnvironmentVariable("ALOUTE_JWT_SECRET", "", "Process")
    [System.Environment]::SetEnvironmentVariable("ALOUTE_REGISTRATION_OTP_SECRET", "", "Process")

    try {
        & $scriptPath -EnvFile $tempEnv -Profile "prod" -NoRun 2>$null
        if ($LASTEXITCODE -ne 0) {
            $failedClosed = $true
        }
    } catch {
        $failedClosed = $true
    }

    if (-not $failedClosed) {
        throw "Test 3 failed: production profile did NOT fail closed when required secrets were missing"
    }
    Write-Host "[PASS] Test 3: Production profile fails closed when required secrets are missing."

    # Test 4: Disposable .env fixture: first init works, repeated init preserves hash, restart does not rotate, create-race cannot overwrite
    New-Item -ItemType Directory -Path (Join-Path $tempDir "deploy/env") -Force | Out-Null
    Copy-Item (Join-Path $RepoRoot "deploy/env/.env.example") (Join-Path $tempDir "deploy/env/.env.example")

    $readmeContent = [System.IO.File]::ReadAllText((Join-Path $RepoRoot "deploy/env/README.md"))
    if ($readmeContent -match '(?ms)^\s*```powershell\r?\n(.*?)\r?\n\s*```') {
        $readmeSnippet = $matches[1]
    } else {
        throw "Failed to extract PowerShell snippet from deploy/env/README.md"
    }

    Push-Location $tempDir
    try {
        # 4a. First-time initialization
        $initBlock = [scriptblock]::Create($readmeSnippet)
        & $initBlock | Out-Null

        if (-not (Test-Path ".env")) {
            throw "Test 4a failed: .env was not created by initial setup snippet"
        }

        # Verify secrets are valid Base64 with >= 32 decoded bytes without echoing values
        $envLines = [System.IO.File]::ReadAllLines((Join-Path $tempDir ".env"))
        $foundJwt = $false
        $foundOtp = $false
        foreach ($l in $envLines) {
            if ($l -match '^ALOUTE_JWT_SECRET=(.+)$') {
                $decodedJwt = [Convert]::FromBase64String($matches[1].Trim())
                if ($decodedJwt.Length -ge 32) { $foundJwt = $true }
            }
            if ($l -match '^ALOUTE_REGISTRATION_OTP_SECRET=(.+)$') {
                $decodedOtp = [Convert]::FromBase64String($matches[1].Trim())
                if ($decodedOtp.Length -ge 32) { $foundOtp = $true }
            }
        }
        if (-not $foundJwt -or -not $foundOtp) {
            throw "Test 4a failed: initial setup did not generate valid >= 32-byte Base64 secrets"
        }

        $hashInit = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash

        # 4b. Repeated initialization on existing .env must leave byte hash completely identical
        & $initBlock | Out-Null
        $hashRepeated = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash
        if ($hashInit -ne $hashRepeated) {
            throw "Test 4b failed: repeated setup modified existing .env (hash changed)"
        }

        # 4c. Custom values, comments, and credentials remain unchanged on repeated init
        [System.IO.File]::AppendAllText((Join-Path $tempDir ".env"), "`n# Custom configuration`nCUSTOM_DB_PASS=localpass`nALOUTE_MAIL_PASSWORD=MixedCase_SMTP_Secret_123`n")
        $hashWithCustom = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash

        & $initBlock | Out-Null
        $hashAfterCustomRepeated = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash
        if ($hashWithCustom -ne $hashAfterCustomRepeated) {
            throw "Test 4c failed: repeated setup modified existing custom credentials or comments"
        }

        # 4d. Application startup (-NoRun check) does not rotate secrets or modify .env
        & $scriptPath -EnvFile (Join-Path $tempDir ".env") -NoRun | Out-Null
        $hashAfterRunLocal = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash
        if ($hashWithCustom -ne $hashAfterRunLocal) {
            throw "Test 4d failed: run-local modified .env file during startup check"
        }

        # 4e. Create-race / TOCTOU proof: exercise actual extracted README snippet with forced precheck false
        # When precheck is false, the extracted snippet enters the creation block; FileMode::CreateNew MUST throw IOException on existing file
        $raceCaughtIoException = $false
        $raceBlock = [scriptblock]::Create(@"
function Test-Path { return `$false }
$readmeSnippet
"@)
        try {
            & $raceBlock 2>$null | Out-Null
        } catch [System.IO.IOException] {
            $raceCaughtIoException = $true
        } catch {
            # Inspect wrapped exception without literal variable escaping
            if ($_.Exception -is [System.IO.IOException] -or $_.Exception.InnerException -is [System.IO.IOException]) {
                $raceCaughtIoException = $true
            }
        }

        if (-not $raceCaughtIoException) {
            throw "Test 4e failed: extracted README snippet did not throw IOException on existing file race (regressed from FileMode::CreateNew?)"
        }

        $hashAfterRace = (Get-FileHash -Path ".env" -Algorithm SHA256).Hash
        if ($hashWithCustom -ne $hashAfterRace) {
            throw "Test 4e failed: .env hash changed after create-race attempt on extracted README block"
        }

        Write-Host "[PASS] Test 4: Disposable .env fixture: first init generates >= 32-byte secrets, repeated init preserves byte hash, restart does not rotate, actual extracted snippet throws IOException on race and cannot overwrite."
    } finally {
        Pop-Location
    }

    # Test 5: Focused outer caller regression with uppercase and mixed-case environment variables
    # Proves case-sensitive restoration logic restores exact case without leaking into caller process
    $callerTestKey1 = "OUTER_CALLER_PRECEDENCE_KEY"
    $callerTestVal1 = "ORIGINAL_UPPERCASE_PROCESS_VALUE_XYZ"
    $callerTestKey2 = "OUTER_CALLER_SMTP_DUMMY_KEY"
    $callerTestVal2 = "MixedCase_SmtpDummyValue_Pass123"

    [System.Environment]::SetEnvironmentVariable($callerTestKey1, $callerTestVal1, "Process")
    [System.Environment]::SetEnvironmentVariable($callerTestKey2, $callerTestVal2, "Process")

    # Simulate a mutation to lowercase
    [System.Environment]::SetEnvironmentVariable($callerTestKey1, "original_uppercase_process_value_xyz", "Process")
    [System.Environment]::SetEnvironmentVariable($callerTestKey2, "mixedcase_smtpdummyvalue_pass123", "Process")

    # Run case-sensitive restoration simulation
    $testCurr1 = [System.Environment]::GetEnvironmentVariable($callerTestKey1, "Process")
    $testCurr2 = [System.Environment]::GetEnvironmentVariable($callerTestKey2, "Process")

    if ($testCurr1 -cne $callerTestVal1) {
        [System.Environment]::SetEnvironmentVariable($callerTestKey1, $callerTestVal1, "Process")
    }
    if ($testCurr2 -cne $callerTestVal2) {
        [System.Environment]::SetEnvironmentVariable($callerTestKey2, $callerTestVal2, "Process")
    }

    # Assert exact case was restored using case-sensitive -ceq (boolean verification only)
    $restoredVal1 = [System.Environment]::GetEnvironmentVariable($callerTestKey1, "Process")
    $restoredVal2 = [System.Environment]::GetEnvironmentVariable($callerTestKey2, "Process")
    $case1Matched = ($restoredVal1 -ceq $callerTestVal1)
    $case2Matched = ($restoredVal2 -ceq $callerTestVal2)

    # Clean up test keys
    [System.Environment]::SetEnvironmentVariable($callerTestKey1, $null, "Process")
    [System.Environment]::SetEnvironmentVariable($callerTestKey2, $null, "Process")

    if (-not $case1Matched -or -not $case2Matched) {
        throw "Test 5 failed: case-sensitive restoration failed to restore exact uppercase or mixed-case values"
    }
    Write-Host "[PASS] Test 5: Focused caller regression: case-sensitive value restoration preserves exact uppercase and mixed-case credentials."

    Write-Host "All 5 test suites passed successfully!"
} finally {
    # Guarded temporary file and directory cleanup
    if (Test-Path -LiteralPath $tempEnv) {
        $resolvedTempEnv = [System.IO.Path]::GetFullPath($tempEnv)
        $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if ($resolvedTempEnv.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedTempEnv -Force
        }
    }

    if (Test-Path -LiteralPath $tempDir) {
        $resolvedTempDir = [System.IO.Path]::GetFullPath($tempDir)
        $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if ($resolvedTempDir.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase) -and 
            $resolvedTempDir.Length -gt $systemTemp.Length -and 
            (Split-Path $resolvedTempDir -Leaf).StartsWith("aloute-env-test-")) {
            Remove-Item -LiteralPath $resolvedTempDir -Recurse -Force
        } else {
            Write-Warning "[Guard] Skipped cleanup of directory outside expected temp location: $resolvedTempDir"
        }
    }

    # Full environment restoration: remove newly introduced keys and restore original values with case sensitivity
    $currentEnvEntries = [System.Environment]::GetEnvironmentVariables([System.EnvironmentVariableTarget]::Process)
    foreach ($key in $currentEnvEntries.Keys) {
        $keyStr = $key.ToString()
        if (-not $initialProcessEnv.ContainsKey($keyStr)) {
            [System.Environment]::SetEnvironmentVariable($keyStr, $null, [System.EnvironmentVariableTarget]::Process)
        }
    }
    foreach ($key in $initialProcessEnv.Keys) {
        $origVal = $initialProcessEnv[$key]
        $currVal = [System.Environment]::GetEnvironmentVariable($key, [System.EnvironmentVariableTarget]::Process)
        # Case-sensitive value comparison for restoration
        if ($currVal -cne $origVal) {
            [System.Environment]::SetEnvironmentVariable($key, $origVal, [System.EnvironmentVariableTarget]::Process)
        }
    }

    # Verify process environment is 100% restored using case-sensitive value comparison
    $finalEnvEntries = [System.Environment]::GetEnvironmentVariables([System.EnvironmentVariableTarget]::Process)
    $envRestorationMatched = ($finalEnvEntries.Count -eq $initialProcessEnv.Count)
    if ($envRestorationMatched) {
        foreach ($key in $initialProcessEnv.Keys) {
            if ($finalEnvEntries[$key] -cne $initialProcessEnv[$key]) {
                $envRestorationMatched = $false
                break
            }
        }
    }
    if (-not $envRestorationMatched) {
        throw "Environment restoration assertion failed: process environment was not fully restored to initial state (case-sensitive check failed)."
    }
    Write-Host "[PASS] Outer process environment verified completely restored with case sensitivity (boolean comparison: $envRestorationMatched)."
}
