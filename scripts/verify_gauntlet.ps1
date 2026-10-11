# FloatBro Automated Quality Gauntlet (Windows PowerShell)
# Usage:
#   .\scripts\verify_gauntlet.ps1         # Standard Fast Pass (Unit Tests + Assemble Debug)
#   .\scripts\verify_gauntlet.ps1 -Full   # Full Release Pass (Unit Tests + Assemble Release)

param(
    [switch]$Full
)

$ErrorActionPreference = "Stop"

function Invoke-GauntletStep {
    param(
        [string]$StepName,
        [scriptblock]$Action
    )
    Write-Host "`n>>> [GAUNTLET] Running: $StepName..." -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        Write-Host ">>> [GAUNTLET] FAILED: $StepName (Exit code: $LASTEXITCODE)" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host ">>> [GAUNTLET] PASSED: $StepName" -ForegroundColor Green
}

Write-Host "====================================================" -ForegroundColor Magenta
Write-Host "  FloatBro Universal Clean Craftsmanship Gauntlet   " -ForegroundColor Magenta
Write-Host "====================================================" -ForegroundColor Magenta

# Step 1: Run All Pure JVM Unit Tests
Invoke-GauntletStep "Pure JVM Unit Tests (:ad-filter, :core:webview, :app)" {
    .\gradlew.bat testDebugUnitTest
}

# Step 2: Check Android Debug Assemble
Invoke-GauntletStep "Assemble Debug APK" {
    .\gradlew.bat assembleDebug
}

# Step 3: Full Mode Verification
if ($Full) {
    Write-Host "`n>>> [GAUNTLET] Running Full Mode Verification..." -ForegroundColor Yellow
    Invoke-GauntletStep "Assemble Release APK" {
        .\gradlew.bat assembleRelease
    }
}

Write-Host "`n====================================================" -ForegroundColor Green
Write-Host "  ALL CRAFTSMANSHIP GATES PASSED (100% HEALTHY)     " -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
