#!/usr/bin/env bash
# FloatBro Automated Quality Gauntlet (POSIX / Linux / CI)
set -euo pipefail

FULL_MODE=false
if [[ "${1:-}" == "--full" ]]; then
    FULL_MODE=true
fi

echo -e "\033[1;35m====================================================\033[0m"
echo -e "\033[1;35m  FloatBro Universal Clean Craftsmanship Gauntlet   \033[0m"
echo -e "\033[1;35m====================================================\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Pure JVM Unit Tests...\033[0m"
./gradlew testDebugUnitTest
echo -e "\033[1;32m>>> [GAUNTLET] Unit Tests PASSED\033[0m"

echo -e "\n\033[1;36m>>> [GAUNTLET] Running: Assemble Debug...\033[0m"
./gradlew assembleDebug
echo -e "\033[1;32m>>> [GAUNTLET] Assemble Debug PASSED\033[0m"

if [[ "$FULL_MODE" == "true" ]]; then
    echo -e "\n\033[1;36m>>> [GAUNTLET] Running: 16KB Page Size Verification...\033[0m"
    if [[ -f "./.github/scripts/check-16kb-alignment.sh" ]]; then
        bash ./.github/scripts/check-16kb-alignment.sh
    fi
fi

echo -e "\n\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m  ALL CRAFTSMANSHIP GATES PASSED (100% HEALTHY)     \033[0m"
echo -e "\033[1;32m====================================================\033[0m"
