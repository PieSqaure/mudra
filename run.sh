#!/usr/bin/env bash
# Single entry point to build/verify Mudra: always runs the :core unit tests, then builds and
# launches the Android app if an SDK + connected device/emulator are available.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# Prefer a system-installed Gradle over the wrapper, so a slow/blocked download of the
# wrapper's Gradle distribution never blocks this script once any working "gradle" is on PATH.
GRADLE_CMD="./gradlew"
if command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD="gradle"
fi

echo "==> Running :core unit tests (sign classification, temporal decoding, Qwen prompt/JSON contract)"
$GRADLE_CMD :core:test

if ! command -v adb >/dev/null 2>&1; then
    echo
    echo "adb not found - skipping the app build/install."
    echo "Install the Android SDK (or open this project in Android Studio) to build and run :app."
    exit 0
fi

if ! adb get-state >/dev/null 2>&1; then
    echo
    echo "No connected device or running emulator detected."
    echo "Start an emulator or connect a device, then re-run this script to build and launch the app."
    exit 0
fi

echo
echo "==> Building and installing the debug app"
$GRADLE_CMD :app:installDebug

echo "==> Launching Mudra"
adb shell am start -n com.pisquarelabs.mudra/.MainActivity
