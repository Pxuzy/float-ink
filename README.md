# 浮墨 · FloatInk

> 面向讲课、演示与远程指导的 Android 悬浮屏幕讲解笔。

[![Android 29+](https://img.shields.io/badge/Android-29%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Release](https://img.shields.io/github/v/release/Pxuzy/float-ink?display_name=tag&sort=semver)](https://github.com/Pxuzy/float-ink/releases)

FloatInk 在获得系统悬浮窗权限后，以悬浮球方式运行在其他应用之上。点击悬浮球即可打开透明画板，用手指或手写笔进行临时标注；退出画板后回到原应用，不截图、不读取底层内容、不上传笔迹。

- **当前版本：** `0.3.20`（`versionCode 34`）
- **应用包名：** `com.pxuzy.floatingpen`
- **支持系统：** Android 10 / API 29 及以上的手机和平板
- **技术栈：** 原生 Android · Kotlin · Gradle
- **正式发布：** [GitHub Releases](https://github.com/Pxuzy/float-ink/releases)

## 为什么使用 FloatInk

| 场景 | 使用方式 |
|---|---|
| 课堂讲解 | 在课件、浏览器或 PDF 上即时圈画、连线和标注 |
| 远程协作 | 在视频会议或共享内容上强调重点，不需要截屏后再编辑 |
| 现场演示 | 通过悬浮球快速进入和退出画板，尽量不打断当前工作流 |
| 构图与比例讲解 | 使用黄金参考线或两点式斐波那契回撤辅助说明比例关系 |

## 快速开始

### 1. 安装正式版

从 [Releases](https://github.com/Pxuzy/float-ink/releases/latest) 下载最新 APK，在 Android 系统安装器中确认安装。

> 从旧 Debug/测试签名包迁移到正式版时，Android 不允许不同签名覆盖安装：请先确认旧版数据是否需要保留，再卸载旧包并安装正式包。正式版之间使用同一证书签名，可以直接覆盖升级并保留应用数据。

### 2. 授予悬浮窗权限

首次打开应用后，根据系统引导允许“显示在其他应用上层”。随后启用悬浮球。

### 3. 开始标注

```text
打开 FloatInk
  → 授予悬浮窗权限
  → 启用悬浮球
  → 点击悬浮球进入透明画板
  → 选择工具并绘制
  → 点击退出，返回原应用
```

## 功能概览

### 悬浮与绘图

- 可拖动、贴边、自动隐藏的悬浮球
- 透明全屏绘图覆盖层，支持手指与手写笔输入
- 画笔、直线、箭头、矩形、圆形
- 撤销、清空当前图层、退出画板
- 颜色选择、HSV/RGB 调色与最近使用颜色
- 各绘图工具分别保存颜色和线宽；设置变更可同步到运行中的悬浮层

### 画板与图层

- 当前服务生命周期内可维护多个画板和图层
- 画板/图层的新建、切换、重命名、删除、排序与显示隐藏
- 可见图层按顺序合成
- 撤销和清空仅作用于当前图层

> 当前会话数据仅在内存中临时保留。停止悬浮服务是明确的会话清除边界；项目不提供截图、录屏、导出或云端同步。

### 辅助工具

| 工具 | 用途 | 操作方式 |
|---|---|---|
| 黄金参考线 | 快速辅助构图与比例讲解 | 显示水平、垂直两条参考线；可分别拖动，在 `38.2%` 与 `61.8%` 附近吸附 |
| 斐波那契回撤 | 通过两个极值点讲解比例区间 | 拖动创建 `0 / 23.6 / 38.2 / 50 / 61.8 / 78.6 / 100%` 等级线；两个端点调整范围，中间较大的白色点整体移动 |

辅助工具独立于普通笔迹：不进入图层、撤销、清空或会话持久化数据。

### 工具栏与更新

- 悬浮工具栏支持启用/停用、排序和横向滚动
- 适配旋转、分屏和窄屏布局
- App 内可检查 GitHub Releases，下载后交由 Android 系统安装器确认更新
- 更新前校验 APK 包名、版本号与正式签名，避免错误包或不同签名包覆盖安装

## 产品边界与隐私

FloatInk 的目标是轻量、临时、低干扰的屏幕讲解，不是截图编辑器或云端白板。

| 不做的能力 | 说明 |
|---|---|
| 截图、录屏、导出笔迹 | 不读取或保存底层画面，也不导出当前笔迹 |
| 云同步、登录、统计分析 | 不建立账号体系，不上传会话或绘制内容 |
| 无障碍服务、MediaProjection | 不使用无障碍权限，不获取屏幕捕获内容 |
| 相机、麦克风、广泛存储权限 | 不请求与讲解笔无关的敏感权限 |
| iOS / iPad 客户端 | 当前仅支持 Android |
| 静默后台安装 | Android 必须由用户在系统安装器中确认 |

### 权限说明

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮球和透明绘图层 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 保持悬浮服务运行，适配 Android 14+ |
| `POST_NOTIFICATIONS` | 显示前台服务通知（Android 13+） |
| `INTERNET` | 检查 GitHub Release 与下载更新 APK |
| `REQUEST_INSTALL_PACKAGES` | 将已验证 APK 交给系统安装器 |

应用不申请相册、相机、麦克风、无障碍服务、屏幕录制或广泛存储权限。

## 安装与设备设置

### 正式更新

推荐使用 App 内入口：`设置 → 软件更新 → 检查更新`。

也可以直接从 [GitHub Releases](https://github.com/Pxuzy/float-ink/releases) 下载 APK。普通 Android 安装流程需要用户确认，这是系统安全机制。

### 国产 ROM 注意事项

部分设备会限制后台悬浮服务。安装后建议在系统设置中确认：

- 允许显示悬浮窗
- 将省电策略设为“不限制”或等效选项
- 如系统提供，开启自启动并在多任务界面锁定应用

具体路径见 [部署与设备设置说明](docs/DEPLOY.md)。

## 开发与构建

### 环境要求

- JDK 17
- Android SDK
- Android SDK Platform 35 与 Build Tools
- 项目自带 Gradle Wrapper，无需全局安装 Gradle

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

### 常用命令

| 命令 | 说明 |
|---|---|
| `./gradlew testDebugUnitTest` | 运行 JVM / Robolectric 单元测试 |
| `./gradlew :app:assembleDebug` | 构建 Debug APK |
| `./gradlew testDebugUnitTest :app:assembleDebug` | 完整本地验证 |
| `bash scripts/build-test.sh` | 项目封装的构建与测试入口 |
| `bash scripts/serve.sh` | 构建 APK 并启动临时下载服务 |
| `bash scripts/deploy.sh` | 向已连接的 ADB 设备部署 APK |
| `bash scripts/install-git-hooks.sh` | 安装项目 Git 安全钩子 |

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 发布与质量门禁

项目通过 Git hooks 和 GitHub Actions 保持可发布状态：

```text
开发与测试
  → 提交前隐私检查
  → 推送前资源、权限、版本与干净检出构建检查
  → 推送 main：CI 单测、Debug 构建与 Release lint
  → 推送 v* 标签：正式签名 APK、完整性、签名与元数据校验
  → GitHub Release 发布 APK
```

- `pre-commit`：检查暂存内容中的密钥、Token、本机路径与构建产物
- `pre-push`：检查 Android 资源、Manifest 权限白名单、版本标签，并在干净检出中运行测试和 Debug 构建
- Release workflow：使用固定正式证书签名，并校验 APK ZIP 完整性、签名指纹、包名和版本

发布示例：

```bash
./gradlew testDebugUnitTest :app:assembleDebug
git add <变更文件>
git commit -m "类型(范围): 中文标题"
git push origin main

git tag -a vX.Y.Z -m "发布 X.Y.Z"
git push origin vX.Y.Z
```

> 不要使用 `--no-verify` 绕过项目门禁；不要提交 APK、构建目录、签名文件、密钥或本机配置。

## 项目结构

```text
app/src/main/java/com/pxuzy/floatingpen/
├── MainActivity.kt                 # 主界面、设置与更新入口
├── OverlayService.kt               # 悬浮服务生命周期
├── FloatingBubbleView.kt           # 悬浮球交互
├── DrawingOverlayView.kt           # 透明画板、工具栏与触摸路由
├── FibonacciOverlayController.kt   # 回撤对象的创建、端点与整体移动状态机
├── FibonacciGuideRenderer.kt       # 回撤等级线、端点和移动点绘制
├── FloatInkIconView.kt             # 共享单色图标
├── PenSettings.kt                  # 工具、颜色与悬浮栏配置持久化
├── ToolbarLayoutEditorView.kt      # 工具栏布局编辑器
└── core/                           # 平台无关的绘图模型与几何逻辑

app/src/test/java/com/pxuzy/floatingpen/
└── …                               # 核心模型、Overlay、设置、更新与回归测试
```

## 验证状态

### 已自动验证

- JVM / Robolectric 单元测试覆盖绘图模型、悬浮层、设置、服务与更新流程
- Debug APK 可实际构建
- Release 工作流校验 APK 完整性、正式签名、包名与版本号

### 仍需真机验收

自动化构建不能替代真实设备验证。以下场景仍应在目标手机与平板上确认：

- 悬浮窗权限的授予、拒绝与重新授权
- 前台服务启动、停止、后台保活与系统回收
- 手指、手写笔及混合输入的手感
- 透明绘图层对底层 App 触摸的拦截与退出恢复
- 横竖屏、分屏、窄屏、大字体下的工具栏与面板布局
- 黄金参考线、斐波那契回撤端点及整体移动点的触控可用性
- 小米、华为、OPPO、vivo 等系统的后台限制与安装器行为

## 贡献约定

- 开始开发前先同步远端 `main`
- 保持单一、可验证的小步提交
- 提交信息使用中文格式：`类型(范围): 中文标题`，正文用 `-` 列出改动与验证
- 修改后运行相关测试；提交前至少运行 `git diff --check`
- 不将模拟环境结果表述为真机兼容结论

## 许可证

当前许可证尚未确定，项目暂不对外承诺具体开源许可。公开发布前会补充 `LICENSE` 文件并明确使用、分发与贡献规则。
