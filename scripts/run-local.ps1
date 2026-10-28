<#
.SYNOPSIS
    Starts AloUTE application locally using literal .env configuration.
.DESCRIPTION
    Loads .env without eval or code execution, preserves existing environment
    variables, enforces secrets safety, and runs current Spring Boot source via Maven.
.PARAMETER EnvFile
    Path to .env file (default: repo root .env).
.PARAMETER Port
    Optional port override (e.g., 18138 for QA).
.PARAMETER Profile
    Active Spring profile (default: dev).
.PARAMETER NoRun
    Only load environment and validate configuration, do not launch Spring Boot.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$EnvFile,

    [Parameter()]
    [int]$Port = 0,

    [Parameter()]
    [string]$Profile = "dev",

    [Parameter()]
    [switch]$NoRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path

if (-not $EnvFile) {
    $EnvFile = Join-Path $RepoRoot ".env"
}

# 1. Parse .env literally without eval / Invoke-Expression
if (Test-Path $EnvFile) {
    Write-Host "[AloUTE] Loading environment from $(Split-Path $EnvFile -Leaf)..."
    $lines = [System.IO.File]::ReadAllLines($EnvFile)
    $lineNum = 0
    foreach ($rawLine in $lines) {
        $lineNum++
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or $line.StartsWith(";")) {
            continue
        }

        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $key = $matches[1]
            $val = $matches[2].Trim()

            # Strip surrounding matching single or double quotes
            if (($val.StartsWith('"') -and $val.EndsWith('"') -and $val.Length -ge 2) -or
                ($val.StartsWith("'") -and $val.EndsWith("'") -and $val.Length -ge 2)) {
                $val = $val.Substring(1, $val.Length - 2)
            }

            # Precedence rule: preserve existing non-empty process environment
            $existing = [System.Environment]::GetEnvironmentVariable($key, [System.EnvironmentVariableTarget]::Process)
            if ([string]::IsNullOrEmpty($existing)) {
                [System.Environment]::SetEnvironmentVariable($key, $val, [System.EnvironmentVariableTarget]::Process)
            }
        } else {
            # Reject invalid lines without echoing contents/values
            Write-Warning "[AloUTE] Skipping malformed line $lineNum in environment file (content redacted)."
        }
    }
} else {
    Write-Warning "[AloUTE] No .env file found at $EnvFile; relying on process environment."
}

# 2. Port and Profile configuration
if ($Port -gt 0) {
    [System.Environment]::SetEnvironmentVariable("PORT", $Port.ToString(), [System.EnvironmentVariableTarget]::Process)
    [System.Environment]::SetEnvironmentVariable("SERVER_PORT", $Port.ToString(), [System.EnvironmentVariableTarget]::Process)
    Write-Host "[AloUTE] Port configured to $Port."
}

if ($PSBoundParameters.ContainsKey('Profile')) {
    [System.Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", $Profile, [System.EnvironmentVariableTarget]::Process)
} elseif (-not [System.Environment]::GetEnvironmentVariable("SPRING_PROFILES_ACTIVE", [System.EnvironmentVariableTarget]::Process)) {
    [System.Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", $Profile, [System.EnvironmentVariableTarget]::Process)
}

# 3. Fail-closed secret checks when running in production profile
$activeProfile = [System.Environment]::GetEnvironmentVariable("SPRING_PROFILES_ACTIVE", [System.EnvironmentVariableTarget]::Process)
if ($activeProfile -match 'prod') {
    $jwtSecret = [System.Environment]::GetEnvironmentVariable("ALOUTE_JWT_SECRET", [System.EnvironmentVariableTarget]::Process)
    $otpSecret = [System.Environment]::GetEnvironmentVariable("ALOUTE_REGISTRATION_OTP_SECRET", [System.EnvironmentVariableTarget]::Process)
    if ([string]::IsNullOrWhiteSpace($jwtSecret) -or [string]::IsNullOrWhiteSpace($otpSecret)) {
        Write-Error "[AloUTE] Security check failed: ALOUTE_JWT_SECRET and ALOUTE_REGISTRATION_OTP_SECRET are required in production profile."
        exit 1
    }
}

if ($NoRun) {
    Write-Host "[AloUTE] Environment validated successfully (NoRun specified)."
    exit 0
}

# 4. Resolve Maven executable (wrapper preferred)
Set-Location $RepoRoot
$mvnCmd = $null
if (Test-Path "$RepoRoot\mvnw.cmd") {
    $mvnCmd = "$RepoRoot\mvnw.cmd"
} elseif (Test-Path "$RepoRoot\mvnw") {
    $mvnCmd = "$RepoRoot\mvnw"
} else {
    $mvnResolved = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnResolved) {
        $mvnCmd = "mvn"
    } elseif ($env:LOCALAPPDATA -and (Test-Path "$env:LOCALAPPDATA\Temp\maven-bin\apache-maven-3.9.9\bin\mvn.cmd")) {
        $mvnCmd = "$env:LOCALAPPDATA\Temp\maven-bin\apache-maven-3.9.9\bin\mvn.cmd"
    }
}

if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot") {
    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
}
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin")) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if (-not $mvnCmd) {
    Write-Error "[AloUTE] Neither Maven wrapper (mvnw) nor system 'mvn' was found in PATH."
    exit 1
}

Write-Host "[AloUTE] Launching Spring Boot application from source via $mvnCmd (profile: $activeProfile)..."
& $mvnCmd spring-boot:run
if ($LASTEXITCODE -ne 0) {
    Write-Error "[AloUTE] Application run exited with code $LASTEXITCODE."
    exit $LASTEXITCODE
}
