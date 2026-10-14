# FloatBro Automated Quality Gauntlet (Windows PowerShell)
# Usage:
#   .\scripts\verify_gauntlet.ps1         # Standard Fast Pass (Quality Gates + Unit Tests + Assemble Debug)
#   .\scripts\verify_gauntlet.ps1 -Full   # Full Release Pass (+ Release R8 Minification & Assemble Release)

param(
    [switch]$Full
)

$ErrorActionPreference = "Stop"

function Invoke-GauntletStep {
    param(
        [string]$StepName,
        [scriptblock]$Action,
        [string]$Hint = ""
    )
    Write-Host "`n>>> [GAUNTLET] Running: $StepName..." -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        Write-Host ">>> [GAUNTLET] FAILED: $StepName (Exit code: $LASTEXITCODE)" -ForegroundColor Red
        if ($Hint) {
            Write-Host ">>> [HINT] $Hint" -ForegroundColor Yellow
        }
        exit $LASTEXITCODE
    }
    Write-Host ">>> [GAUNTLET] PASSED: $StepName" -ForegroundColor Green
}

Write-Host "====================================================" -ForegroundColor Magenta
Write-Host "  FloatBro Universal Clean Craftsmanship Gauntlet   " -ForegroundColor Magenta
Write-Host "====================================================" -ForegroundColor Magenta

# Step 1: Code Style & Formatting (Spotless / ktlint)
Invoke-GauntletStep "Code Style & Formatting (Spotless / ktlint)" {
    .\gradlew.bat spotlessCheck
} "Run '.\gradlew.bat spotlessApply' to automatically format your code."

# Step 2: Kotlin AST Smells & Complexity (Detekt)
Invoke-GauntletStep "Static Code Analysis & Smells (Detekt)" {
    .\gradlew.bat detekt
}

# Step 3: Android Lint & Compose Lint Rules (Android Lint)
Invoke-GauntletStep "Android Lint & Compose Lint Rules" {
    .\gradlew.bat lintDebug
}

# Step 4: Pure JVM Unit Tests & ArchUnit Rules (:ad-filter, :adblock-client, :core:webview, :app)
Invoke-GauntletStep "JVM Unit Tests & Architecture Rules" {
    .\gradlew.bat testDebugUnitTest
}

# Step 5: Check Android Debug Assemble
Invoke-GauntletStep "Assemble Debug APK" {
    .\gradlew.bat assembleDebug
}

# Step 6: Full Mode Verification (R8 Minification & Release Assemble)
if ($Full) {
    Write-Host "`n>>> [GAUNTLET] Running Full Mode Verification..." -ForegroundColor Yellow
    Invoke-GauntletStep "Release R8 Minification Verification" {
        .\gradlew.bat :app:minifyReleaseWithR8
    }
    Invoke-GauntletStep "Assemble Release APK" {
        .\gradlew.bat assembleRelease
    }
}

Write-Host "`n====================================================" -ForegroundColor Green
Write-Host "  ALL CRAFTSMANSHIP GATES PASSED (100% HEALTHY)     " -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
