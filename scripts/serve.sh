#!/usr/bin/env bash
# =========================================================
# 悬浮讲解笔 — APK 构建 & 快速部署
# =========================================================
# 用法:
#   bash scripts/serve.sh              构建并提供下载链接
#   bash scripts/serve.sh --no-build   不构建，只启动服务
#   bash scripts/serve.sh --port 8080  指定端口
# =========================================================
set -euo pipefail

cd "$(dirname "$0")/.."
PORT=${2:-8080}
BUILD=true

if [ "${1:-}" = "--no-build" ]; then BUILD=false; fi
if [ "${1:-}" = "--port" ]; then PORT="$2"; fi

SELF_IP=$(curl -s ifconfig.me 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo "localhost")

if [ "$BUILD" = true ]; then
    echo "→ 构建 APK..."
    ./gradlew :app:assembleDebug 2>&1 | tail -2
    echo ""
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')

echo "═══════════════════════════════════════════════"
echo "  APK 构建完成"
echo ""
echo "  文件: $APK_PATH"
echo "  大小: $APK_SIZE"
echo ""
echo "  在平板上打开以下地址即可下载安装："
echo ""
echo "  http://$SELF_IP:$PORT/app-debug.apk"
echo ""
echo "  （如果上面不是公网 IP，请用服务器公网 IP 替换）"
echo ""
echo "  按 Ctrl+C 停止服务"
echo "═══════════════════════════════════════════════"
echo ""

cd app/build/outputs/apk/debug/
python3 -m http.server "$PORT"
