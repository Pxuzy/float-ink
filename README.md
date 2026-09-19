<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/ic_launcher_floatink.png" width="128" height="128" alt="悬浮画笔 FloatPen 图标" />
</p>

<h1 align="center">悬浮画笔 · FloatPen</h1>

<p align="center">
  <strong>面向 Android 手机和平板的悬浮屏幕讲解笔</strong>
</p>

<p align="center">
  <em>讲到哪里，就画到哪里。</em>
</p>

<p align="center">
  点击悬浮球，在课件、PDF、网页或视频会议上打开透明画板。<br />
  用手指或手写笔圈出重点、画出思路，讲完一键退出，回到原来的应用。
</p>

<p align="center">
  <a href="https://github.com/Pxuzy/float-ink/releases/latest"><img src="https://img.shields.io/github/v/release/Pxuzy/float-ink?display_name=tag&amp;sort=semver&amp;label=Download&amp;color=2563eb" alt="下载最新版本" /></a>
  <a href="https://github.com/Pxuzy/float-ink/stargazers"><img src="https://img.shields.io/github/stars/Pxuzy/float-ink?style=flat&amp;label=Stars" alt="GitHub Stars" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" alt="许可证 Apache-2.0" /></a>
  <br />
  <a href="#版本与下载"><img src="https://img.shields.io/badge/platform-Android%2010%2B%20%7C%20%E6%89%8B%E6%9C%BA%20%7C%20%E5%B9%B3%E6%9D%BF-007ec6" alt="支持平台：Android 10+ 手机和平板" /></a>
  <a href="README.en.md" lang="en"><img src="https://img.shields.io/badge/lang-English-d93f0b" alt="Switch to English" /></a>
</p>

<p align="center">
  <a href="https://github.com/Pxuzy/float-ink/releases/latest"><strong>下载 APK</strong></a> ·
  <a href="#快速使用">快速上手</a> ·
  <a href="#功能亮点">功能亮点</a> ·
  <a href="docs/releases/v0.3.25.md">更新说明</a> ·
  <a href="https://github.com/Pxuzy/float-ink/issues">反馈问题</a>
</p>

---

## 功能亮点

| | 功能 | 用法 |
|---|---|---|
| ✍️ | 透明画板 | 直接在当前应用上方标注，支持手指与手写笔 |
| 🫧 | 悬浮入口 | 自由拖动，需要时靠近边缘自动隐藏 |
| 📐 | 多种绘图工具 | 画笔、直线、箭头、矩形与圆形，快速圈画重点 |
| 🎨 | 画笔样式 | 分别保存工具颜色与线宽，悬浮栏快速选色 |
| 🗂️ | 多画板与图层 | 切换画板、调整图层顺序与显隐，按图层撤销和清空 |
| 📏 | 比例辅助 | 黄金参考线与两点式斐波那契回撤，辅助构图和讲解 |

## 版本与下载

