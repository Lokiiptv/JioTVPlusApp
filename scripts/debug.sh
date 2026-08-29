#!/usr/bin/env bash
# =============================================================================
# debug.sh — Debug JioTVPlus on device: logcat + Frida SSL-unpin + HTTP intercept
#
# Usage:
#   ./scripts/debug.sh                    # logcat only
#   ./scripts/debug.sh --frida            # attach Frida (SSL unpin + root bypass)
#   ./scripts/debug.sh --frida --mitmweb  # full stack: Frida + mitmweb proxy
#   ./scripts/debug.sh --crash            # watch for crash/ANR only
#   ./scripts/debug.sh --network          # logcat filtered to network/HTTP logs
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
INTERCEPT_DIR="$PROJECT_DIR/../JioTV/intercept-scripts"
APP_PACKAGE="com.jiotvplus.app.debug"
MITMPROXY_PORT=8080
MITMWEB_PORT=8081
FRIDA_SCRIPT=""   # auto-detected

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; MAGENTA='\033[0;35m'; NC='\033[0m'
info()    { echo -e "${CYAN}[DEBUG]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Parse flags ───────────────────────────────────────────────────────────────
USE_FRIDA=false
USE_MITMWEB=false
CRASH_ONLY=false
NETWORK_ONLY=false
RELEASE=false

for arg in "$@"; do
    case "$arg" in
      --frida)    USE_FRIDA=true ;;
      --mitmweb)  USE_MITMWEB=true ;;
      --crash)    CRASH_ONLY=true ;;
      --network)  NETWORK_ONLY=true ;;
      --release)  RELEASE=true; APP_PACKAGE="com.jiotvplus.app" ;;
    esac
done

# ── Locate ADB ────────────────────────────────────────────────────────────────
if [ -z "${ANDROID_HOME:-}" ]; then
    [ -f "$PROJECT_DIR/local.properties" ] && \
        ANDROID_HOME=$(grep "^sdk.dir=" "$PROJECT_DIR/local.properties" | cut -d= -f2)
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
[ -x "$ADB" ] || ADB=$(which adb 2>/dev/null) || error "adb not found"

