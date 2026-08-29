#!/usr/bin/env bash
# =============================================================================
# deploy.sh — Install JioTVPlus APK on an ADB-connected TV / emulator
#
# Usage:
#   ./scripts/deploy.sh                    # installs last built APK
#   ./scripts/deploy.sh path/to/app.apk   # installs specific APK
#   ./scripts/deploy.sh --start-avd        # boots Android TV emulator first
#   ./scripts/deploy.sh --list-devices     # show connected devices
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_PACKAGE="com.jiotvplus.app.debug"          # .debug suffix for debug builds
APP_ACTIVITY=".ui.MainActivity"
AVD_NAME="Android_TV_720p_API28_Root"
BOOT_TIMEOUT=120

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[DEPLOY]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Locate ADB ────────────────────────────────────────────────────────────────
if [ -z "${ANDROID_HOME:-}" ]; then
    [ -f "$PROJECT_DIR/local.properties" ] && \
        ANDROID_HOME=$(grep "^sdk.dir=" "$PROJECT_DIR/local.properties" | cut -d= -f2)
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
[ -x "$ADB" ] || ADB=$(which adb 2>/dev/null) || error "adb not found"
EMULATOR="$ANDROID_HOME/emulator/emulator"

# ── Parse args ────────────────────────────────────────────────────────────────
START_AVD=false
LIST_ONLY=false
INPUT_APK=""

for arg in "$@"; do
    case "$arg" in
      --start-avd)   START_AVD=true ;;
      --list-devices) LIST_ONLY=true ;;
      --release)     APP_PACKAGE="com.jiotvplus.app" ;;
      *)             [ -f "$arg" ] && INPUT_APK="$arg" ;;
    esac
done

if $LIST_ONLY; then
    echo -e "${BOLD}Connected ADB devices:${NC}"
    "$ADB" devices -l
    exit 0
fi

# ── Start AVD if requested ────────────────────────────────────────────────────
if $START_AVD; then
    if [ -x "$EMULATOR" ]; then
        info "Starting AVD: $AVD_NAME"
        "$EMULATOR" -avd "$AVD_NAME" \
            -writable-system \
            -no-snapshot-load \
            -selinux permissive \
            -no-audio \
            -no-boot-anim &
        EMULATOR_PID=$!
        info "Waiting for device to boot (up to ${BOOT_TIMEOUT}s)…"
        "$ADB" wait-for-device
        ELAPSED=0
        until "$ADB" shell getprop sys.boot_completed 2>/dev/null | grep -q "^1$"; do
            sleep 3; ELAPSED=$((ELAPSED+3))
            [ $ELAPSED -lt $BOOT_TIMEOUT ] || error "Emulator did not boot within ${BOOT_TIMEOUT}s"
            echo -n "."
        done
        echo ""
        success "Emulator booted"
    else
        warn "emulator binary not found at $EMULATOR, skipping AVD start"
    fi
fi

# ── Resolve APK ──────────────────────────────────────────────────────────────
if [ -z "$INPUT_APK" ]; then
    [ -f "$PROJECT_DIR/.last_apk" ] && INPUT_APK=$(cat "$PROJECT_DIR/.last_apk")
    [ -n "$INPUT_APK" ] || error "No APK specified and no recent build found. Run build.sh first."
fi
[ -f "$INPUT_APK" ] || error "APK not found: $INPUT_APK"

# ── Check device connected ────────────────────────────────────────────────────
DEVICE_COUNT=$("$ADB" devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    error "No ADB device connected. Connect a TV/emulator or use --start-avd flag."
fi

echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  JioTVPlus — Deploy${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
DEVICE=$("$ADB" devices | grep "device$" | head -1 | awk '{print $1}')
DEVICE_MODEL=$("$ADB" -s "$DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VER=$("$ADB" -s "$DEVICE" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo -e "  Device:  ${DEVICE_MODEL} (Android ${ANDROID_VER}) — ${DEVICE}"
echo -e "  APK:     $(basename "$INPUT_APK") ($(du -sh "$INPUT_APK" | cut -f1))"
echo -e "  Package: $APP_PACKAGE"
echo ""

# ── Install APK ───────────────────────────────────────────────────────────────
info "Installing APK…"
"$ADB" -s "$DEVICE" install -r -d "$INPUT_APK" && success "Install successful" || {
    warn "Install with -d failed (device may not be debug). Retrying without -d…"
    "$ADB" -s "$DEVICE" install -r "$INPUT_APK"
    success "Install successful"
}

# ── Launch app ────────────────────────────────────────────────────────────────
info "Launching $APP_PACKAGE…"
"$ADB" -s "$DEVICE" shell am start -n "${APP_PACKAGE}/${APP_PACKAGE}${APP_ACTIVITY}" \
    -a android.intent.action.MAIN \
    -c android.intent.category.LEANBACK_LAUNCHER 2>&1 | grep -v "^Starting:"
success "App launched"

# ── Tail logcat for app startup ───────────────────────────────────────────────
echo ""
info "Tailing logcat for ${APP_PACKAGE} (Ctrl+C to stop)…"
echo -e "${YELLOW}──────────────── LOGCAT ────────────────${NC}"
"$ADB" -s "$DEVICE" logcat --pid=$("$ADB" -s "$DEVICE" shell pidof "$APP_PACKAGE" 2>/dev/null | tr -d '\r') \
    -v time 2>/dev/null || \
"$ADB" -s "$DEVICE" logcat -s "JioTVPlus:V" "AndroidRuntime:E" "System.err:E" -v time
