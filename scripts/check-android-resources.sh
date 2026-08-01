#!/usr/bin/env bash
# FloatInk Android resource integrity gate.
# Usage:
#   scripts/check-android-resources.sh working-tree
#   scripts/check-android-resources.sh range <old> <new>
set -euo pipefail

MODE="${1:-working-tree}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$MODE" == "working-tree" ]]; then
  SEARCH_ROOT="$ROOT_DIR/app/src/main"
elif [[ "$MODE" == "range" && $# -eq 3 ]]; then
  SNAPSHOT="$(mktemp -d)"
  trap 'rm -rf "$SNAPSHOT"' EXIT
  git archive "$3" app/src/main | tar xf - -C "$SNAPSHOT"
  SEARCH_ROOT="$SNAPSHOT/app/src/main"
else
  echo "Usage: $0 working-tree | range <old> <new>" >&2
  exit 2
fi

failures=0
report() {
  printf 'BLOCKED: %s\n' "$1" >&2
  failures=$((failures + 1))
}

refs_file="$(mktemp)"
paths_file="$(mktemp)"
trap 'rm -f "$refs_file" "$paths_file"' EXIT

rg -o -P --no-filename '(?<!android\.)R\.(drawable|mipmap|layout|string|color|xml|menu|font|anim|animator|id)\.[A-Za-z_][A-Za-z0-9_]*' \
  "$SEARCH_ROOT" --glob '*.kt' --glob '*.java' --glob '*.xml' 2>/dev/null \
  | sort -u > "$refs_file" || true

while read -r ref; do
  [[ -z "$ref" ]] && continue
  type="${ref#R.}"
  type="${type%%.*}"
  name="${ref##*.}"
  found=0

  if [[ "$type" == "id" ]]; then
    if rg -q "name=\"$name\"" "$SEARCH_ROOT"/res --glob '*.xml' 2>/dev/null; then
      found=1
    fi
  elif [[ "$type" == "string" || "$type" == "color" || "$type" == "menu" || "$type" == "font" || "$type" == "layout" || "$type" == "xml" || "$type" == "anim" || "$type" == "animator" ]]; then
    if rg -q "(name|resourceName)=\"$name\"|/${name}(\.|\"|$)" "$SEARCH_ROOT"/res --glob '*.xml' 2>/dev/null; then
      found=1
    fi
  else
    if find "$SEARCH_ROOT"/res -type f \( -name "$name.*" -o -name "$name" -o -name "$name-*.*" \) -print -quit | grep -q .; then
      found=1
    fi
  fi

  if [[ "$found" -eq 0 ]]; then
    report "missing Android resource: $ref"
  fi
done < "$refs_file"

# Untracked resource check: range mode checks file presence inside the snapshot.
# working-tree mode checks Git tracked vs. untracked files in the real repo.
if [[ "$MODE" == "range" ]]; then
  if ! git cat-file -e "$3":app/src/main 2>/dev/null; then
    printf 'Android resource integrity skipped (no app/src/main in commit %s).\n' "$3"
    exit 0
  fi
  cd "$ROOT_DIR"
  git ls-files --others --exclude-standard "$ROOT_DIR/app/src/main/res" | sort > "$paths_file"
  while read -r untracked_path; do
    [[ -z "$untracked_path" ]] && continue
    untracked_name="$(basename "$untracked_path" | sed 's/\.[^.]*$//')"
    if rg -q "R\.[A-Za-z0-9_]+\.${untracked_name}\b" "$SEARCH_ROOT" --glob '*.kt' --glob '*.java' --glob '*.xml' 2>/dev/null; then
      report "referenced Android resource is untracked: $untracked_path"
    fi
  done < "$paths_file"
else
  git ls-files --others --exclude-standard app/src/main/res | sort > "$paths_file"
  while read -r path; do
    [[ -z "$path" ]] && continue
    if rg -q "R\.[A-Za-z0-9_]+\.$(basename "$path" | sed 's/\.[^.]*$//')\b" app/src/main --glob '*.kt' --glob '*.java' --glob '*.xml' 2>/dev/null; then
      report "referenced Android resource is untracked: $path"
    fi
  done < "$paths_file"
fi

if (( failures > 0 )); then
  cat >&2 <<'EOF'
Android resource integrity check failed. Track the referenced resource or
remove the stale reference before pushing.
EOF
  exit 1
fi
printf 'Android resource integrity check passed (%s).\n' "$MODE"