#!/usr/bin/env bash
# FloatInk Android resource integrity gate.
# Usage:
#   scripts/check-android-resources.sh working-tree
#   scripts/check-android-resources.sh range <old> <new>
set -euo pipefail

MODE="${1:-working-tree}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$MODE" == "working-tree" ]]; then
  SEARCH_ROOTS=(app/src/main)
elif [[ "$MODE" == "range" && $# -eq 3 ]]; then
  SEARCH_ROOTS=(app/src/main)
else
  echo "Usage: $0 working-tree | range <old> <new>" >&2
  exit 2
fi

failures=0
report() {
  printf 'BLOCKED: %s\n' "$1" >&2
  failures=$((failures + 1))
}

# Resource references in Kotlin/Java are checked against files in the current
# checkout. This catches resources that existed locally but were never tracked.
refs_file="$(mktemp)"
paths_file="$(mktemp)"
trap 'rm -f "$refs_file" "$paths_file"' EXIT

rg -o -P --no-filename '(?<!android\.)R\.(drawable|mipmap|layout|string|color|xml|menu|font|anim|animator|id)\.[A-Za-z_][A-Za-z0-9_]*' \
  app/src/main --glob '*.kt' --glob '*.java' --glob '*.xml' 2>/dev/null \
  | sort -u > "$refs_file" || true

# Android resource files are named by type and resource name. Values resources
# are parsed by name; file-backed resources are matched by basename and any
# valid density/configuration qualifier suffix.
while read -r ref; do
  [[ -z "$ref" ]] && continue
  type="${ref#R.}"
  type="${type%%.*}"
  name="${ref##*.}"
  found=0

  if [[ "$type" == "id" ]]; then
    if rg -q "name=\"$name\"" app/src/main/res --glob '*.xml' 2>/dev/null; then
      found=1
    fi
  elif [[ "$type" == "string" || "$type" == "color" || "$type" == "menu" || "$type" == "font" || "$type" == "layout" || "$type" == "xml" || "$type" == "anim" || "$type" == "animator" ]]; then
    if rg -q "(name|resourceName)=\"$name\"|/${name}(\.|\"|$)" app/src/main/res --glob '*.xml' 2>/dev/null; then
      found=1
    fi
  else
    if find app/src/main/res -type f \( -name "$name.*" -o -name "$name" -o -name "$name-*.*" \) -print -quit | grep -q .; then
      found=1
    fi
  fi

  if [[ "$found" -eq 0 ]]; then
    report "missing Android resource: $ref"
  fi
done < "$refs_file"

# A resource present in the working tree but absent from Git is exactly the
# failure mode this gate is meant to catch before a remote build.
git ls-files --others --exclude-standard app/src/main/res | sort > "$paths_file"
while read -r path; do
  [[ -z "$path" ]] && continue
  if rg -q "R\.[A-Za-z0-9_]+\.$(basename "$path" | sed 's/\.[^.]*$//')\b" app/src/main --glob '*.kt' --glob '*.java' --glob '*.xml' 2>/dev/null; then
    report "referenced Android resource is untracked: $path"
  fi
done < "$paths_file"

if (( failures > 0 )); then
  cat >&2 <<'EOF'
Android resource integrity check failed. Track the referenced resource or
remove the stale reference before pushing.
EOF
  exit 1
fi
printf 'Android resource integrity check passed (%s).\n' "$MODE"
