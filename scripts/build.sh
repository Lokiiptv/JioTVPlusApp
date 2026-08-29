#!/usr/bin/env bash
# =============================================================================
# build.sh — Build JioTVPlus APK (debug or release)
#
# Usage:
#   ./scripts/build.sh              # debug build (default)
#   ./scripts/build.sh release      # release build (minified + signed)
#   ./scripts/build.sh clean        # clean build cache
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_MODULE="app"
VARIANT="${1:-debug}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[BUILD]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Validate SDK ─────────────────────────────────────────────────────────────
if [ -z "${ANDROID_HOME:-}" ]; then
    [ -f "$PROJECT_DIR/local.properties" ] && \
        ANDROID_HOME=$(grep "^sdk.dir=" "$PROJECT_DIR/local.properties" | cut -d= -f2)
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -d "$ANDROID_HOME" ] || error "Android SDK not found at $ANDROID_HOME. Set ANDROID_HOME or update local.properties."

cd "$PROJECT_DIR"

# ── Gradle wrapper ────────────────────────────────────────────────────────────
GRADLEW="$PROJECT_DIR/gradlew"
[ -x "$GRADLEW" ] || chmod +x "$GRADLEW"

# ── Task dispatch ─────────────────────────────────────────────────────────────
case "$VARIANT" in
  clean)
    info "Cleaning build cache…"
    "$GRADLEW" clean
    success "Clean complete"
    exit 0
    ;;
  release)
    TASK="assembleRelease"
    APK_DIR="$PROJECT_DIR/$APP_MODULE/build/outputs/apk/release"
    ;;
  debug|*)
    TASK="assembleDebug"
    APK_DIR="$PROJECT_DIR/$APP_MODULE/build/outputs/apk/debug"
    ;;
esac

# ── Build ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  JioTVPlus — ${VARIANT^} Build${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

BUILD_START=$(date +%s)
info "Running: $TASK"

"$GRADLEW" "$TASK" --stacktrace 2>&1 | while IFS= read -r line; do
    if echo "$line" | grep -qE "^(BUILD SUCCESSFUL|BUILD FAILED|> Task|FAILURE|ERROR)"; then
        echo -e "${CYAN}$line${NC}"
    else
        echo "$line"
    fi
done

# Check exit code (pipe breaks set -e, check manually)
BUILD_STATUS=${PIPESTATUS[0]}
BUILD_END=$(date +%s)
ELAPSED=$((BUILD_END - BUILD_START))

if [ $BUILD_STATUS -ne 0 ]; then
    error "Build FAILED after ${ELAPSED}s. Check output above."
fi

# ── Report APK location ────────────────────────────────────────────────────────
APK=$(find "$APK_DIR" -name "*.apk" | head -1)
if [ -n "$APK" ]; then
    APK_SIZE=$(du -sh "$APK" | cut -f1)
    echo ""
    success "Build complete in ${ELAPSED}s"
    echo -e "  ${BOLD}APK:${NC} $APK"
    echo -e "  ${BOLD}Size:${NC} $APK_SIZE"
    echo ""
    # Write APK path to a temp file so other scripts can pick it up
    echo "$APK" > "$PROJECT_DIR/.last_apk"
else
    warn "Build succeeded but APK not found in $APK_DIR"
fi
