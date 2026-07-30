# RGB、圆形工具与设置 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 RGB 通道即时上限，新增正圆绘图工具，让首页各工具线宽与 `PenSettings` 单一来源一致并实时渲染，同时重排画板/图层弹层以适配窄屏和增长内容。

**Architecture:** 保持原生 View + Canvas + 纯 Kotlin `core` 模型。先扩展稳定的 `DrawingElement`、`PenSettings` 和 `.floatink` 编解码，再接入 `DrawingOverlayView` 与 `MainActivity`。画板面板只调整 View 层级、测量和滚动边界，不改变画板/图层数据模型。

**Tech Stack:** Kotlin, Android Views, Canvas, Robolectric, Gradle 8.10.2, compileSdk 35, minSdk 29。

## Global Constraints

- 从 `main` 最新提交 `a8b8f96` 创建分支，`main` 不直接修改。
- RGB 每个通道的有效范围为 `0..255`；输入长度最多 3 位，超范围输入即时规范化为 `255`。
- 圆形第一阶段是正圆；拖动起点到终点的最大轴向距离作为半径，起点为圆心，保持旧工具自由矩形不变。
- 圆形工具 ID 固定为 `circle`，显示名为 `圆形`，加入默认工具列表和工具栏布局兼容逻辑。
- 旧 `.floatink` 文件必须继续读取；新 `circle` 类型写入 `type=circle`，未知类型继续跳过。
- 首页显示当前工具的实际独立样式；线宽和颜色变化立即更新标签、预览、持久化和运行中的悬浮层。
- 画板弹层使用实际可用宽度，列表区域独立滚动，固定标题区和安全操作区，所有触控槽至少 48dp。
- 每个切片必须先写失败测试，再实现，再运行测试和 Debug 构建，并使用 Quant 式中文提交信息。

---

### Task 1: RGB 通道即时上限

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/RgbColorInputView.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/RgbColorInputViewTest.kt`

**Interfaces:**
- Preserve `RgbColorInputView.parsedColor(): Int?` and `color` property.
- Add a private/shared channel normalizer only if needed; no caller changes.

- [ ] **Step 1: Write failing tests**

Add tests proving that typing `256` immediately becomes `255`, typing `999` becomes `255`, channel values `0`, `25`, `255` remain unchanged, and `parsedColor()` accepts the normalized result.

- [ ] **Step 2: Run focused test and verify failure**

Run:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*RgbColorInputViewTest*' --no-daemon
```

Expected: new normalization tests fail because the current `LengthFilter(3)` accepts arbitrary three-digit values.

- [ ] **Step 3: Implement minimal normalization**

Use an `InputFilter` that accepts numeric input, clamps the resulting text to `255`, and leaves intermediate empty/partial input editable. Keep `parsedColor()` as the final validation boundary for pasted or programmatic text.

- [ ] **Step 4: Run focused and full tests**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*RgbColorInputViewTest*' --no-daemon
```

Expected: all RGB tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/RgbColorInputView.kt app/src/test/java/com/pxuzy/floatingpen/RgbColorInputViewTest.kt
git commit -m "fix(color): 限制 RGB 通道即时不超过 255"
```

---

### Task 2: 纯圆形模型、几何与文件兼容

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/core/DrawingElement.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/PenSettings.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/FloatInkSessionStore.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/core/DrawingElementTest.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/FloatInkSessionStoreTest.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt`

**Interfaces:**
- Add `DrawingElement.Circle(center: Pair<Float,Float>, radius: Float, color: Int, width: Float)` with `drawColor` and `drawWidth`.
- Add `DrawingElement.circle` tool definition and `PenSettings.TOOL_IDS` entry.
- Add `PenSettings` circle style persistence through existing `tool_<id>_color_argb` and `tool_<id>_width_dp` conventions.

- [ ] **Step 1: Write failing model and codec tests**

Test that circle is a tool, `PenSettings.normalizeTool("circle")` returns `circle`, a circle round-trips through `.floatink`, and old line/rect/arrow fixtures remain readable.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*DrawingElementTest*' --tests '*FloatInkSessionStoreTest*' --tests '*MainActivityTest*' --no-daemon
```

Expected: compilation or assertion failure because `Circle` and `circle` tool are absent.

- [ ] **Step 3: Implement model and persistence**

Add `Circle`, extend the tool definition/list, and update `encodeElements`/`decodeElements` with `type=circle`, `center`, and `radius`. Keep unknown element types ignored and do not alter existing JSON fields.

- [ ] **Step 4: Run focused tests**

Expected: model, codec, and settings tests pass, including old element compatibility.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/core/DrawingElement.kt app/src/main/java/com/pxuzy/floatingpen/PenSettings.kt app/src/main/java/com/pxuzy/floatingpen/FloatInkSessionStore.kt app/src/test/java/com/pxuzy/floatingpen/core/DrawingElementTest.kt app/src/test/java/com/pxuzy/floatingpen/FloatInkSessionStoreTest.kt app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt
git commit -m "feat(shape): 增加正圆模型与会话文件兼容"
```

---

### Task 3: 悬浮画板圆形绘制与工具栏接入

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/ToolbarLayoutEditorView.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`
- Test: `app/src/test/java/com/pxuzy/floatingpen/ToolbarLayoutEditorViewTest.kt` if existing coverage needs extension

