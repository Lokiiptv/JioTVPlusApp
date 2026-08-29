# =============================================================================
# Makefile — JioTVPlus Android TV App
# All commands run from project root
# =============================================================================

.PHONY: help build build-release clean sign deploy debug intercept stop-intercept \
        export-flows all check-device logcat crash-watch release release-minor release-major

SCRIPTS := scripts
GRADLEW := ./gradlew

# Default: show help
help:
	@echo ""
	@echo "  JioTVPlus — Android TV Build System"
	@echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@echo "  Build:"
	@echo "    make build          Build debug APK"
	@echo "    make build-release  Build release APK (minified)"
	@echo "    make clean          Clean Gradle build cache"
	@echo ""
	@echo "  Sign:"
	@echo "    make sign           Sign last APK with debug key"
	@echo "    make sign-release   Sign last APK with release key"
	@echo ""
	@echo "  Deploy:"
	@echo "    make deploy         Install + launch on connected device"
	@echo "    make start-avd      Boot Android TV emulator first"
	@echo "    make deploy-avd     Start AVD + install + launch"
	@echo ""
	@echo "  Debug:"
	@echo "    make logcat         Stream app logcat"
	@echo "    make crash-watch    Watch for crashes & ANRs"
	@echo "    make debug-network  Watch HTTP/network logs"
	@echo "    make debug-frida    Logcat + Frida SSL unpin"
	@echo ""
	@echo "  Intercept (MITM):"
	@echo "    make intercept      Start mitmweb + Frida stack"
	@echo "    make stop-intercept Stop interception"
	@echo "    make export-flows   Export last capture to JSON"
	@echo ""
	@echo "  Pipelines:"
	@echo "    make all            build → sign → deploy"
	@echo "    make all-debug-mitm build → deploy → intercept"
	@echo ""
	@echo "  Release:"
	@echo "    make release        Build + tag + GitHub release (patch)"
	@echo "    make release-minor  Bump minor version + release"
	@echo "    make release-major  Bump major version + release"
	@echo ""

# ── Build ──────────────────────────────────────────────────────────────────────
build:
	@bash $(SCRIPTS)/build.sh debug

build-release:
	@bash $(SCRIPTS)/build.sh release

clean:
	@bash $(SCRIPTS)/build.sh clean

# ── Sign ───────────────────────────────────────────────────────────────────────
sign:
	@bash $(SCRIPTS)/sign.sh

sign-release:
	@bash $(SCRIPTS)/sign.sh "" release

# ── Deploy ─────────────────────────────────────────────────────────────────────
deploy:
	@bash $(SCRIPTS)/deploy.sh

start-avd:
	@bash $(SCRIPTS)/deploy.sh --start-avd

deploy-avd:
	@bash $(SCRIPTS)/deploy.sh --start-avd

check-device:
	@bash $(SCRIPTS)/deploy.sh --list-devices

# ── Debug ──────────────────────────────────────────────────────────────────────
logcat:
	@bash $(SCRIPTS)/debug.sh

crash-watch:
	@bash $(SCRIPTS)/debug.sh --crash

debug-network:
	@bash $(SCRIPTS)/debug.sh --network

debug-frida:
	@bash $(SCRIPTS)/debug.sh --frida

# ── Intercept ──────────────────────────────────────────────────────────────────
intercept:
	@bash $(SCRIPTS)/intercept.sh

stop-intercept:
	@bash $(SCRIPTS)/intercept.sh --stop

export-flows:
	@bash $(SCRIPTS)/intercept.sh --export

# ── Pipelines ──────────────────────────────────────────────────────────────────
all: build sign deploy

all-debug-mitm: build deploy intercept

# ── Release ────────────────────────────────────────────────────────────────────
release:
	@bash $(SCRIPTS)/release.sh patch

release-minor:
	@bash $(SCRIPTS)/release.sh minor

release-major:
	@bash $(SCRIPTS)/release.sh major
