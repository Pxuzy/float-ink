#!/usr/bin/env bash
# Test suite for check-project-gate.sh tag/version gate.
# Each test creates a throwaway repo, runs the gate, and compares the
# exit code with the expected one. It does NOT touch the real repo.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE="$SCRIPT_DIR/check-project-gate.sh"
PRIVACY="$SCRIPT_DIR/check-git-privacy.sh"
RESOURCES="$SCRIPT_DIR/check-android-resources.sh"
TMPROOT="$(mktemp -d)"
TESTS_RUN=0
TESTS_FAILED=0

cleanup() { rm -rf "$TMPROOT"; }
trap cleanup EXIT

fail() { printf 'FAIL: %s\n' "$1" >&2; TESTS_FAILED=$((TESTS_FAILED + 1)); }
pass() { printf 'PASS: %s\n' "$1"; }

bootstrap() {
  local name="$1"
  local dir="$TMPROOT/$name"
  mkdir -p "$dir/scripts" "$dir/app/src/main/res"
  printf '%s\n' '<manifest xmlns:android="http://schemas.android.com/apk/res/android">' '    <uses-permission android:name="android.permission.INTERNET" />' '</manifest>' > "$dir/app/src/main/AndroidManifest.xml"
  touch "$dir/app/src/main/res/.gitkeep"
  git init --quiet "$dir"
  git -C "$dir" config user.email "test@users.noreply.github.com"
  git -C "$dir" config user.name "Gate Tester"
  for f in check-project-gate.sh check-git-privacy.sh check-android-resources.sh; do
    cp "$SCRIPT_DIR/$f" "$dir/scripts/"
    chmod +x "$dir/scripts/$f"
  done
  git -C "$dir" add app/src/main/res/.gitkeep app/src/main/AndroidManifest.xml
  git -C "$dir" commit --quiet -m "base"
  echo "$dir"
}

make_commit() {
  local repo="$1" msg="$2" vname="$3" vcode="$4" rver="$5"
  printf 'versionName = "%s"\nversionCode = %s\n' "$vname" "$vcode" > "$repo/app/build.gradle.kts"
  printf -- '- 当前版本：`%s`（`versionCode %s`）\n' "$rver" "$vcode" > "$repo/README.md"
  git -C "$repo" add app/build.gradle.kts README.md
  git -C "$repo" commit --allow-empty --quiet -m "commit $msg"
}

run_gate() {
  local label="$1" dir="$2" old_rev="$3" new_rev="$4" local_ref="$5" expected="$6"
  TESTS_RUN=$((TESTS_RUN + 1))
  set +e
  "$dir/scripts/check-project-gate.sh" range "$old_rev" "$new_rev" "$local_ref" >/dev/null 2>&1
  local actual=$?
  set -e
  if [[ "$actual" -eq "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected $expected, got $actual)"
  fi
}

printf '%s\n' "=== version gate integration tests ==="

# ---- correct tag ----
REPO="$(bootstrap correct)"
make_commit "$REPO" v0.3.6 0.3.6 20 0.3.6
OLD="$(git -C "$REPO" rev-parse HEAD)"
make_commit "$REPO" v0.3.7 0.3.7 21 0.3.7
NEW="$(git -C "$REPO" rev-parse HEAD)"
run_gate "correct tag (versionCode 20->21)"     "$REPO" "$OLD" "$NEW" refs/tags/v0.3.7 0
run_gate "tag name mismatch"                    "$REPO" "$OLD" "$NEW" refs/tags/v0.3.5 1
run_gate "versionCode not incremented"          "$REPO" "$OLD" "$OLD" refs/tags/v0.3.6 1

# ---- first-time tag ----
REPO2="$(bootstrap first-time)"
make_commit "$REPO2" v0.3.6 0.3.6 20 0.3.6
OLD2="$(git -C "$REPO2" rev-parse HEAD)"
run_gate "first-time tag (nil tree, fails versionCode)" "$REPO2" "$(git hash-object -t tree /dev/null)" "$OLD2" refs/tags/v0.3.6 1

printf '%s\n' ''
printf '=== RESULTS: %d run, %d failed ===\n' "$TESTS_RUN" "$TESTS_FAILED"
if (( TESTS_FAILED > 0 )); then
  exit 1
fi
exit 0