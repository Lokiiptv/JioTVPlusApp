#!/usr/bin/env bash
# =============================================================================
# intercept.sh — Start full MITM interception stack for JioTVPlus
#
# Starts mitmweb + mitmproxy_addon.py + frida-server on the connected device.
# Captures all API traffic to a timestamped .flows file.
#
# Usage:
#   ./scripts/intercept.sh              # start interception
#   ./scripts/intercept.sh --stop       # stop and remove proxy settings
#   ./scripts/intercept.sh --export     # export last capture to JSON/HAR
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
INTERCEPT_DIR="$PROJECT_DIR/../JioTV/intercept-scripts"
CAPTURES_DIR="$PROJECT_DIR/captures"
APP_PACKAGE="com.jiotvplus.app.debug"
MITMPROXY_PORT=8080
MITMWEB_PORT=8081

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INTERCEPT]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

if [ -z "${ANDROID_HOME:-}" ]; then
    [ -f "$PROJECT_DIR/local.properties" ] && \
        ANDROID_HOME=$(grep "^sdk.dir=" "$PROJECT_DIR/local.properties" | cut -d= -f2)
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
[ -x "$ADB" ] || ADB=$(which adb 2>/dev/null) || error "adb not found"

PID_FILE="/tmp/jiotvplus-intercept.pids"

stop_intercept() {
    info "Stopping interception stack…"
    if [ -f "$PID_FILE" ]; then
        while IFS= read -r pid; do
            kill "$pid" 2>/dev/null && echo "  killed PID $pid" || true
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi
    DEVICE=$("$ADB" devices | grep "device$" | head -1 | awk '{print $1}' || true)
    if [ -n "$DEVICE" ]; then
        "$ADB" -s "$DEVICE" shell settings delete global http_proxy 2>/dev/null || true
        "$ADB" -s "$DEVICE" shell settings delete global global_http_proxy_host 2>/dev/null || true
        success "Proxy settings cleared from device"
    fi
    exit 0
}

