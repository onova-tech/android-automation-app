#!/usr/bin/env bash
# Installs the Android SDK needed by :app (AGP 9.4.1, compileSdk 37) and warms Gradle.
# :core is pure JVM and only needs JDK 17.
set -euo pipefail
cd "$(dirname "$0")/.."

# The base image ships a Yarn apt source whose GPG key has expired, which makes apt-get update fail
# (and, under set -e, abort this script); Yarn isn't needed here, so disable it.
if [ -f /etc/apt/sources.list.d/yarn.list ]; then
  sudo mv /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/yarn.list.disabled
fi

SDK="${ANDROID_HOME:-/usr/local/android-sdk}"
if [ ! -d "$SDK/platforms/android-37.0" ]; then
  sudo mkdir -p "$SDK/cmdline-tools" && sudo chown -R "$(id -un)" "$SDK"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$tmp/tools.zip" -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
fi

echo "sdk.dir=$SDK" > local.properties   # gitignored
./gradlew --no-daemon -q :core:assemble :app:assembleDebug

# Claude Code CLI (native installer -> ~/.local/bin/claude). Non-fatal: the build above is what matters.
if ! command -v claude >/dev/null 2>&1 && [ ! -x "$HOME/.local/bin/claude" ]; then
  curl -fsSL https://claude.ai/install.sh | bash || echo "WARN: Claude Code install failed; run the installer manually"
fi