**Interfaces:**
- Circle preview and committed element use the same radius helper.
- Existing constructor signatures and callbacks remain source-compatible.

- [ ] **Step 1: Write failing overlay tests**

Add tests that the circle tool is visible/selectable, a drag creates exactly one `Circle`, the radius is positive and based on the maximum absolute x/y delta, zero-length gestures are ignored, preview uses current color/width, and switching to circle does not break the exit action.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*DrawingOverlayViewTest*' --no-daemon
```

Expected: tests fail because the drawing switch has no `circle` case and renderer has no circle branch.

- [ ] **Step 3: Implement circle drawing**

Add a pure helper for `circleGeometry(startX, startY, endX, endY)` returning center/radius, use it for MOVE preview and ACTION_UP commit, draw with `Canvas.drawCircle`, and ignore radius zero. Add a monochrome circle icon path and ensure the normalized toolbar list includes the new tool.

- [ ] **Step 4: Run focused tests and build**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*DrawingOverlayViewTest*' --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt app/src/main/java/com/pxuzy/floatingpen/ToolbarLayoutEditorView.kt app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt app/src/test/java/com/pxuzy/floatingpen/ToolbarLayoutEditorViewTest.kt
git commit -m "feat(shape): 接入悬浮画板正圆工具"
```

---

### Task 4: 首页按工具显示独立线宽并实时渲染

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/MainActivity.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt`

**Interfaces:**
- `ToolPreviewView` renders `pen`, `line`, `arrow`, `rect`, and `circle` from the selected tool's live `ToolStyle`.
- `buildPenPage()` refreshes the current-tool preview and labels through local references/listeners, not stale captured values.

- [ ] **Step 1: Write failing UI tests**

Add tests that selecting each tool shows its persisted width, changing the tool width updates the label and preview stroke width immediately, changing global width does not silently overwrite tool width, and circle preview is present.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*MainActivityTest*' --no-daemon
```

Expected: circle preview/tool controls fail and current preview does not expose a verifiable live style contract.

- [ ] **Step 3: Implement single-source live style rendering**

Keep `PenSettings.load(context).styleFor(selectedTool)` as the source of truth. On tool selection reload selected color/width and rebuild only the necessary current-tool region; on SeekBar changes update the selected tool style, label, and preview immediately. Extend `ToolPreviewView` with circle geometry and ensure arrow preview uses the same `ArrowGeometry` as the overlay.

- [ ] **Step 4: Run focused tests and build**

Expected: all MainActivity style tests pass and Debug APK builds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/MainActivity.kt app/src/test/java/com/pxuzy/floatingpen/MainActivityTest.kt
git commit -m "feat(settings): 按工具实时同步首页线宽预览"
```

---

### Task 5: 画板与图层面板布局重排

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`

**Interfaces:**
- Preserve all existing tags and callbacks for board/layer selection, add, visibility, rename, delete, and reorder.
- Replace fixed `220.dp` content sizing with available-width clamping.

- [ ] **Step 1: Write failing layout tests**

Add structural tests for 220dp, 320dp, and tablet widths: panel width never exceeds host width minus margins; board and layer sections have stable header tags; list scroll containers have bounded heights; rows retain 48dp touch slots; add buttons remain in their section headers; action menus remain reachable.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest --tests '*DrawingOverlayViewTest*' --no-daemon
```

Expected: fixed 220dp assertions or missing new section layout tags fail on narrow hosts.

- [ ] **Step 3: Implement layout**

Use a panel width of `min(availableHostWidth - 16dp, 360dp)` with a minimum clamped to the host, create explicit `canvas-board-section`, `canvas-layer-section`, and separate bounded scroll areas, and keep panel positioning through `positionPopupAboveToolbar`. Use `MATCH_PARENT` rows inside the panel so names and overflow controls do not collide.

- [ ] **Step 4: Run full verification**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
```

Expected: all tests pass, APK builds, and no whitespace errors are reported.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt
git commit -m "fix(canvas): 重排画板图层弹层布局"
```

---

### Task 6: 文档、状态与最终交付

**Files:**
- Modify: `docs/PROJECT_STATE.md`
- Modify: `README.md` only if the current capability list needs updating

- [ ] **Step 1: Update project state**

Record RGB immediate clamping, circle support, per-tool live width preview, and board/layer layout changes. Record that real-device validation remains required.

- [ ] **Step 2: Run final verification and inspect scope**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest :app:assembleDebug --no-daemon
git diff --check
git status --short --branch
git log --oneline --decorate -8
```

Expected: all tests pass, APK exists, worktree contains only intended documentation changes after the final commit.

- [ ] **Step 3: Commit documentation**

```bash
git add docs/PROJECT_STATE.md README.md
git commit -m "docs(state): 记录圆形工具与设置界面优化"
```
