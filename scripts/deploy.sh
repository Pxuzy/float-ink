#!/usr/bin/env bash
# =========================================================
# 悬浮讲解笔 — 一键部署脚本
# 通过 ADB WiFi 直接从服务器安装到平板
# 并自动处理国产 ROM 的电池/权限限制
# =========================================================
set -euo pipefail

SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SELF_DIR/../app/build/outputs/apk/debug/app-debug.apk"
PKG="com.pxuzy.floatingpen"

echo "╔═══════════════════════════════════════════════╗"
echo "║       悬浮讲解笔  —  一键部署                ║"
echo "╚═══════════════════════════════════════════════╝"
echo ""

# ── 步骤 0: 确保 APK 已构建 ──
if [ ! -f "$APK" ]; then
    echo "→ APK 未构建，先构建..."
    cd "$SELF_DIR/.."
    ./gradlew :app:assembleDebug 2>&1 | tail -3
    echo ""
fi
echo "✓ APK: $(ls -lh "$APK" | awk '{print $5}')"

# ── 步骤 1: 连接设备 ──
echo ""
echo "┌──────────────────────────────────────────────┐"
echo "│  第一步：ADB WiFi 连接                       │"
echo "│                                              │"
echo "│  在你的平板上：                               │"
echo "│  ① 开启「开发者选项」→「无线调试」            │"
echo "│  ② 开启后点「配对码配对」                     │
echo "│  ③ 记下 IP:端口 和 配对码                     │"
echo "│                                              │"
echo "│  在本终端输入以下命令：                       │"
echo "│  adb pair IP:端口 配对码                      │"
echo "│  adb connect IP:端口                          │"
echo "│                                              │"
echo "│  或先 USB 连接一次然后：                      │"
echo "│  adb tcpip 5555                              │"
echo "│  adb connect 平板IP:5555                     │"
echo "└──────────────────────────────────────────────┘"
echo ""

if ! adb devices | grep -q "device$"; then
    echo "⚠ 当前没有已连接的设备。"
    echo "  请先运行 adb connect <平板IP>:5555"
    echo ""
    echo "  或直接输入以下命令自动连接（替换 IP）："
    read -rp "  平板 IP 地址: " TABLET_IP
    if [ -n "$TABLET_IP" ]; then
        adb connect "$TABLET_IP:5555" 2>&1 || true
    fi
    if ! adb devices | grep -q "device$"; then
        echo "✗ 连接失败。请手工连接后重试。"
        exit 1
    fi
fi

DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo "✓ 已连接: $DEVICE"

# ── 步骤 2: 安装 APK ──
echo ""
echo "→ 安装 APK..."
adb -s "$DEVICE" install -r "$APK" 2>&1 | tail -1
echo "✓ 安装完成"

# ── 步骤 3: 自动处理 OEM 限制 ──
echo ""
echo "┌──────────────────────────────────────────────┐"
echo "│  第二步：自动解除系统限制                    │"
echo "└──────────────────────────────────────────────┘"
echo ""

# 3a) 授予悬浮窗权限
echo "→ 授予悬浮窗权限..."
adb -s "$DEVICE" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>/dev/null && echo "  ✓ SYSTEM_ALERT_WINDOW" || echo "  ⚠ 需手动授权（将打开设置页面）"

# 3b) 忽略电池优化
echo "→ 关闭电池优化..."
adb -s "$DEVICE" shell dumpsys deviceidle whitelist +"$PKG" 2>/dev/null && echo "  ✓ deviceidle whitelist"
adb -s "$DEVICE" shell am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -d "package:$PKG" 2>/dev/null &
sleep 1
echo "  ⚠ 请在设置中选「无限制」"

# 3c) 关闭省电策略 (MIUI/HyperOS 专用)
echo "→ MIUI/HyperOS 省电策略..."
adb -s "$DEVICE" shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$PKG" 2>/dev/null &
sleep 1
echo "  ⚠ 请在「应用信息」→「省电策略」选「无限制」"

# 3d) 自启动 (MIUI 专用)
echo "→ MIUI 自启动..."
adb -s "$DEVICE" shell am start -a android.settings.ACTION_AUTOSTART_SETTINGS 2>/dev/null &
sleep 1
echo "  ⚠ 请在列表中开启「悬浮讲解笔」自启动"

# 3e) 锁住后台任务
echo "→ 建议手动操作: 在多任务界面下拉锁定本 App"
echo "  (多任务界面 → 找到悬浮讲解笔 → 下拉锁定)"

echo ""
echo "┌──────────────────────────────────────────────┐"
echo "│  手动检查清单（在平板上操作）                 │"
echo "│                                              │"
echo "│  □ 悬浮窗权限：设置→应用→悬浮讲解笔           │"
echo "│    → 显示悬浮窗 → 允许                       │"
echo "│                                              │"
echo "│  □ 省电策略：设置→应用→悬浮讲解笔             │"
echo "│    → 省电策略 → 无限制                       │"
echo "│                                              │"
echo "│  □ 自启动：设置→应用→权限→自启动             │"
echo "│    → 开启悬浮讲解笔                          │"
echo "│                                              │"
echo "│  □ 多任务锁定：多任务界面→下拉锁定            │"
echo "│                                              │"
echo "│  做完以上操作后，打开 App 即可使用             │"
echo "└──────────────────────────────────────────────┘"

echo ""
echo "✓ 部署完成！后续只需运行:"
echo "  bash scripts/deploy.sh"
echo ""
echo "  脚本会自动检测设备并安装最新 APK。"
