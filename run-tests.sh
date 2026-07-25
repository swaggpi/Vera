#!/usr/bin/env bash
# Ralph completion gate: compile + unit tests. Must pass before marking any feature [x].
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

echo "== Unit tests (JVM, no device needed) =="
./gradlew :core:test :app:testDebugUnitTest --console=plain

echo "== Compile debug APK =="
./gradlew assembleDebug --console=plain

echo "OK: tests passed and app-debug.apk built."
