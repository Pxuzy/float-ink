#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
    echo "错误：请先设置 ANDROID_HOME 或 ANDROID_SDK_ROOT。" >&2
    exit 1
fi

./gradlew :app:compileDebugKotlin