- **当前版本：** `0.3.25`（`versionCode 39`）
- **支持设备：** Android 10（API 29）及以上的手机和平板
- **正式下载：** [GitHub Releases](https://github.com/Pxuzy/float-ink/releases/latest)
- **应用包名：** `com.pxuzy.floatingpen`

## 适用场景

| 场景 | 用法 |
|---|---|
| 课堂讲解 | 在课件、浏览器或 PDF 上圈画、连线和强调重点 |
| 远程协作 | 在视频会议或共享内容上做即时说明，不必截图后编辑 |
| 现场演示 | 通过悬浮球快速进入和退出画板，减少对原有工作流的打断 |
| 构图与比例讲解 | 用黄金参考线或两点式斐波那契回撤辅助说明比例关系 |

## 安装步骤

1. 打开 [悬浮画笔 Releases](https://github.com/Pxuzy/float-ink/releases/latest)，下载最新 APK。
2. 在 Android 系统安装器中确认安装。首次从浏览器或文件管理器安装 APK 时，系统可能要求允许该来源安装未知应用。
3. 打开悬浮画笔，按提示授予“显示在其他应用上层”权限。
4. 在应用内启用悬浮球。

> 正式版之间使用同一发布证书签名，可直接覆盖安装并保留应用数据。若此前安装的是 Debug 或测试签名包，Android 不允许覆盖安装；请先确认旧数据是否需要保留，再卸载旧包并安装正式版。

## 快速使用

```text
打开悬浮画笔
  → 授予悬浮窗权限并启用悬浮球
  → 切换到需要讲解的应用
  → 点击悬浮球，进入透明画板
  → 选择工具、颜色和线宽后绘制
  → 点击退出，返回原应用
```

### 悬浮球

- 按住并拖动，可将悬浮球停在屏幕内任意位置。
- 开启“靠近边缘时自动隐藏到边界”后，只有在释放点靠近左右边缘时才会贴边，并按设置的延迟隐藏；关闭后不会自动贴边或隐藏。
- 可在应用设置中调整透明度、自动隐藏和隐藏延迟。
- 点击悬浮球进入透明画板；悬浮球自动隐藏后，按设备上的实际唤醒方式重新显示。

### 透明画板

- 支持手指和手写笔输入。
- 提供画笔、直线、箭头、矩形和圆形。
- 每种绘图工具可分别保存颜色和线宽；修改设置后，正在运行的画板会同步使用新设置。
- 悬浮工具栏选色默认同步全部工具，可在“设置 → 悬浮工具栏 → 悬浮栏选色范围”切换为仅当前工具；设置页提供实时工具栏预览。
- 撤销和清空仅作用于当前图层。
- 点击“退出”关闭绘图层，底层应用恢复触摸；当前会话会保留到停止悬浮服务为止。

### 画板与图层

- 一个会话可创建多个画板；每个画板默认包含一个图层。
- 可新建、切换、重命名或删除画板和图层。
- 图层支持显示/隐藏和排序；可见图层按照当前顺序合成。
- 停止悬浮服务会清除当前临时会话。

### 辅助工具

| 工具 | 使用方法 |
|---|---|
| 黄金参考线 | 显示水平、垂直两条可拖动参考线；接近 `38.2%` 或 `61.8%` 时会吸附。 |
| 斐波那契回撤 | 在画板上确定两个端点，显示 `0 / 23.6 / 38.2 / 50 / 61.8 / 78.6 / 100%` 等级线；拖动两个端点调整范围，拖动中间白色点整体移动。 |

辅助工具独立于普通笔迹，不会进入图层、撤销、清空或会话数据。

## 更新

本版完整更新见 [v0.3.25 更新说明](docs/releases/v0.3.25.md)。

推荐在应用内使用：`设置 → 软件更新 → 检查更新`。

也可以从 [GitHub Releases](https://github.com/Pxuzy/float-ink/releases) 下载 APK 后交由 Android 系统安装器更新。应用会在启动安装器前检查 APK 的包名、版本号和正式签名；Android 的安装确认由用户在系统界面完成。

## 设备设置建议

部分 Android 系统会限制后台悬浮服务。若悬浮球经常消失或服务被关闭，请在系统设置中检查：

- 已允许显示悬浮窗。
- 电池策略已设置为“不限制”或系统中的等效选项。
- 如设备提供该选项，已允许自启动并在最近任务中锁定应用。

不同品牌的设置路径不同，详细说明见[部署与设备设置说明](docs/DEPLOY.md)。

## 隐私与权限

悬浮画笔是轻量、临时的讲解工具，不是截图编辑器或云端白板。

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮球和透明绘图层 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 保持悬浮服务运行，适配 Android 14+ |
| `POST_NOTIFICATIONS` | 显示前台服务通知（Android 13+） |
| `INTERNET` | 检查 GitHub Release 和下载更新 APK |
| `REQUEST_INSTALL_PACKAGES` | 将已验证的 APK 交给系统安装器 |

悬浮画笔不会：

- 截图、录屏、读取或保存底层应用画面。
- 上传笔迹、建立账号、进行云同步或统计分析。
- 请求相册、相机、麦克风、无障碍服务、屏幕录制或广泛存储权限。
- 静默安装更新，或绕过 Android 的签名与安装安全机制。

## 已知验证范围

项目已具备 JVM/Robolectric 自动化测试和 Debug 构建验证。不同品牌系统对悬浮窗、后台保活、通知和安装器的限制不同；手写笔手感、横竖屏、分屏及 OEM 后台策略仍应在目标设备上实际确认。

## 开发者入口

源码使用原生 Android、Kotlin 和 Gradle。开发、测试、发布门禁及项目状态记录见 [项目状态](docs/PROJECT_STATE.md) 与 [项目目标](docs/GOAL.md)。

## 许可证

本项目由 `Pxuzy` 以 Apache License 2.0 发布。你可以自由使用、修改、商用和再发布本项目，但须遵守许可证中的版权、许可证和归属要求。

完整条款见 [LICENSE](LICENSE)。仓库中使用的第三方图标资源及其许可证见 [第三方资源归属说明](docs/THIRD_PARTY_NOTICES.md)。