export_flows() {
    FLOWS_FILE=$(ls -t "$CAPTURES_DIR"/*.flows 2>/dev/null | head -1)
    [ -n "$FLOWS_FILE" ] || error "No capture files found in $CAPTURES_DIR"
    OUTPUT_JSON="${FLOWS_FILE%.flows}.json"
    info "Exporting $FLOWS_FILE → JSON…"

    python3 - "$FLOWS_FILE" "$OUTPUT_JSON" << 'PYEOF'
import sys, json
from mitmproxy import io as mio
from mitmproxy import http as mhttp

with open(sys.argv[1], "rb") as f:
    flows = []
    reader = mio.FlowReader(f)
    for flow in reader.stream():
        if not isinstance(flow, mhttp.HTTPFlow): continue
        entry = {
            "method": flow.request.method,
            "url": flow.request.pretty_url,
            "request_headers": dict(flow.request.headers),
            "request_body": flow.request.get_text(strict=False),
            "status": flow.response.status_code if flow.response else None,
            "response_headers": dict(flow.response.headers) if flow.response else {},
            "response_body": flow.response.get_text(strict=False) if flow.response else None,
        }
        flows.append(entry)

with open(sys.argv[2], "w") as f:
    json.dump(flows, f, indent=2, ensure_ascii=False)

print(f"Exported {len(flows)} flows → {sys.argv[2]}")
PYEOF
    success "Exported: $OUTPUT_JSON"
    exit 0
}

# ── Dispatch flags ────────────────────────────────────────────────────────────
case "${1:-start}" in
  --stop)   stop_intercept ;;
  --export) export_flows ;;
esac

# ── Validate tools ────────────────────────────────────────────────────────────
command -v mitmweb &>/dev/null || error "mitmweb not found. Install: pip install mitmproxy"
command -v frida   &>/dev/null || warn "frida not installed — traffic intercept only, no SSL unpinning"

DEVICE=$("$ADB" devices | grep "device$" | head -1 | awk '{print $1}')
[ -n "$DEVICE" ] || error "No ADB device connected."
DEVICE_MODEL=$("$ADB" -s "$DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r')

mkdir -p "$CAPTURES_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
FLOWS_FILE="$CAPTURES_DIR/jiotvplus-${TIMESTAMP}.flows"

echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  JioTVPlus — Interception Stack${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Device:  $DEVICE_MODEL ($DEVICE)"
echo -e "  Capture: $FLOWS_FILE"
echo -e "  UI:      http://127.0.0.1:$MITMWEB_PORT"
echo ""

# ── Install mitmproxy CA cert ─────────────────────────────────────────────────
CERT_PATH="$HOME/.mitmproxy/mitmproxy-ca-cert.cer"
if [ -f "$CERT_PATH" ]; then
    CERT_HASH=$(openssl x509 -inform PEM -subject_hash_old -in "$CERT_PATH" | head -1)
    SYSTEM_CERT="/system/etc/security/cacerts/${CERT_HASH}.0"
    HAS_CERT=$("$ADB" -s "$DEVICE" shell "ls $SYSTEM_CERT 2>/dev/null" | wc -c)
    if [ "$HAS_CERT" -eq 0 ]; then
        info "Installing mitmproxy CA cert to system store…"
        "$ADB" -s "$DEVICE" root 2>/dev/null || true
        sleep 1
        "$ADB" -s "$DEVICE" remount 2>/dev/null || true
        "$ADB" -s "$DEVICE" push "$CERT_PATH" "$SYSTEM_CERT" 2>/dev/null || \
            warn "Could not push CA cert (device may not be rooted)"
        "$ADB" -s "$DEVICE" shell chmod 644 "$SYSTEM_CERT" 2>/dev/null || true
        success "CA cert installed"
    else
        success "mitmproxy CA cert already installed"
    fi
else
    warn "mitmproxy CA cert not found at $CERT_PATH — run mitmweb once to generate it"
fi

# ── Start mitmweb ─────────────────────────────────────────────────────────────
ADDON_ARG=""
[ -f "$INTERCEPT_DIR/mitmproxy_addon.py" ] && ADDON_ARG="-s $INTERCEPT_DIR/mitmproxy_addon.py"

info "Starting mitmweb on port $MITMPROXY_PORT…"
mitmweb \
    --listen-port "$MITMPROXY_PORT" \
    --web-port "$MITMWEB_PORT" \
    --save-stream-file "$FLOWS_FILE" \
    $ADDON_ARG &
MITMWEB_PID=$!
echo "$MITMWEB_PID" > "$PID_FILE"
sleep 2
success "mitmweb running (PID $MITMWEB_PID)"

# ── Set device proxy ─────────────────────────────────────────────────────────
HOST_IP=$(ip route | awk '/default/ {print $3}' | head -1)
"$ADB" -s "$DEVICE" shell settings put global http_proxy "${HOST_IP}:${MITMPROXY_PORT}" 2>/dev/null || \
    "$ADB" -s "$DEVICE" shell settings put global global_http_proxy_host "$HOST_IP" 2>/dev/null || true
"$ADB" -s "$DEVICE" shell settings put global global_http_proxy_port "$MITMPROXY_PORT" 2>/dev/null || true
success "Device proxy → ${HOST_IP}:${MITMPROXY_PORT}"

# ── Push & start frida-server ─────────────────────────────────────────────────
if command -v frida &>/dev/null; then
    DEVICE_ARCH=$("$ADB" -s "$DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')
    FRIDA_BIN=$(ls "$INTERCEPT_DIR/frida-server-"*"-android-${DEVICE_ARCH}" 2>/dev/null | sort -V | tail -1 || \
                ls "$INTERCEPT_DIR/frida-server-"*"-android-arm64" 2>/dev/null | sort -V | tail -1 || true)
    if [ -n "$FRIDA_BIN" ]; then
        FRIDA_RUNNING=$("$ADB" -s "$DEVICE" shell "ps -A 2>/dev/null | grep frida-server" | wc -l || echo 0)
        if [ "$FRIDA_RUNNING" -eq 0 ]; then
            "$ADB" -s "$DEVICE" push "$FRIDA_BIN" /data/local/tmp/frida-server 2>/dev/null
            "$ADB" -s "$DEVICE" shell chmod 755 /data/local/tmp/frida-server
            "$ADB" -s "$DEVICE" shell "nohup /data/local/tmp/frida-server > /dev/null 2>&1 &"
            sleep 2
            success "frida-server started ($(basename "$FRIDA_BIN"))"
        else
            success "frida-server already running"
        fi

        FRIDA_SCRIPT="$INTERCEPT_DIR/jiotv-intercept.js"
        [ -f "$FRIDA_SCRIPT" ] || FRIDA_SCRIPT="$INTERCEPT_DIR/frida-unpinning.js"
        if [ -f "$FRIDA_SCRIPT" ]; then
            info "Spawning $APP_PACKAGE with Frida SSL unpin…"
            "$ADB" -s "$DEVICE" shell am force-stop "$APP_PACKAGE" 2>/dev/null || true
            sleep 0.5
            frida -D "$DEVICE" -f "$APP_PACKAGE" -l "$FRIDA_SCRIPT" --no-pause &
            echo "$!" >> "$PID_FILE"
            sleep 2
            success "Frida attached"
        fi
    fi
fi

echo ""
echo -e "${GREEN}${BOLD}Interception active.${NC}"
echo -e "  ${BOLD}mitmweb UI:${NC}  http://127.0.0.1:${MITMWEB_PORT}"
echo -e "  ${BOLD}Flows file:${NC}  $FLOWS_FILE"
echo -e "  ${BOLD}Stop:${NC}        ./scripts/intercept.sh --stop"
echo -e "  ${BOLD}Export JSON:${NC} ./scripts/intercept.sh --export"
echo ""
echo -e "${YELLOW}Waiting… (Ctrl+C or run --stop to clean up)${NC}"
wait
