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

extract_version() {
  local revision="$1" path="$2" pattern="$3"
  git show "$revision:$path" 2>/dev/null | sed -nE "s/$pattern/\\1/p" | head -n1
}

check_tag_version() {
  local old="$1" new="$2" local_ref="$3" tag version_name readme_version version_code old_version_code
  [[ "$local_ref" == refs/tags/v* ]] || return 0

  tag="${local_ref#refs/tags/v}"
  version_name="$(extract_version "$new" app/build.gradle.kts '^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)"')"
  readme_version="$(extract_version "$new" README.md '^- 当前版本：`([^`]+)`.*$')"
  version_code="$(extract_version "$new" app/build.gradle.kts '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)')"
  old_version_code="$(extract_version "$old" app/build.gradle.kts '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)')"

  [[ "$tag" == "$version_name" ]] || { printf 'BLOCKED: tag v%s does not match versionName %s.\n' "$tag" "$version_name" >&2; return 1; }
  [[ "$tag" == "$readme_version" ]] || { printf 'BLOCKED: tag v%s does not match README version %s.\n' "$tag" "$readme_version" >&2; return 1; }
  [[ "$version_code" =~ ^[0-9]+$ && "$old_version_code" =~ ^[0-9]+$ ]] || { echo 'BLOCKED: could not read numeric versionCode from tag range.' >&2; return 1; }
  (( version_code > old_version_code )) || { printf 'BLOCKED: versionCode %s must be greater than previous %s.\n' "$version_code" "$old_version_code" >&2; return 1; }
  printf 'Tag/version gate passed (v%s, versionCode %s).\n' "$tag" "$version_code"
}

if [[ "$MODE" == "range" && $# -eq 4 ]]; then
  if ! check_tag_version "$2" "$3" "$4"; then
    failed=1
  fi
fi

exit "$failed"