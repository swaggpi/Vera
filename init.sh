#!/usr/bin/env bash
# Vera — Android environment bootstrap (Ralph loop init).
# Idempotent: installs the Android SDK (Tier-1: compile + unit tests) if missing,
# accepts licenses, and writes local.properties. Safe to re-run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_VER="11076708"   # cmdline-tools package (2024+)
PLATFORM="android-35"
BUILD_TOOLS="35.0.0"

log() { echo "[init] $*"; }

export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"

# 1. Java check
if ! command -v java >/dev/null 2>&1; then
  log "ERROR: no JDK on PATH. Install JDK 17+."; exit 1
fi
log "JDK: $(java -version 2>&1 | head -1)"

# 2. cmdline-tools
CLT_DIR="$SDK/cmdline-tools/latest"
if [ ! -x "$CLT_DIR/bin/sdkmanager" ]; then
  log "Installing Android cmdline-tools..."
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VER}_latest.zip"
  curl -fsSL "$url" -o "$tmp/clt.zip"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
  unzip -q "$tmp/clt.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
export PATH="$CLT_DIR/bin:$SDK/platform-tools:$PATH"
log "sdkmanager: $(sdkmanager --version 2>/dev/null || echo missing)"

# 3. Accept licenses + install packages
log "Accepting licenses..."
yes | sdkmanager --licenses >/dev/null 2>&1 || true
log "Installing platform-tools, platforms;$PLATFORM, build-tools;$BUILD_TOOLS ..."
sdkmanager "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" >/dev/null

# 4. local.properties for Gradle
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
log "Wrote $ROOT/local.properties (sdk.dir=$SDK)"

# 5. Gradle wrapper sanity (downloads Gradle on first run)
if [ -x "$ROOT/gradlew" ]; then
  log "Gradle wrapper: $("$ROOT/gradlew" --version 2>/dev/null | grep -m1 Gradle || echo 'will download on first build')"
fi

log "Done. Env: ANDROID_SDK_ROOT=$SDK"
log "Add to your shell:  export ANDROID_SDK_ROOT=$SDK; export PATH=\$PATH:$CLT_DIR/bin:$SDK/platform-tools"
