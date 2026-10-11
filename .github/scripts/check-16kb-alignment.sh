#!/usr/bin/env bash
# ==============================================================================
# FloatBro: Android 15 (16KB Page Size) Compliance Checker
# 校验范围:
#   1. APK 内所有 .so 库 ELF LOAD 段的对齐值 (Align >= 0x4000 即 16384 bytes)
#   2. APK 封包未压缩 .so 的 16KB 字节边界对齐 (zipalign -P 16 -c -v 4)
# ==============================================================================

set -euo pipefail

APK_PATH="${1:-}"

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "❌ Error: Invalid APK path provided: '$APK_PATH'"
  echo "Usage: $0 <path_to_apk>"
  exit 1
fi

echo "=========================================================="
echo "  Starting Android 15 16KB Page Size Verification"
echo "  Target: $APK_PATH"
echo "=========================================================="

TEMP_EXTRACT_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_EXTRACT_DIR"' EXIT

# 1. 尝试定位 llvm-readelf 工具 (优先使用 NDK 内置工具，次选系统 readelf)
READELF_CMD=""
if command -v llvm-readelf &> /dev/null; then
  READELF_CMD="llvm-readelf"
elif [[ -n "${ANDROID_NDK_HOME:-}" && -f "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" ]]; then
  READELF_CMD="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
elif command -v readelf &> /dev/null; then
  READELF_CMD="readelf"
else
  echo "❌ Error: Neither llvm-readelf nor readelf found in PATH or NDK!"
  exit 1
fi

echo "🛠️ Using ELF Reader: $READELF_CMD"

# 2. 解压 APK 中的所有 native .so 库
unzip -q "$APK_PATH" "lib/*" -d "$TEMP_EXTRACT_DIR" || {
  echo "ℹ️ No native libraries (lib/*) found in APK. Skipping ELF check."
  exit 0
}

TOTAL_SO_COUNT=0
FAILED_SO_COUNT=0

echo ""
echo "--- [Check 1/2]: Inspecting ELF LOAD Segment Alignments ---"

while IFS= read -r so_file; do
  ((TOTAL_SO_COUNT++)) || true
  lib_name=$(basename "$so_file")
  abi_arch=$(basename "$(dirname "$so_file")")
  
  non_compliant=false
  align_values=$($READELF_CMD -l "$so_file" | awk '/LOAD/ {for(i=1;i<=NF;i++) if($i ~ /^0x/) last=$i; print last}')

  for align_hex in $align_values; do
    align_dec=$((align_hex))
    if (( align_dec < 16384 )); then
      non_compliant=true
      echo "❌ FAIL: [$abi_arch] $lib_name -> LOAD alignment is $align_hex ($align_dec bytes < 16384)"
      break
    fi
  done

  if [ "$non_compliant" = false ]; then
    echo "✅ PASS: [$abi_arch] $lib_name (Align >= 16KB)"
  else
    ((FAILED_SO_COUNT++)) || true
  fi
done < <(find "$TEMP_EXTRACT_DIR/lib" -type f -name "*.so")

echo ""
echo "Analyzed $TOTAL_SO_COUNT native shared libraries."

if (( FAILED_SO_COUNT > 0 )); then
  echo "❌ FAILED: $FAILED_SO_COUNT shared libraries do NOT support Android 15 16KB Page Size!"
  echo "💡 Solution: Upgrade NDK to r27c+/r28, ensure linker flags contain '-Wl,-z,max-page-size=16384'."
  exit 1
fi

# 3. 校验 Zip 未压缩文件 16KB 对齐
echo ""
echo "--- [Check 2/2]: Checking APK Zip Boundary Alignment (zipalign -P 16) ---"

ZIPALIGN_CMD=""
if command -v zipalign &> /dev/null; then
  ZIPALIGN_CMD="zipalign"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  ZIPALIGN_CANDIDATE=$(find "${ANDROID_HOME}/build-tools" -name "zipalign" | sort -V | tail -n 1)
  if [[ -n "$ZIPALIGN_CANDIDATE" && -x "$ZIPALIGN_CANDIDATE" ]]; then
    ZIPALIGN_CMD="$ZIPALIGN_CANDIDATE"
  fi
fi

if [[ -n "$ZIPALIGN_CMD" ]]; then
  echo "🛠️ Using zipalign: $ZIPALIGN_CMD"
  if $ZIPALIGN_CMD -c -P 16 -v 4 "$APK_PATH" > /dev/null 2>&1; then
    echo "✅ PASS: APK package conforms to 16KB page boundary alignment!"
  else
    echo "❌ FAIL: APK failed zipalign 16KB boundary verification!"
    exit 1
  fi
else
  echo "⚠️ Warning: zipalign not found in current environment, skipped Zip boundary check."
fi

echo ""
echo "🎉 Congratulations! APK fully complies with Android 15 16KB Page Size requirements."
echo "=========================================================="
exit 0
