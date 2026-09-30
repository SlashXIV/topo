#!/usr/bin/env bash
# SDK et NDK Android pour Linux (WSL), dans ~/android-sdk. Le NDK Windows ne sert pas ici.
# Meme version de NDK que l'application (gradle/libs.versions.toml).
set -euo pipefail
SDK="$HOME/android-sdk"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -sSLo /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  unzip -q -o /tmp/cmdline-tools.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
# yes recoit SIGPIPE quand sdkmanager a fini de lire : sans le "|| true", pipefail echoue.
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "ndk;$NDK_VERSION" "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null
echo "SDK pret : $SDK (NDK $NDK_VERSION)"
