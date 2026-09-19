#!/usr/bin/env bash
# Exercise both gate modes in an isolated repository, including binary-only diffs.
set -euo pipefail
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMPROOT="$(mktemp -d)"
trap 'rm -rf "$TMPROOT"' EXIT
mkdir -p "$TMPROOT/scripts"
cp "$SOURCE_DIR/check-git-privacy.sh" "$TMPROOT/scripts/"
cd "$TMPROOT"
git init -q
git config user.name 'Privacy Gate Test'
git config user.email 'test@users.noreply.github.com'
git add scripts
git commit -qm base
base="$(git rev-parse HEAD)"
count=0
check() {
  local expected="$1" actual=0
  shift
  bash scripts/check-git-privacy.sh "$@" >result.log 2>&1 || actual=$?
  if [[ "$actual" != "$expected" ]]; then
    printf 'FAIL: %s expected=%s actual=%s\n' "$*" "$expected" "$actual"
    cat result.log
    exit 1
  fi
  count=$((count + 1))
}
check 0 staged
for path in .ai/memory.db .emu/ram.img release.jks .env assets/icon.png; do
  git read-tree "$base"
  mkdir -p "$(dirname "$path")"
  if [[ "$path" == .env ]]; then
    touch "$path"
  else
    printf '\0binary fixture\0' >"$path"
  fi
  git add -- "$path"
  expected=1
  [[ "$path" == assets/icon.png ]] && expected=0
  check "$expected" staged
  tree="$(git write-tree)"
  commit="$(printf 'fixture\n' | git commit-tree "$tree" -p "$base")"
  check "$expected" range "$base" "$commit"
done
git read-tree "$base"
private_email="fixture@$(printf 'example.invalid')"
GIT_AUTHOR_EMAIL="$private_email" check 1 staged
GIT_COMMITTER_EMAIL="$private_email" check 1 staged
tree="$(git write-tree)"
bad_author="$(printf 'author fixture\n' | GIT_AUTHOR_EMAIL="$private_email" git commit-tree "$tree" -p "$base")"
bad_committer="$(printf 'committer fixture\n' | GIT_COMMITTER_EMAIL="$private_email" git commit-tree "$tree" -p "$base")"
check 1 range "$base" "$bad_author"
check 1 range "$base" "$bad_committer"
safe_child="$(printf 'safe child\n' | git commit-tree "$tree" -p "$bad_author")"
check 0 range "$bad_author" "$safe_child"
printf 'Privacy gate regression checks passed: %s\n' "$count"