DEVICE=$("$ADB" devices | grep "device$" | head -1 | awk '{print $1}')
[ -n "$DEVICE" ] || error "No ADB device connected."
DEVICE_MODEL=$("$ADB" -s "$DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r')

echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  JioTVPlus — Debug Session${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Device:  $DEVICE_MODEL ($DEVICE)"
echo -e "  Package: $APP_PACKAGE"
echo -e "  Frida:   $([ "$USE_FRIDA" = true ] && echo enabled || echo disabled)"
echo -e "  mitmweb: $([ "$USE_MITMWEB" = true ] && echo enabled || echo disabled)"
echo ""

# ── Cleanup on exit ───────────────────────────────────────────────────────────
cleanup() {
    echo ""
    info "Cleaning up…"
    # Remove proxy settings
    "$ADB" -s "$DEVICE" shell settings delete global http_proxy 2>/dev/null || true
    "$ADB" -s "$DEVICE" shell settings delete global global_http_proxy_host 2>/dev/null || true
    "$ADB" -s "$DEVICE" shell settings delete global global_http_proxy_port 2>/dev/null || true
    # Kill background processes
    kill "$(jobs -p)" 2>/dev/null || true
    success "Cleanup done"
}
trap cleanup EXIT INT TERM

# ── Step 1: mitmweb ───────────────────────────────────────────────────────────
if $USE_MITMWEB; then
    if ! command -v mitmweb &>/dev/null; then
        warn "mitmweb not found. Install with: pip install mitmproxy"
    else
        ADDON=""
        [ -f "$INTERCEPT_DIR/mitmproxy_addon.py" ] && ADDON="-s $INTERCEPT_DIR/mitmproxy_addon.py"

        info "Starting mitmweb on port $MITMPROXY_PORT (UI: http://127.0.0.1:$MITMWEB_PORT)…"
        mitmweb --listen-port "$MITMPROXY_PORT" --web-port "$MITMWEB_PORT" $ADDON \
            --save-stream-file "$PROJECT_DIR/debug-$(date +%Y%m%d-%H%M%S).flows" &
        sleep 2

        # Set emulator proxy
        HOST_IP=$(ip route | awk '/default/ {print $3}' | head -1)
        "$ADB" -s "$DEVICE" shell settings put global http_proxy "${HOST_IP}:${MITMPROXY_PORT}" 2>/dev/null || true
        success "Proxy set to ${HOST_IP}:${MITMPROXY_PORT}"
        echo -e "  ${YELLOW}→ Open mitmweb UI:${NC} http://127.0.0.1:${MITMWEB_PORT}"
    fi
fi

# ── Step 2: Push & start frida-server ─────────────────────────────────────────
if $USE_FRIDA; then
    if ! command -v frida &>/dev/null; then
        warn "frida not found. Install: pip install frida-tools"
    else
        DEVICE_ARCH=$("$ADB" -s "$DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')
        info "Device arch: $DEVICE_ARCH"

        # Find frida-server binary
        FRIDA_BIN=$(ls "$INTERCEPT_DIR/frida-server-"*"-android-${DEVICE_ARCH}" 2>/dev/null | sort -V | tail -1)
        if [ -z "$FRIDA_BIN" ]; then
            # Try arm64 as fallback
            FRIDA_BIN=$(ls "$INTERCEPT_DIR/frida-server-"*"-android-arm64" 2>/dev/null | sort -V | tail -1)
        fi
        [ -n "$FRIDA_BIN" ] || error "frida-server binary not found in $INTERCEPT_DIR"

        # Check if already running
        FRIDA_PID=$("$ADB" -s "$DEVICE" shell "ps -A 2>/dev/null | grep frida-server" | awk '{print $2}' | head -1 || true)
        if [ -z "$FRIDA_PID" ]; then
            info "Pushing frida-server ($(basename "$FRIDA_BIN"))…"
            "$ADB" -s "$DEVICE" push "$FRIDA_BIN" /data/local/tmp/frida-server 2>/dev/null
            "$ADB" -s "$DEVICE" shell chmod 755 /data/local/tmp/frida-server
            "$ADB" -s "$DEVICE" shell "nohup /data/local/tmp/frida-server > /dev/null 2>&1 &"
            sleep 2
            success "frida-server started"
        else
            success "frida-server already running (PID $FRIDA_PID)"
        fi

        # Find best Frida script
        if [ -f "$INTERCEPT_DIR/jiotv-intercept.js" ]; then
            FRIDA_SCRIPT="$INTERCEPT_DIR/jiotv-intercept.js"
        elif [ -f "$INTERCEPT_DIR/frida-unpinning.js" ]; then
            FRIDA_SCRIPT="$INTERCEPT_DIR/frida-unpinning.js"
        fi
        [ -n "$FRIDA_SCRIPT" ] || error "No Frida script found in $INTERCEPT_DIR"
        info "Frida script: $(basename "$FRIDA_SCRIPT")"

        # Force-stop and spawn with Frida
        info "Spawning $APP_PACKAGE with Frida…"
        "$ADB" -s "$DEVICE" shell am force-stop "$APP_PACKAGE" 2>/dev/null || true
        sleep 0.5

        frida -D "$DEVICE" -f "$APP_PACKAGE" -l "$FRIDA_SCRIPT" --no-pause &
        FRIDA_PID=$!
        sleep 2
        success "Frida attached (PID $FRIDA_PID)"
    fi
fi

# ── Step 3: Logcat ────────────────────────────────────────────────────────────
echo ""

if $CRASH_ONLY; then
    info "Watching for crashes & ANRs (Ctrl+C to stop)…"
    echo -e "${YELLOW}──────────── CRASH / ANR WATCH ─────────────${NC}"
    "$ADB" -s "$DEVICE" logcat -v time \
        "AndroidRuntime:E" "System.err:E" "ActivityManager:E" "InputMethodManager:E" \
        "WindowManager:E" "ActivityThread:E" "libc:F" "*:S" | \
        grep --color=auto -E "FATAL|ANR|Error|Exception|Crash|$APP_PACKAGE"

elif $NETWORK_ONLY; then
    info "Watching network/HTTP logs (Ctrl+C to stop)…"
    echo -e "${YELLOW}──────────── NETWORK LOGS ──────────────────${NC}"
    "$ADB" -s "$DEVICE" logcat -v time | grep --color=auto -iE \
        "okhttp|retrofit|OkHttp|HTTP|network|URL|request|response|socket|connect|ssl|cert"

else
    # Full logcat for our package
    info "Tailing logcat for $APP_PACKAGE (Ctrl+C to stop)…"
    echo -e "${YELLOW}──────────── LOGCAT ($(date +%H:%M:%S)) ─────────────${NC}"

    # Color-map log levels
    "$ADB" -s "$DEVICE" logcat -v time 2>/dev/null | \
        grep -E "$APP_PACKAGE|AndroidRuntime|System.err|OkHttp|Hilt" | \
        sed -E \
          -e "s/(E[[:space:]]+[^:]+:.*)/$(echo -e '\033[0;31m')\1$(echo -e '\033[0m')/" \
          -e "s/(W[[:space:]]+[^:]+:.*)/$(echo -e '\033[1;33m')\1$(echo -e '\033[0m')/" \
          -e "s/(I[[:space:]]+[^:]+:.*)/$(echo -e '\033[0;36m')\1$(echo -e '\033[0m')/"
fi
