#!/usr/bin/env bash
# =============================================================================
#  Haze Mobile — command-line APK builder (no Android Studio required)
#
#  Prereqs (see BUILD.md):
#    - JDK 17            : sudo pacman -S jdk17-openjdk
#    - Android SDK       : cmdline-tools + platform-tools + platforms;android-34
#                          + build-tools;34.0.0  (installed via sdkmanager)
#    - gradle (one time) : sudo pacman -S gradle   (only to create the wrapper)
#
#  Usage:
#    ./build-apk.sh            build a debug APK into app/build/outputs/apk/debug/
#    ./build-apk.sh --install  build, then `adb install` to a connected phone
#    ./build-apk.sh --release  build an unsigned release APK
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [[ -t 1 ]]; then
  GREEN='\033[0;32m'; BLUE='\033[0;34m'; RED='\033[0;31m'; RESET='\033[0m'
else
  GREEN=''; BLUE=''; RED=''; RESET=''
fi
info() { echo -e "${BLUE}[*]${RESET} $*"; }
ok()   { echo -e "${GREEN}[✓]${RESET} $*"; }
die()  { echo -e "${RED}[✗]${RESET} $*" >&2; exit 1; }

DO_INSTALL=false
VARIANT="assembleDebug"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
for arg in "$@"; do
  case "$arg" in
    --install) DO_INSTALL=true ;;
    --release) VARIANT="assembleRelease"; APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk" ;;
    -h|--help) sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^#\s\?//'; exit 0 ;;
    *) die "Unknown option: $arg" ;;
  esac
done

# ── JDK ───────────────────────────────────────────────────────────────────────
command -v java >/dev/null 2>&1 || die "JDK not found. Install it:  sudo pacman -S jdk17-openjdk"

# ── Android SDK location ──────────────────────────────────────────────────────
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_HOME
[[ -d "$ANDROID_HOME" ]] || die "Android SDK not found at '$ANDROID_HOME'. See BUILD.md (sdkmanager step). \
Override with: ANDROID_HOME=/path/to/sdk ./build-apk.sh"
info "Using Android SDK: $ANDROID_HOME"

# AGP reads the SDK path from local.properties (or ANDROID_HOME). Pin it.
echo "sdk.dir=$ANDROID_HOME" > local.properties

# ── Gradle wrapper (create once if missing) ───────────────────────────────────
if [[ ! -f "gradlew" || ! -f "gradle/wrapper/gradle-wrapper.jar" ]]; then
  info "Gradle wrapper missing — generating it (needs system 'gradle' once)…"
  command -v gradle >/dev/null 2>&1 || die "gradle not found (needed only to create the wrapper): sudo pacman -S gradle"
  gradle wrapper --gradle-version 8.9
  ok "Wrapper created"
fi

# ── Build ─────────────────────────────────────────────────────────────────────
info "Building ($VARIANT) — first run downloads Gradle + dependencies, be patient…"
./gradlew "$VARIANT"

[[ -f "$APK_PATH" ]] || die "Build finished but APK not found at $APK_PATH"
ok "APK ready: $ROOT/$APK_PATH"

if $DO_INSTALL; then
  command -v adb >/dev/null 2>&1 || die "adb not found. Install it:  sudo pacman -S android-tools"
  info "Installing to connected device via adb…"
  adb install -r "$APK_PATH"
  ok "Installed. Launch 'Haze' on your phone (Orbot must be running)."
else
  echo "    Install to a USB-connected phone:  adb install -r \"$APK_PATH\""
  echo "    Or copy the .apk to the phone and tap it (allow 'install unknown apps')."
fi
