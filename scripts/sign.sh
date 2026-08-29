#!/usr/bin/env bash
# =============================================================================
# sign.sh — Zipalign + apksigner for JioTVPlus APK
#
# Usage:
#   ./scripts/sign.sh                          # signs last built APK (debug)
#   ./scripts/sign.sh path/to/app.apk          # signs a specific APK (debug key)
#   ./scripts/sign.sh path/to/app.apk release  # signs with release keystore
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$PROJECT_DIR/app"
KEYSTORE_PROPS="$APP_DIR/keystore.properties"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[SIGN]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Resolve APK path ──────────────────────────────────────────────────────────
INPUT_APK="${1:-}"
SIGNING_FLAVOR="${2:-debug}"

if [ -z "$INPUT_APK" ]; then
    [ -f "$PROJECT_DIR/.last_apk" ] && INPUT_APK=$(cat "$PROJECT_DIR/.last_apk")
    [ -n "$INPUT_APK" ] || error "No APK specified and no recent build found. Run build.sh first."
fi
[ -f "$INPUT_APK" ] || error "APK not found: $INPUT_APK"

# ── Load keystore properties ──────────────────────────────────────────────────
load_prop() {
    grep "^${1}=" "$KEYSTORE_PROPS" 2>/dev/null | cut -d= -f2- | tr -d '\r'
}

if [ "$SIGNING_FLAVOR" = "release" ]; then
    STORE_FILE="$APP_DIR/$(load_prop RELEASE_STORE_FILE)"
    STORE_PASS="$(load_prop RELEASE_STORE_PASSWORD)"
    KEY_ALIAS="$(load_prop RELEASE_KEY_ALIAS)"
    KEY_PASS="$(load_prop RELEASE_KEY_PASSWORD)"
    [ -n "$STORE_PASS" ] || error "Release keystore not configured in keystore.properties"
else
    STORE_FILE="$APP_DIR/$(load_prop DEBUG_STORE_FILE)"
    STORE_PASS="$(load_prop DEBUG_STORE_PASSWORD)"
    KEY_ALIAS="$(load_prop DEBUG_KEY_ALIAS)"
    KEY_PASS="$(load_prop DEBUG_KEY_PASSWORD)"
fi

[ -f "$STORE_FILE" ] || error "Keystore not found: $STORE_FILE"

# ── Locate build tools ────────────────────────────────────────────────────────
if [ -z "${ANDROID_HOME:-}" ]; then
    [ -f "$PROJECT_DIR/local.properties" ] && \
        ANDROID_HOME=$(grep "^sdk.dir=" "$PROJECT_DIR/local.properties" | cut -d= -f2)
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS_DIR=$(ls -d "$ANDROID_HOME/build-tools/"*/ 2>/dev/null | sort -V | tail -1)
[ -d "$BUILD_TOOLS_DIR" ] || error "Android build-tools not found under $ANDROID_HOME"

ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
[ -x "$ZIPALIGN" ] || error "zipalign not found at $ZIPALIGN"
[ -x "$APKSIGNER" ] || error "apksigner not found at $APKSIGNER"

# ── Derive output filename ────────────────────────────────────────────────────
APK_DIR="$(dirname "$INPUT_APK")"
APK_BASE="$(basename "$INPUT_APK" .apk)"
ALIGNED_APK="$APK_DIR/${APK_BASE}-aligned.apk"
SIGNED_APK="$APK_DIR/${APK_BASE}-signed.apk"

echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  JioTVPlus — APK Signing (${SIGNING_FLAVOR})${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Input:     $INPUT_APK"
echo -e "  Keystore:  $STORE_FILE (alias: $KEY_ALIAS)"
echo ""

# ── Step 1: Zipalign ──────────────────────────────────────────────────────────
info "Zipaligning APK…"
rm -f "$ALIGNED_APK"
"$ZIPALIGN" -v -p 4 "$INPUT_APK" "$ALIGNED_APK" > /dev/null
success "Zipalign done → $(basename "$ALIGNED_APK")"

# ── Step 2: Sign ──────────────────────────────────────────────────────────────
info "Signing APK with apksigner (v2/v3 scheme)…"
rm -f "$SIGNED_APK"
"$APKSIGNER" sign \
    --ks "$STORE_FILE" \
    --ks-pass "pass:$STORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass "pass:$KEY_PASS" \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

rm -f "$ALIGNED_APK"

# ── Step 3: Verify ────────────────────────────────────────────────────────────
info "Verifying signature…"
"$APKSIGNER" verify --verbose "$SIGNED_APK" 2>&1 | grep -E "Verified|v[1-4] scheme"

SIGNED_SIZE=$(du -sh "$SIGNED_APK" | cut -f1)
echo ""
success "Signed APK ready (${SIGNED_SIZE})"
echo -e "  ${BOLD}Output:${NC} $SIGNED_APK"
echo ""

# Update .last_apk to point to signed APK
echo "$SIGNED_APK" > "$PROJECT_DIR/.last_apk"
