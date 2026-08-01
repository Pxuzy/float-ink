#!/usr/bin/env bash
# Build only files present in a Git revision, catching local-only resources.
set -euo pipefail

REVISION="${1:-HEAD}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/floatink-clean-build.XXXXXX")"
cleanup() { rm -rf "$BUILD_DIR"; }
trap cleanup EXIT

git -C "$ROOT_DIR" archive --format=tar "$REVISION" | tar -xf - -C "$BUILD_DIR"
cd "$BUILD_DIR"
[[ -x ./gradlew ]] || { echo "BLOCKED: Git revision $REVISION has no executable Gradle wrapper." >&2; exit 1; }
# CI and developer shells may not export the SDK variable. Keep the clean
# checkout independent from local.properties while still using the installed SDK.
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Android/Sdk" ]]; then
  export ANDROID_HOME="$HOME/Android/Sdk"
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo 'BLOCKED: Android SDK not found; set ANDROID_HOME.' >&2
  exit 1
fi
echo "Clean checkout build: $REVISION"
./gradlew testDebugUnitTest :app:assembleDebug