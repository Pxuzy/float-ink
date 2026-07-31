#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
chmod +x scripts/check-git-privacy.sh scripts/check-android-resources.sh .githooks/pre-commit .githooks/pre-push

git config core.hooksPath .githooks
printf '已启用项目 Git 钩子：%s\n' "$(git config --get core.hooksPath)"
printf '%s\n' '提交前：pre-commit 检查暂存内容' '推送前：pre-push 检查待推送提交'
