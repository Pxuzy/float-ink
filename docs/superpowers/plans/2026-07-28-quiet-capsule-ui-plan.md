# FloatInk 静默胶囊与 RGB 键盘实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持现有绘图、画板和历史会话行为不变的前提下，实现可可靠调出系统键盘的 RGB 三通道输入，并将 App 与悬浮层统一为静默胶囊 UI。

**Architecture:** 新建可复用的 `RgbColorInputView`，只负责 RGB 展示、整数输入和校验；MainActivity 与 DrawingOverlayView 负责各自的颜色草稿和保存。OverlayService 继续独占 WindowManager 参数，通过幂等文本输入模式切换让悬浮 EditText 临时获得窗口焦点，DrawingOverlayView 只发出进入/退出请求。

**Tech Stack:** Kotlin、Android View、WindowManager TYPE_APPLICATION_OVERLAY、Robolectric、JUnit 4、Gradle/AGP。

## Global Constraints

- minSdk 29，targetSdk 35，不新增第三方 UI 依赖。
- 不改变绘图手势、窗口触摸消费、工具顺序、画板/图层模型和 `.floatink` 格式。
- RGB 仅接受三个 `0..255` 十进制整数，输入框使用 `InputType.TYPE_CLASS_NUMBER`。
- 颜色控件触控槽至少 48dp；悬浮层工具图标保持单色，只有墨色点显示颜色。
- 每个任务独立测试、独立提交；稳定回退点为 `v0.2.6`，本轮基线为 `e49d94f`。

---

### Task 1: 可复用 RGB 三通道输入

**Files:**
- Create: `app/src/main/java/com/pxuzy/floatingpen/RgbColorInputView.kt`
- Create: `app/src/test/java/com/pxuzy/floatingpen/RgbColorInputViewTest.kt`

**Interfaces:**
- Produces: `RgbColorInputView(context)`, `var color: Int`, `fun parsedColor(): Int?`, `fun focusChannel(channel: Channel): EditText`，稳定 tags `rgb-r`、`rgb-g`、`rgb-b`、`rgb-error`。

- [ ] **Step 1: 写失败测试**

测试三个输入框均为数字类型，`0/255` 可解析，空值、`256` 和非数字返回 null 并显示通道错误。

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.RgbColorInputViewTest`
Expected: FAIL，类尚不存在。

- [ ] **Step 3: 最小实现**

实现三列 `R/G/B` 输入、`InputFilter.LengthFilter(3)`、整数解析、通道错误和颜色同步；不持久化、不直接保存颜色。

- [ ] **Step 4: 运行测试并提交**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.RgbColorInputViewTest`
Expected: PASS。

Commit: `feat(color): 增加 RGB 三通道数字输入组件`

### Task 2: App 颜色编辑器接入数字键盘

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/MainActivity.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt`

**Interfaces:**
- Consumes: `RgbColorInputView.parsedColor()`。
- Produces: App 自定义颜色对话框中三个数字输入，最后编辑模式仍决定保存来源。

- [ ] **Step 1: 写失败测试**

断言对话框含 `rgb-r/g/b`，输入类型为数字；输入 `12/34/56` 保存为对应 ARGB；`256` 时对话框不关闭且错误可见。

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.MainActivityTest`
Expected: 新断言 FAIL。

- [ ] **Step 3: 接入组件**

替换逗号 RGB EditText；通道获得焦点时将最后编辑模式设为 RGB，并在 `post` 后请求焦点/调用 `InputMethodManager.showSoftInput(..., SHOW_IMPLICIT)`。保留 HEX、HSV、透明度和 ScrollView。

- [ ] **Step 4: 测试并提交**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.MainActivityTest`
Expected: PASS。

Commit: `fix(color): 让 RGB 输入调用系统数字键盘`

### Task 3: 悬浮窗口文本输入模式

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/OverlayService.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/OverlayServiceTest.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`

**Interfaces:**
- Produces: DrawingOverlayView callbacks `onTextInputModeChanged: (Boolean) -> Unit`，OverlayService 私有 `setDrawingTextInputMode(enabled: Boolean)`。
- Invariant: 默认 flags 含 `FLAG_NOT_FOCUSABLE`；输入模式移除它和 `FLAG_ALT_FOCUSABLE_IM`；恢复可重复调用。

