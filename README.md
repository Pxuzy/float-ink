# 浮墨（FloatInk）

一个轻量、低干扰的 Android 悬浮屏幕讲解笔，适用于手机和平板。

> 在任意允许悬浮窗的应用上打开悬浮画板，用手指或手写笔临时标注屏幕内容；退出后，本次笔迹会被清除，不保存、不截图、不上传。

- GitHub：<https://github.com/Pxuzy/float-ink>
- 应用包名：`com.pxuzy.floatingpen`
- 当前版本：`0.3.7`（`versionCode 21`）
- 技术栈：原生 Android / Kotlin / Gradle
- 支持范围：Android 29+（手机和平板）

## 核心体验

```text
打开 App
  → 授予悬浮窗权限
  → 启用悬浮球
  → 点击悬浮球进入透明画板
  → 手指或手写笔绘制标注
  → 退出画板
  → 清除本次笔迹并返回原 App
```

## 当前能力（代码已实现，真机仍需验收）

- 可拖动、贴边和自动隐藏的悬浮按钮
- 透明全屏绘图覆盖层
- 手指与手写笔输入
- 画笔、直线、箭头、矩形、圆形工具
- 撤销、清空和退出
- 颜色选择与自定义 HSV/RGB 调色，RGB 通道即时限制为 0–255
- 各绘图工具独立保存颜色和线宽，并在 App 内实时预览
- 多画板、多图层合成、排序与真实显隐
- 箭头头部比例调节
- 悬浮按钮透明度、自动隐藏和隐藏延迟设置
- 悬浮工具栏排序、启用/停用和横向滚动
- 设置修改后可实时同步到正在运行的悬浮层
- 旋转、分屏和窄屏布局适配
- 从 GitHub Releases 检查、下载并交给系统安装更新 APK

## 明确不做

当前版本不包含以下能力：

- 截图、录屏、导出或保存笔迹
- 云同步、登录、分析统计
- 无障碍服务、MediaProjection 或读取底层 App 内容
- iOS/iPad 版本
- 完全静默的后台安装更新（Android 必须由用户确认安装）

## 构建环境

需要以下工具：

- JDK 17
- Android SDK
- Android SDK Platform 35
- Android Build Tools
- Gradle Wrapper（项目已包含）

项目使用 Gradle Wrapper，不需要手动安装 Gradle。构建前请确保 `ANDROID_HOME` 已指向 Android SDK，例如：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

## 构建与测试

在项目根目录执行：

```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Debug APK
./gradlew :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Git 隐私安全钩子

项目提供提交前和推送前的隐私安全检查，阻止私钥、Token、密码、本机绝对路径、IP、个人邮箱以及构建产物进入 Git。

首次使用时执行：

```bash
bash scripts/install-git-hooks.sh
```

之后：

- `pre-commit` 检查暂存内容；
- `pre-push` 检查即将提交的范围；
- `pre-push` 通过单一的 `check-project-gate.sh` 融合隐私、Android 资源、Manifest 权限白名单和版本标签检查，防止源码引用了只存在于本机、但未进入 Git 的 `R.drawable` 等资源；随后从待推送提交的干净 Git 检出运行单元测试和 Debug 构建；
- 检查失败时必须移除敏感内容或改为环境变量/占位符，不能用 `--no-verify` 绕过正常发布流程。

也可以使用项目脚本：

```bash
bash scripts/build-test.sh
```

## 安装到 Android 设备

### 方式一：浏览器下载

适合远程平板：

```bash
bash scripts/serve.sh
```

脚本会构建 APK 并启动临时 HTTP 下载服务。将终端显示的地址复制到平板浏览器，下载后按系统提示安装。

详细说明：[`docs/DEPLOY.md`](docs/DEPLOY.md)

### 方式二：ADB 无线安装

平板开启无线调试并连接后：

```bash
adb connect <平板IP>:<端口>
bash scripts/deploy.sh
```

此方式适合开发迭代，不要求平板和服务器通过 USB 连接。

## 权限说明

App 仅申请悬浮画板运行所需的系统权限：

- `SYSTEM_ALERT_WINDOW`：显示悬浮按钮和透明绘图层
- `FOREGROUND_SERVICE`：保持悬浮服务稳定运行
- `FOREGROUND_SERVICE_SPECIAL_USE`：适配 Android 14+ 的悬浮服务类型
- `POST_NOTIFICATIONS`：显示前台服务通知

App 不申请相册、存储、相机、麦克风、无障碍服务或屏幕录制权限。

国产 Android 系统可能还需要手动设置后台运行、自启动和省电策略。详见 [`docs/DEPLOY.md`](docs/DEPLOY.md)。

## 项目结构

```text
app/src/main/java/com/pxuzy/floatingpen/
├── MainActivity.kt              # 主界面与设置页
├── OverlayService.kt            # 悬浮窗服务生命周期
├── FloatingBubbleView.kt        # 悬浮按钮
├── DrawingOverlayView.kt        # 透明绘图层与工具栏
├── HsvColorPickerView.kt        # HSV 调色器
├── ToolbarLayoutEditorView.kt   # 工具栏布局编辑器
├── PenSettings.kt               # 画笔配置与持久化
└── core/
    ├── DrawingElement.kt        # 平台无关的绘图模型
    ├── ToolStyle.kt             # 工具样式
    └── ArrowGeometry.kt         # 箭头几何计算
```

测试代码位于：

```text
app/src/test/java/com/pxuzy/floatingpen/
```

## 当前验证状态

自动化单元测试和 Debug APK 构建已完成，但这不等同于真实设备验收。真实 Android 手机/平板验证仍需要在目标设备上完成，重点包括：

- 悬浮窗权限授予与拒绝
- 悬浮服务启动、停止和后台保活
- 手指与手写笔绘制
- 旋转、分屏和窄屏工具栏
- 透明度、自动隐藏和位置恢复
- 国产 ROM 的后台限制

## 远程更新流程

项目不在 Android 设备上执行 `git pull`，而是使用 GitHub Releases 发布 APK：

```text
git push / 创建 v0.3.0 标签
  → GitHub Actions 拉取仓库、测试并构建 APK
  → GitHub Release 发布新版本
  → App 检查 GitHub Releases
  → 下载 APK 并调用 Android 系统安装器
```

普通 Android 设备在安装更新时仍需要用户确认，这是系统安全限制。GitHub Actions 使用 GitHub Secrets 注入的固定 release keystore，所有后续版本保持同一签名，可以正常覆盖安装。

发布示例：

```bash
git add .
git commit -m "chore(release): 准备 v0.3.0 发布"
git push origin main
git tag v0.3.0
git push origin v0.3.0
```

App 内入口：`设置 → 软件更新 → 检查远程更新`。更新地址固定为 `Pxuzy/float-ink`，只接受 Release 中的 `.apk` 资产。

## 开发规范

- 保持 `main` 分支可构建
- 每次只提交一个逻辑变化
- 使用中文 Conventional Commit，例如：`feat: 添加悬浮工具栏自动隐藏`
- 提交前运行单元测试和 Debug 构建
- 不提交 APK、构建目录、签名文件、密钥和本机配置
- 真实设备行为以测试结果为准，不以模拟环境结果替代

## 许可证

许可证尚未确定。项目早期版本暂不对外承诺具体开源许可证，发布前会单独确定并补充 LICENSE 文件。
