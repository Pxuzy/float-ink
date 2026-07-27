# 部署指南

## 前提

开发在服务器上，平板通过浏览器下载 APK，手动安装。

---

## 构建并部署到平板

### 方法 1：HTTP 直连下载（推荐）

**构建机器上运行：**

```bash
cd /path/to/float-ink
bash scripts/serve.sh
```

输出示例：

```
http://<构建机器地址>:8080/app-debug.apk
```

**平板上操作：**
- 平板浏览器打开这个地址 → 自动下载 APK
- 下载完成后点击安装
- 安装后打开 App → 授予悬浮窗权限 → 启用悬浮球

> 注：如果 8080 端口被防火墙阻止，用 `--port 8081` 等换一个端口。
> 使用完按 `Ctrl+C` 停止 HTTP 服务。

### 方法 2：通过 Telegram（备选）

在对话中让 AI 发送 APK 文件，直接点击下载。

---

## 国产 ROM 权限设置（一次性的）

小米/华为/OPPO/vivo 等系统会默认禁止后台悬浮窗。安装后做一次以下设置：

| 设置项 | 路径 |
|---|---|
| 悬浮窗权限 | 设置 → 应用管理 → 悬浮讲解笔 → 权限管理 → 显示悬浮窗 → 允许 |
| 省电策略 | 设置 → 应用管理 → 悬浮讲解笔 → 省电策略 → 无限制 |
| 自启动 | 设置 → 应用管理 → 权限 → 自启动 → 开启悬浮讲解笔 |
| 多任务锁定 | 打开多任务界面 → 找到悬浮讲解笔 → 按住下拉锁定（加锁图标） |

做完以上设置后 App 即可在后台稳定运行悬浮球。

---

## 开发工作流

```text
改代码 → bash scripts/serve.sh
       → 平板浏览器下载安装
       → 测试 → 再改 → 重复
```

## 远程仓库更新（推荐用于日常使用）

App 不会在设备上执行 `git pull`。设备没有 Git 工作区，直接拉取源码也不能替换已安装的 Android 包。正确流程是由 GitHub Actions 把远程仓库构建成 APK，再由 App 下载 Release APK：

```text
修改代码 → push main → 创建 v* 标签
  → Actions 运行测试和 assembleDebug
  → 自动创建 GitHub Release 并上传 APK
  → App：设置 → 软件更新 → 检查远程更新
  → 下载完成 → 系统安装器确认更新
```

发布命令：

```bash
git add .
git commit -m "feat(update): 接入 GitHub Releases 远程更新"
git push origin main
git tag v0.2.0
git push origin v0.2.0
```

说明：

- Tag 必须使用 `v` 开头，例如 `v0.2.0`。
- Release 必须包含 `.apk` 文件，App 会忽略其他资产。
- 普通 Android 设备会弹出系统安装确认，不能静默安装。
- 当前 Actions 产出 Debug APK，适合个人侧载；正式发布前应配置 release keystore，并保持签名一致，否则 Android 不允许覆盖安装。
- App 需要网络权限仅用于访问 GitHub Releases API 和下载 APK，不读取屏幕内容、不上传笔迹。
