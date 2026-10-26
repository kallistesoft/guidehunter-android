#!/usr/bin/env bash
# Starts the test emulator, or reuses one that is already running, and returns once it takes adb commands.
#
#   emulator.sh [avd]        default AVD: BlockDaily_Pixel_9 (Pixel 9, arm64, Play image)
#
# Prints the serial of the ready device (e.g. emulator-5554) on the last line.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD="${1:-BlockDaily_Pixel_9}"

# adb shell can hang forever on an emulator restored from a bad snapshot, and macOS has no `timeout`,
# so every probe runs under this watchdog.
with_timeout() {
    local secs=$1
    shift
    "$@" &
    local pid=$!
    (sleep "$secs" && kill "$pid" 2>/dev/null) &
    local watcher=$!
    local rc=0
    wait "$pid" || rc=$?
    kill "$watcher" 2>/dev/null || true
    return $rc
}

running_emulator() {
    "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

# Booted and responsive: 10 s is far more than a healthy emulator needs to answer getprop.
is_ready() {
    local serial=$1
    [[ "$(with_timeout 10 "$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "AVD '$AVD' not found. Available:" >&2
    "$SDK/emulator/emulator" -list-avds >&2
    exit 1
fi

serial="$(running_emulator || true)"
if [[ -n "$serial" ]] && is_ready "$serial"; then
    echo "Reusing running emulator."
    echo "$serial"
    exit 0
fi

if [[ -n "$serial" ]] || pgrep -f qemu-system >/dev/null; then
    echo "Emulator present but not responding: restarting it with a cold boot."
    pkill -f qemu-system || true
    "$ADB" kill-server || true
    sleep 2
fi

# A cold boot (no snapshot) avoids the hanging-adb state; `android emulator start` returns once booted.
echo "Starting $AVD (cold boot, about a minute)..."
android emulator start --cold "$AVD"

# Allow up to 3 minutes for the package manager after boot_completed; a cold boot usually needs well under one.
for _ in $(seq 1 90); do
    serial="$(running_emulator || true)"
    if [[ -n "$serial" ]] && is_ready "$serial" &&
        with_timeout 10 "$ADB" -s "$serial" shell pm path android >/dev/null 2>&1; then
        echo "$serial"
        exit 0
    fi
    sleep 2
done

echo "Emulator did not become ready. Check the emulator window, or run: $ADB devices" >&2
exit 1
