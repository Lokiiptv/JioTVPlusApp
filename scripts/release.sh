#!/usr/bin/env bash
# =============================================================================
# release.sh — Build release APK, tag git, create GitHub release
#
# Usage:
#   ./scripts/release.sh                    # build + tag + GitHub release (patch)
#   ./scripts/release.sh minor              # bump minor version (1.0.0 → 1.1.0)
#   ./scripts/release.sh major              # bump major version (1.0.0 → 2.0.0)
#   ./scripts/release.sh --no-gh            # build + tag only, skip GitHub
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$PROJECT_DIR/app"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; MAGENTA='\033[0;35m'; NC='\033[0m'
info()    { echo -e "${CYAN}[RELEASE]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Parse args ────────────────────────────────────────────────────────────────
BUMP_TYPE="patch"
PUSH_GH=true

for arg in "$@"; do
    case "$arg" in
        major|minor|patch) BUMP_TYPE="$arg" ;;
        --no-gh) PUSH_GH=false ;;
    esac
done

cd "$PROJECT_DIR"

# ── Read current version ────────────────────────────────────────────────────────
CURRENT_VC=$(grep "versionCode" "$APP_DIR/build.gradle.kts" | head -1 | grep -oE '[0-9]+')
CURRENT_VN=$(grep "versionName" "$APP_DIR/build.gradle.kts" | head -1 | sed 's/.*"\(.*\)".*/\1/')
info "Current version: $CURRENT_VN (code $CURRENT_VC)"

# ── Bump version ───────────────────────────────────────────────────────────────
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VN"
case "$BUMP_TYPE" in
    major)  MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor)  MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch)  PATCH=$((PATCH + 1)) ;;
esac
NEW_VN="$MAJOR.$MINOR.$PATCH"
NEW_VC=$((CURRENT_VC + 1))

info "Bumping to: $NEW_VN (code $NEW_VC)"

# Update build.gradle.kts
sed -i "s/versionCode = $CURRENT_VC/versionCode = $NEW_VC/" "$APP_DIR/build.gradle.kts"
sed -i "s/versionName = \"$CURRENT_VN\"/versionName = \"$NEW_VN\"/" "$APP_DIR/build.gradle.kts"

# ── Build release APK ──────────────────────────────────────────────────────────
info "Building release APK…"
"$SCRIPT_DIR/build.sh" release
BUILD_STATUS=$?
if [ $BUILD_STATUS -ne 0 ]; then
    error "Release build failed. Reverting version bump."
    sed -i "s/versionCode = $NEW_VC/versionCode = $CURRENT_VC/" "$APP_DIR/build.gradle.kts"
    sed -i "s/versionName = \"$NEW_VN\"/versionName = \"$CURRENT_VN\"/" "$APP_DIR/build.gradle.kts"
    exit 1
fi

RELEASE_APK=$(find "$APP_DIR/build/outputs/apk/release" -name "*.apk" | head -1)
[ -n "$RELEASE_APK" ] || error "Release APK not found"

# Copy to dist with versioned name
DIST_DIR="$PROJECT_DIR/dist"
mkdir -p "$DIST_DIR"
APK_NAME="jiotvplus-v$NEW_VN.apk"
cp "$RELEASE_APK" "$DIST_DIR/$APK_NAME"
APK_SIZE=$(du -sh "$DIST_DIR/$APK_NAME" | cut -f1)

success "Release APK ready: $DIST_DIR/$APK_NAME ($APK_SIZE)"

# ── Git commit + tag ──────────────────────────────────────────────────────────
info "Committing version bump + tagging…"
git add "$APP_DIR/build.gradle.kts" "$DIST_DIR/$APK_NAME"
git commit -m "release: v$NEW_VN" 2>/dev/null || warn "Nothing to commit"
git tag -a "v$NEW_VN" -m "JioTVPlus v$NEW_VN" 2>/dev/null || warn "Tag v$NEW_VN already exists"
success "Git tagged: v$NEW_VN"

# ── GitHub release ────────────────────────────────────────────────────────────
if $PUSH_GH; then
    if ! command -v gh &>/dev/null; then
        warn "gh CLI not found. Skipping GitHub release."
        warn "To create release manually:"
        warn "  git push origin main --tags"
        warn "  gh release create v$NEW_VN $DIST_DIR/$APK_NAME --title 'JioTVPlus v$NEW_VN' --notes 'Release v$NEW_VN'"
        exit 0
    fi

    # Push commits + tag
    info "Pushing to remote…"
    git push origin main --tags 2>/dev/null || warn "Push failed (no remote?)"

    # Create GitHub release
    info "Creating GitHub release v$NEW_VN…"
    gh release create "v$NEW_VN" \
        "$DIST_DIR/$APK_NAME" \
        --title "JioTVPlus v$NEW_VN" \
        --notes "## JioTVPlus v$NEW_VN

### Download
- **APK:** \`$APK_NAME\` ($APK_SIZE)

### Install
- **TV:** Install via ADB: \`adb install $APK_NAME\`
- **Phone:** Transfer APK to device and install

### Features
- Live TV streaming (HLS + DASH)
- Channel grid with genre filters
- Voice + text search
- Recently played + Play Next
- Token auto-refresh
- Screen keep-on during playback
- Works on Android TV + phones" \
        2>/dev/null && success "GitHub release created: v$NEW_VN" || warn "GitHub release failed (no auth or no remote?)"
fi

echo ""
echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}${BOLD}  Release v$NEW_VN complete!${NC}"
echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  ${BOLD}APK:${NC}   $DIST_DIR/$APK_NAME"
echo -e "  ${BOLD}Tag:${NC}    v$NEW_VN"
echo -e "  ${BOLD}Size:${NC}   $APK_SIZE"
echo ""
