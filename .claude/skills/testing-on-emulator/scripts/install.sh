#!/usr/bin/env bash
# Builds the app, installs it on the running emulator and launches it.
#
#   install.sh [debug|release] [--fresh] [--no-build]
#
#   debug      ca.kallistesoft.guidehunter.debug ("GuideHunter Dev"), the default
#   release    ca.kallistesoft.guidehunter, R8-shrunk; signed with the debug key when no release keystore is set up
#   --fresh    clear the app's data first, so it opens on the first-run flow
#   --no-build install the APK already in app/build/outputs
#
# Run from anywhere inside the repo. Clears logcat before launching, so check_crash.sh only sees this run.
set -euo pipefail

BUILD_TYPE=debug
FRESH=false
BUILD=true
for arg in "$@"; do
    case "$arg" in
        debug | release) BUILD_TYPE=$arg ;;
        --fresh) FRESH=true ;;
        --no-build) BUILD=false ;;
        *)
            echo "Unknown argument: $arg (expected debug, release, --fresh, --no-build)" >&2
            exit 2
            ;;
    esac
done

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
# There is no system Java on this Mac; Android Studio's runtime builds the project.
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="$SDK"

ROOT="$(git rev-parse --show-toplevel)"
APK_DIR="$ROOT/app/build/outputs/apk/$BUILD_TYPE"
ACTIVITY="ca.kallistesoft.guidehunter.MainActivity"
if [[ $BUILD_TYPE == debug ]]; then
    PKG=ca.kallistesoft.guidehunter.debug
else
    PKG=ca.kallistesoft.guidehunter
fi

if ! "$ADB" devices | grep -q $'\tdevice$'; then
    echo "No device connected. Run .claude/skills/testing-on-emulator/scripts/emulator.sh first." >&2
    exit 1
fi

if $BUILD; then
    task=":app:assemble$(tr '[:lower:]' '[:upper:]' <<<"${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"
    echo "Building ($task)..."
    (cd "$ROOT" && ./gradlew -q "$task")
fi

if [[ $BUILD_TYPE == debug ]]; then
    APK="$APK_DIR/app-debug.apk"
elif [[ -f "$APK_DIR/app-release.apk" ]]; then
    APK="$APK_DIR/app-release.apk"
else
    # No keystore.properties: Gradle leaves the release APK unsigned and Android refuses to install it.
    # Sign a copy with the local debug key; it is still the R8-shrunk release build.
    unsigned="$APK_DIR/app-release-unsigned.apk"
    [[ -f "$unsigned" ]] || { echo "No release APK in $APK_DIR. Build without --no-build." >&2; exit 1; }
    APK="$APK_DIR/app-release-debugsigned.apk"
    apksigner="$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)"
    "$apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
        --ks-key-alias androiddebugkey --key-pass pass:android --out "$APK" "$unsigned"
    echo "Release APK signed with the debug key."
fi
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }

echo "Installing $(basename "$APK")..."
if ! out="$("$ADB" install -r "$APK" 2>&1)"; then
    if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out"; then
        # Installed copy has another signature (e.g. a Play build): replace it. Its data is lost.
        echo "Installed copy has a different signature: uninstalling it (its data is lost)."
        "$ADB" uninstall "$PKG" >/dev/null
        "$ADB" install "$APK"
    else
        echo "$out" >&2
        exit 1
    fi
fi

if $FRESH; then
    "$ADB" shell pm clear "$PKG" >/dev/null
    echo "App data cleared: first-run flow."
fi

"$ADB" shell am force-stop "$PKG"
"$ADB" logcat -c
"$ADB" shell am start -W -n "$PKG/$ACTIVITY" | grep -E "Status|TotalTime" || true
echo "Launched $PKG ($BUILD_TYPE)."
