#!/usr/bin/env bash
# 悬浮画笔 Git privacy/security gate.
# Usage:
#   scripts/check-git-privacy.sh staged
#   scripts/check-git-privacy.sh range <old> <new>
set -euo pipefail

MODE="${1:-staged}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0
report() {
  printf 'BLOCKED: %s\n' "$1" >&2
  failures=$((failures + 1))
}

# Check only text represented by Git. Binary assets are not searched as text.
if [[ "$MODE" == "staged" ]]; then
  DIFF_CMD=(git diff --cached --no-ext-diff --unified=0 -- . ':(exclude)gradle/wrapper/gradle-wrapper.jar')
elif [[ "$MODE" == "range" && $# -eq 3 ]]; then
  DIFF_CMD=(git diff "$2" "$3" --no-ext-diff --unified=0 -- . ':(exclude)gradle/wrapper/gradle-wrapper.jar')
else
  echo "Usage: $0 staged | range <old> <new>" >&2
  exit 2
fi

# Check effective identities (including environment/--author overrides), not
# just user.email. Range mode checks new commits without revisiting old history.
check_identity() {
  local label="$1" email="$2"
  if [[ ! "$email" =~ ^[A-Za-z0-9+._-]+@users\.noreply\.github\.com$ ]]; then
    report "$label must use a GitHub noreply email (address redacted)"
  fi
}
if [[ "$MODE" == "staged" ]]; then
  for role in AUTHOR COMMITTER; do
    identity="$(git var "GIT_${role}_IDENT")"
    email="${identity##*<}"
    check_identity "$role" "${email%%>*}"
  done
else
  if git cat-file -e "$2^{commit}" 2>/dev/null; then
    commits="$(git rev-list "$2..$3")"
  else
    commits="$(git rev-list "$3" --not --remotes)"
  fi
  while IFS= read -r commit; do
    [[ -z "$commit" ]] && continue
    check_identity "${commit:0:8} author" "$(git show -s --format=%ae "$commit")"
    check_identity "${commit:0:8} committer" "$(git show -s --format=%ce "$commit")"
  done <<<"$commits"
fi

DIFF_FILE="$(mktemp)"
trap 'rm -f "$DIFF_FILE"' EXIT
"${DIFF_CMD[@]}" > "$DIFF_FILE"

# Search added lines only. Existing historical content is reported by the
# pre-push range check when it is part of the outgoing commits.
ADDED="$(awk '/^\+[^+]/ {sub(/^\+/, ""); print}' "$DIFF_FILE")"
# Binary-only and empty-file changes still require the path checks below.

check_added() {
  local label="$1" pattern="$2"
  if printf '%s\n' "$ADDED" | grep -EIn "$pattern" >/tmp/floatink-privacy-hit.$$ 2>/dev/null; then
    while IFS= read -r line; do report "$label: $line"; done </tmp/floatink-privacy-hit.$$
  fi
  rm -f /tmp/floatink-privacy-hit.$$
}

check_added "private key material" '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
check_added "GitHub/API token pattern" 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}'
check_added "hard-coded credential assignment" '(api[_-]?key|access[_-]?token|client[_-]?secret|password)[[:space:]]*[=:][[:space:]]*["'"'][^"'"']{8,}["'"']'
check_added "absolute local home path" '/home/[A-Za-z0-9._-]+|/Users/[A-Za-z0-9._-]+'
check_added "IPv4 address; use a placeholder or environment value" '\b([0-9]{1,3}\.){3}[0-9]{1,3}\b'
check_added_email() {
  local label="personal email; use a GitHub noreply address or placeholder"
  if printf '%s\n' "$ADDED" | grep -EIn '\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b' | grep -Ev '@users\.noreply\.github\.com\b' >/tmp/floatink-privacy-hit.$$ 2>/dev/null; then
    while IFS= read -r line; do report "$label: $line"; done </tmp/floatink-privacy-hit.$$
  fi
  rm -f /tmp/floatink-privacy-hit.$$
}
check_added_email
# Local secret/config files are checked as paths, not as arbitrary added text.
# This avoids false positives for safe documentation and ignore-rule lines such
# as `local.properties` or `*.jks`.
if [[ "$MODE" == "staged" ]]; then
  CHANGED_PATHS="$(git diff --cached --name-only --diff-filter=ACMR)"
else
  CHANGED_PATHS="$(git diff "$2" "$3" --name-only --diff-filter=ACMR)"
fi
if printf '%s\n' "$CHANGED_PATHS" | grep -EIn '(^|/)(\.env($|\.)|local\.properties$|[^/]+\.(jks|keystore|p12|pfx|pem|key))' >/tmp/floatink-privacy-hit.$$ 2>/dev/null; then
  while IFS= read -r line; do report "local secret/config artifact: $line"; done </tmp/floatink-privacy-hit.$$
fi
rm -f /tmp/floatink-privacy-hit.$$

# Generated/local artifacts are blocked by checking changed path names below.
STAGED_PATHS="$CHANGED_PATHS"
if printf '%s\n' "$STAGED_PATHS" | grep -EIn '(^|/)(\.superpowers|\.ai|\.emu|app/build|build|\.gradle)(/|$)' >/tmp/floatink-privacy-hit.$$ 2>/dev/null; then
  while IFS= read -r line; do report "generated/local artifact path: $line"; done </tmp/floatink-privacy-hit.$$
fi
rm -f /tmp/floatink-privacy-hit.$$

check_added "large artifact (>10 MB); use Git LFS or exclude from tracking" '^Binary files .* differ|^Files .* differ'

if [[ "$MODE" == "staged" ]]; then
  STAGED_LIST="$(git diff --cached --name-only --diff-filter=ACMR)"
else
  STAGED_LIST="$(git diff "$2" "$3" --name-only --diff-filter=ACMR)"
fi
if printf '%s\n' "$STAGED_LIST" | grep -EIn '(^|/)(artifacts/|current-.*\.png|floating-icon.*\.png|floatink-open.*\.png|\.psd|\.ai|\.aep|\.zip|\.mp4)(/|$)' >/tmp/floatink-privacy-hit.$$ 2>/dev/null; then
  while IFS= read -r line; do report "local design/material asset (not intended for the repo): $line"; done </tmp/floatink-privacy-hit.$$
fi
rm -f /tmp/floatink-privacy-hit.$$

if (( failures > 0 )); then
  cat >&2 <<'EOF'
Privacy gate failed. Remove the finding from the staged diff, replace it with a
portable placeholder/environment variable, or add a narrowly justified rule to
this script. Do not bypass the hook for a normal push.
EOF
  exit 1
fi
printf 'Privacy gate passed (%s).\n' "$MODE"
