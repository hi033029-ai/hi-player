#!/usr/bin/env bash
set -euo pipefail
ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${ANDROID_HOME:?The Android SDK must be installed}"
SDKMANAGER=""
for candidate in "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "$ANDROID_HOME/cmdline-tools/16.0/bin/sdkmanager"; do
  if [ -x "$candidate" ]; then SDKMANAGER=$candidate; break; fi
done
if [ -z "$SDKMANAGER" ]; then
  SDKMANAGER=$(find "$ANDROID_HOME/cmdline-tools" -type f -path '*/bin/sdkmanager' | sort -V | tail -n 1)
fi
[ -n "$SDKMANAGER" ] && [ -x "$SDKMANAGER" ] || { echo 'Android SDK manager was not found' >&2; exit 1; }
if [ -n "${GITHUB_ENV:-}" ]; then
  printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$ANDROID_HOME" "$ANDROID_HOME" >> "$GITHUB_ENV"
fi
sudo apt-get update -qq
sudo apt-get install -y meson ninja-build nasm
"$SDKMANAGER" 'ndk;27.2.12479018' 'cmake;3.22.1' 'platforms;android-36.1' 'platforms;android-37.2'
