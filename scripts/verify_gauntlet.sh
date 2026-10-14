#!/usr/bin/env bash
# FloatBro Automated Quality Gauntlet (POSIX / Linux / CI)
set -eo pipefail

FULL_MODE=false
if [[ "${1:-}" == "--full" ]]; then
    FULL_MODE=true
fi

echo -e "\033[1;35m====================================================\033[0m"
echo -e "\033[1;35m  FloatBro Universal Clean Craftsmanship Gauntlet   \033[0m"
echo -e "\033[1;35m====================================================\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Code Style & Formatting (Spotless / ktlint)...\033[0m"
if ! ./gradlew spotlessCheck; then
    echo -e "\033[1;31m>>> [GAUNTLET] Spotless check failed!\033[0m"
    echo -e "\033[1;33m>>> [HINT] Run './gradlew spotlessApply' to automatically format your code.\033[0m"
    exit 1
fi
echo -e "\033[1;32m>>> [GAUNTLET] Spotless PASSED\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Static Code Analysis & Smells (Detekt)...\033[0m"
./gradlew detekt
echo -e "\033[1;32m>>> [GAUNTLET] Detekt PASSED\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Android Lint & Compose Lint Rules...\033[0m"
./gradlew lintDebug
echo -e "\033[1;32m>>> [GAUNTLET] Lint PASSED\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: JVM Unit Tests & Architecture Rules...\033[0m"
./gradlew testDebugUnitTest
echo -e "\033[1;32m>>> [GAUNTLET] Unit Tests PASSED\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Assemble Debug...\033[0m"
./gradlew assembleDebug
echo -e "\033[1;32m>>> [GAUNTLET] Assemble Debug PASSED\033[0m"

if [[ "$FULL_MODE" == "true" ]]; then
    echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Release R8 Minification Verification...\033[0m"
    ./gradlew :app:minifyReleaseWithR8
    echo -e "\033[1;32m>>> [GAUNTLET] Release R8 Minification PASSED\033[0m"

    echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Assemble Release APK...\033[0m"
    ./gradlew assembleRelease
    echo -e "\033[1;32m>>> [GAUNTLET] Assemble Release PASSED\033[0m"

    if [[ -f "./.github/scripts/check-16kb-alignment.sh" ]]; then
        echo -e "\n\033[1;36m>>> [GAUNTLET] Running: 16KB Page Size Verification...\033[0m"
        bash ./.github/scripts/check-16kb-alignment.sh
    fi
fi

echo -e "\n\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m  ALL CRAFTSMANSHIP GATES PASSED (100% HEALTHY)     \033[0m"
echo -e "\033[1;32m====================================================\033[0m"
