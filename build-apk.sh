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
#    ./build-apk.sh --release  build a release APK (signed if a keystore is set)
#    ./build-apk.sh --bundle   build a release .aab (App Bundle) for the Play Store
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
IS_BUNDLE=false
VARIANT="assembleDebug"
ARTIFACT="app/build/outputs/apk/debug/app-debug.apk"
for arg in "$@"; do
  case "$arg" in
    --install) DO_INSTALL=true ;;
    --release) VARIANT="assembleRelease"; ARTIFACT="app/build/outputs/apk/release/app-release.apk" ;;
    --bundle)  IS_BUNDLE=true; VARIANT="bundleRelease"; ARTIFACT="app/build/outputs/bundle/release/app-release.aab" ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^#\s\?//'; exit 0 ;;
    *) die "Unknown option: $arg" ;;
  esac
done

# A Play-uploadable .aab must be signed with your upload keystore. Warn early.
if $IS_BUNDLE && [[ -z "${HAZE_KEYSTORE_FILE:-}" ]] \
   && ! grep -q '^HAZE_KEYSTORE_FILE=' gradle.properties 2>/dev/null; then
  info "No HAZE_KEYSTORE_FILE set — the .aab will be UNSIGNED and cannot be uploaded as-is."
  info "Set the keystore properties (see the RELEASE notes in app/build.gradle.kts) to sign it."
fi

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

[[ -f "$ARTIFACT" ]] || die "Build finished but artifact not found at $ARTIFACT"
ok "Artifact ready: $ROOT/$ARTIFACT"

if $IS_BUNDLE; then
  echo "    Upload this .aab in the Play Console → your app → Production/Testing → Create release."
  echo "    (Play re-signs installs from this bundle with the app-signing key.)"
elif $DO_INSTALL; then
  command -v adb >/dev/null 2>&1 || die "adb not found. Install it:  sudo pacman -S android-tools"
  info "Installing to connected device via adb…"
  adb install -r "$ARTIFACT"
  ok "Installed. Launch 'Haze' on your phone (Orbot must be running)."
else
  echo "    Install to a USB-connected phone:  adb install -r \"$ARTIFACT\""
  echo "    Or copy the .apk to the phone and tap it (allow 'install unknown apps')."
fi
