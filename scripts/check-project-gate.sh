#!/usr/bin/env bash
# FloatInk pre-push project integrity gate.
# Usage:
#   scripts/check-project-gate.sh working-tree
#   scripts/check-project-gate.sh range <old> <new> [local-ref]
set -euo pipefail

MODE="${1:-working-tree}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$MODE" == "working-tree" ]]; then
  PRIVACY_ARGS=(staged)
  RESOURCE_ARGS=(working-tree)
elif [[ "$MODE" == "range" && $# -ge 3 && $# -le 4 ]]; then
  PRIVACY_ARGS=(range "$2" "$3")
  RESOURCE_ARGS=(range "$2" "$3")
else
  echo "Usage: $0 working-tree | range <old> <new> [local-ref]" >&2
  exit 2
fi

failed=0
if ! "$ROOT_DIR/scripts/check-git-privacy.sh" "${PRIVACY_ARGS[@]}"; then
  failed=1
fi
if ! "$ROOT_DIR/scripts/check-android-resources.sh" "${RESOURCE_ARGS[@]}"; then
  failed=1
fi

check_manifest_permissions() {
  local manifest permissions permission manifest_revision
  if [[ "$MODE" == "range" ]]; then
    manifest_revision="${3:-${2:-}}"
    manifest="$(git show "$manifest_revision:app/src/main/AndroidManifest.xml" 2>/dev/null || true)"
  else
    manifest="$(<app/src/main/AndroidManifest.xml)"
  fi
  [[ -n "$manifest" ]] || { echo 'BLOCKED: app/src/main/AndroidManifest.xml is missing.' >&2; return 1; }

  # This is the intentionally small product boundary for FloatInk. Any new
  # permission must be explicitly reviewed and added here.
  permissions='SYSTEM_ALERT_WINDOW POST_NOTIFICATIONS FOREGROUND_SERVICE FOREGROUND_SERVICE_SPECIAL_USE INTERNET REQUEST_INSTALL_PACKAGES'
  while IFS= read -r permission; do
    [[ -z "$permission" ]] && continue
    if [[ " $permissions " != *" $permission "* ]]; then
      printf 'BLOCKED: manifest permission %s is not on the FloatInk allowlist.\n' "$permission" >&2
      return 1
    fi
  done < <(printf '%s\n' "$manifest" | sed -nE 's/.*android:name="android\.permission\.([A-Z0-9_]+)".*/\1/p' | sort -u)
  printf 'Manifest permission gate passed.\n'
}

extract_version() {
  local revision="$1" path="$2" pattern="$3"
  git show "$revision:$path" 2>/dev/null | sed -nE "s/$pattern/\\1/p" | head -n1
}

check_tag_version() {
  local old="$1" new="$2" local_ref="$3" tag version_name readme_version version_code old_version_code
  [[ "$local_ref" == refs/tags/v* ]] || return 0

  tag="${local_ref#refs/tags/v}"
  version_name="$(extract_version "$new" app/build.gradle.kts '^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)"')"
  readme_version="$(extract_version "$new" README.md '^-[[:space:]]+\*\*当前版本：\*\*[[:space:]]*`([^`]+)`.*$')"
  if [[ -z "$readme_version" ]]; then
    readme_version="$(extract_version "$new" README.md '^-[[:space:]]+当前版本：[[:space:]]*`([^`]+)`.*$')"
  fi
  version_code="$(extract_version "$new" app/build.gradle.kts '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)')"
  old_version_code="$(extract_version "$old" app/build.gradle.kts '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)')"

  [[ "$tag" == "$version_name" ]] || { printf 'BLOCKED: tag v%s does not match versionName %s.\n' "$tag" "$version_name" >&2; return 1; }
  [[ "$tag" == "$readme_version" ]] || { printf 'BLOCKED: tag v%s does not match README version %s.\n' "$tag" "$readme_version" >&2; return 1; }
  [[ "$version_code" =~ ^[0-9]+$ && "$old_version_code" =~ ^[0-9]+$ ]] || { echo 'BLOCKED: could not read numeric versionCode from tag range.' >&2; return 1; }
  (( version_code > old_version_code )) || { printf 'BLOCKED: versionCode %s must be greater than previous %s.\n' "$version_code" "$old_version_code" >&2; return 1; }
  printf 'Tag/version gate passed (v%s, versionCode %s).\n' "$tag" "$version_code"
}

if [[ "$MODE" == "range" && $# -eq 4 ]]; then
  if ! check_manifest_permissions "$2" "$3" "$3"; then
    failed=1
  fi
  if ! check_tag_version "$2" "$3" "$4"; then
    failed=1
  fi
else
  if [[ "$MODE" == "range" ]]; then
    if ! check_manifest_permissions "$2" "$3"; then
      failed=1
    fi
  elif ! check_manifest_permissions; then
    failed=1
  fi
fi

exit "$failed"