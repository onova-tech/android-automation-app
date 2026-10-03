#!/usr/bin/env bash
# Installs the Android SDK needed by :app (AGP 9.4.1, compileSdk 37) and warms Gradle.
# :core is pure JVM and only needs JDK 17.
set -euo pipefail
cd "$(dirname "$0")/.."

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