- [ ] **Step 1: 写失败测试**

服务测试打开绘图后进入输入模式会移除 `FLAG_NOT_FOCUSABLE`，退出会恢复；重复退出不改变结果；flags 不含 `FLAG_NOT_TOUCH_MODAL`。View 测试打开精确编辑触发 `true`，关闭/切换/退出触发 `false`。

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.OverlayServiceTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: 新接口和状态断言 FAIL。

- [ ] **Step 3: 最小窗口实现**

OverlayService 保存附着绘图窗口的参数实例，使用 `updateViewLayout` 修改 flags 与 softInputMode，异常时恢复默认值。DrawingOverlayView 关闭颜色面板、切换其他面板、配置变化和退出前统一调用幂等 `finishTextInputMode()`。

- [ ] **Step 4: 测试并提交**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.OverlayServiceTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: PASS。

Commit: `fix(overlay): 支持悬浮颜色输入临时获取焦点`

### Task 4: 悬浮 RGB 编辑与 IME 可达布局

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`

**Interfaces:**
- Consumes: `RgbColorInputView`、`onTextInputModeChanged`。
- Produces: 悬浮精确颜色编辑器的 RGB 三输入、取消/保存、可滚动内容和稳定 tags。

- [ ] **Step 1: 写失败结构测试**

断言 220dp/320dp/横屏下有 ScrollView、RGB 三通道、取消和保存；进入编辑模式后颜色面板宽度受限；取消不会保存，保存有效 RGB 会更新当前工具。

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: 新结构断言 FAIL。

- [ ] **Step 3: 实现编辑器**

用复用组件替换单一 RGB 文本框；加入明确取消/保存；聚焦后等待窗口焦点再请求 IME；使用 WindowInsets IME 底部值为弹层/滚动容器增加安全 padding，配置变化时退回颜色面板第一层。

- [ ] **Step 4: 测试并提交**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: PASS。

Commit: `feat(color): 重构悬浮精确颜色编辑器`

### Task 5: 静默胶囊视觉契约

**Files:**
- Create: `app/src/main/java/com/pxuzy/floatingpen/FloatInkTheme.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/MainActivity.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/FloatingBubbleView.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`

**Interfaces:**
- Produces: 集中的背景/表面/文字/边框颜色、4/6/8dp 圆角和间距常量。

- [ ] **Step 1: 写失败结构测试**

断言导航与页面分区稳定、颜色控件位于工具滚动区之前、固定操作位于末端、220dp 仍可见退出、面板圆角不超过 8dp、工具图标保持单色。

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.MainActivityTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: 新视觉契约断言 FAIL。

- [ ] **Step 3: 视觉调整**

集中现有散落颜色；将工具栏/面板圆角收敛为 8dp；减少页面重复副标题和卡片层级；保留首页主操作、画笔配置、设置与历史功能的现有行为和 tags。

- [ ] **Step 4: 测试并提交**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.MainActivityTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`
Expected: PASS。

Commit: `feat(ui): 应用静默胶囊视觉系统`

### Task 6: 全量验证与 Debug APK

**Files:**
- Modify: `docs/PROJECT_STATE.md`

- [ ] **Step 1: 全量测试与构建**

Run: `./gradlew testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`，无跳过测试。

- [ ] **Step 2: 静态门禁**

Run: `git diff --check && unzip -t app/build/outputs/apk/debug/app-debug.apk`
Expected: 无空白错误，APK ZIP 完整。

- [ ] **Step 3: 检查变更范围**

确认未改持久化键、DrawingSession/FloatInkSessionStore、绘图触摸阈值、版本号和 release workflow。

- [ ] **Step 4: 更新状态并提交**

Commit: `docs(ui): 记录静默胶囊与 RGB 键盘验证结果`

- [ ] **Step 5: 真机验收**

侧载 Debug APK，验证 App 与悬浮 RGB 数字键盘、保存/取消/返回/旋转后的焦点恢复、浅深背景对比度和手机/平板布局。真机通过前不合并 `main`、不发布 tag。
