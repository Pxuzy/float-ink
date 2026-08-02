# Fibonacci Overlay Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile in-View Fibonacci special case with a durable two-point overlay object that is visible, selectable, draggable, resize-safe, and discoverable through a real icon.

**Architecture:** Keep regular ink in `DrawingSession` unchanged. A Fibonacci guide remains a temporary overlay object, but its geometry, hit testing, normalized coordinates, and gesture state live in `FibonacciOverlayController`. `DrawingOverlayView` routes touch events and asks a renderer to draw the controller state. The Fibonacci tool is an auxiliary overlay tool, not a configurable pen tool, so it remains outside `PenSettings.TOOL_IDS` while selection uses a dedicated unified path.

**Tech Stack:** Kotlin, Android View/Canvas, Robolectric/JUnit, Gradle.

## Global Constraints

- Preserve the horizontal/vertical golden reference guide as a separate tool.
- Do not add storage permissions, screenshots, MediaProjection, AccessibilityService, network calls, or dependencies.
- Fibonacci remains outside ordinary drawing elements, layers, undo, clear, and persisted session data.
- Store guide endpoints normalized to the active canvas viewport so resize, rotation, and split-screen retain relative positions.
- Always keep selected guide endpoints visible; use a minimum 48dp touch target.
- Keep changes in independently verified commits; do not stage existing untracked icon candidates, images, or `artifacts/`.
- Complete `./gradlew testDebugUnitTest :app:assembleDebug`, `git diff --check`, and release gates before creating a user-facing APK.

---

### Task 1: Fibonacci Object Controller

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/core/FibonacciRetracement.kt`
- Create: `app/src/main/java/com/pxuzy/floatingpen/FibonacciOverlayController.kt`
- Create: `app/src/test/java/com/pxuzy/floatingpen/FibonacciOverlayControllerTest.kt`

**Interfaces:**
- Produces `FibonacciOverlayController(viewportWidth: Float, viewportHeight: Float, hitRadiusPx: Float)`.
- `begin(x: Float, y: Float): Boolean`, `move(x: Float, y: Float): Boolean`, `end(): Boolean`, `cancel(): Boolean`, `resize(width: Float, height: Float)`.
- `guide: FibonacciGuide?`, `isSelected: Boolean`, `isCreating: Boolean`, `renderState(): FibonacciRenderState?`.
- `FibonacciGuide` stores normalized `start` and `end` fractions and resolves pixels only against the supplied viewport.

- [ ] **Step 1: Write failing controller tests**

```kotlin
@Test
fun `drag creates selected guide with persistent endpoints`() {
    val controller = FibonacciOverlayController(400f, 800f, 48f)
    controller.begin(40f, 80f)
    controller.move(240f, 680f)
    controller.end()

    val state = controller.renderState()!!
    assertTrue(state.selected)
    assertEquals(FibonacciPoint(40f, 80f), state.start)
    assertEquals(FibonacciPoint(240f, 680f), state.end)
}

@Test
fun `dragging selected endpoint changes only that endpoint`() {
    val controller = FibonacciOverlayController(400f, 800f, 48f)
    controller.begin(40f, 80f)
    controller.move(240f, 680f)
    controller.end()

    controller.begin(240f, 680f)
    controller.move(300f, 600f)
    controller.end()

    val state = controller.renderState()!!
    assertEquals(FibonacciPoint(40f, 80f), state.start)
    assertEquals(FibonacciPoint(300f, 600f), state.end)
}

@Test
fun `resize preserves relative endpoint positions`() {
    val controller = FibonacciOverlayController(400f, 800f, 48f)
    controller.begin(100f, 200f)
    controller.move(300f, 600f)
    controller.end()
    controller.resize(800f, 400f)

    val state = controller.renderState()!!
    assertEquals(FibonacciPoint(200f, 100f), state.start)
    assertEquals(FibonacciPoint(600f, 300f), state.end)
}
```

- [ ] **Step 2: Run the focused test to verify RED**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.FibonacciOverlayControllerTest`

Expected: compilation failure because `FibonacciOverlayController` and `FibonacciGuide` do not exist.

- [ ] **Step 3: Implement the minimal normalized controller**

Implement explicit gesture modes `Idle`, `Creating`, `DraggingStart`, and `DraggingEnd`. On empty-canvas down, create a guide with equal endpoints and enter `Creating`; on endpoint down, enter that endpoint's drag mode; completed guides retain `selected = true`. Clamp normalized fractions to `0f..1f`. `resize` only updates the viewport, because guide storage is normalized.

- [ ] **Step 4: Run focused controller tests to verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.FibonacciOverlayControllerTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the independently verified core slice**

```bash
git add app/src/main/java/com/pxuzy/floatingpen/core/FibonacciRetracement.kt \
  app/src/main/java/com/pxuzy/floatingpen/FibonacciOverlayController.kt \
  app/src/test/java/com/pxuzy/floatingpen/FibonacciOverlayControllerTest.kt \
  docs/superpowers/plans/2026-08-02-fibonacci-overlay-refactor.md
git commit -m "重构(黄金分割): 拆出回撤对象控制器" \
  -m "- 使用归一化坐标保存两点式回撤对象\n- 覆盖创建、端点拖动和尺寸变化回归测试"
```

### Task 2: Controller Renderer and Recognizable Icon

**Files:**
- Create: `app/src/main/java/com/pxuzy/floatingpen/FibonacciGuideRenderer.kt`
- Modify: `app/src/main/java/com/pxuzy/floatingpen/FloatInkIconView.kt`
- Create: `app/src/test/java/com/pxuzy/floatingpen/FloatInkIconViewTest.kt`

**Interfaces:**
- `FibonacciGuideRenderer(density: Float).draw(canvas: Canvas, state: FibonacciRenderState)`.
- The renderer draws levels from `FibonacciRetracement.levels`, endpoint circles, a connector, `61.8%` emphasis, and selected-state handles.
- `FloatInkIconView(context, "fibonacci")` has a non-empty, recognizable drawing inside its view bounds.

- [ ] **Step 1: Write failing icon and renderer tests**

```kotlin
@Test
fun `fibonacci icon draws visible pixels`() {
    val icon = FloatInkIconView(context, "fibonacci")
    icon.layout(0, 0, 48, 48)
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    icon.draw(Canvas(bitmap))
    assertTrue((0 until 48).any { x -> (0 until 48).any { y -> bitmap.getPixel(x, y) != Color.TRANSPARENT } })
}
```

Also test renderer output with a bitmap by asserting non-transparent pixels exist at the expected `0%`, `61.8%`, and `100%` level rows and at both endpoint locations.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.FloatInkIconViewTest --tests com.pxuzy.floatingpen.FibonacciGuideRendererTest`

Expected: compile failure because the renderer test class does not yet exist.

- [ ] **Step 3: Implement renderer and icon**

Draw the Fibonacci icon in shared `FloatInkIconView`: a diagonal anchor spine, two endpoint circles, and three horizontal level strokes. Avoid text and arbitrary decorative marks. Renderer keeps all Canvas allocation scoped to drawing and renders handles whenever the guide is selected or actively created.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.FloatInkIconViewTest --tests com.pxuzy.floatingpen.FibonacciGuideRendererTest`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Overlay Routing and Auxiliary Tool Registration

**Files:**
- Modify: `app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt`
- Modify: `app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt`
- Modify: `README.md`
- Modify: `docs/PROJECT_STATE.md`

**Interfaces:**
- `DrawingOverlayView` owns one `FibonacciOverlayController` and one `FibonacciGuideRenderer`.
- `selectAuxiliaryTool("fibonacci")` performs active-gesture cancellation, tool selection callback, control refresh, indicator refresh, and redraw without treating Fibonacci as a pen setting.
- `onSizeChanged` calls `fibonacciController.resize(w.toFloat(), h.toFloat())`.

- [ ] **Step 1: Write failing overlay regression tests**

```kotlin
@Test
fun `fibonacci selection creates persistent selectable overlay without ink`() {
    val view = DrawingOverlayView(context, "pen", 0) {}
    val toolbar = view.findByTag("monochrome-toolbar") as LinearLayout
    toolbar.findByTag("more-tools").performClick()
    val entry = view.findByTag("fibonacci-retracement") as FloatInkIconView
    entry.performClick()

    val canvas = view.getChildAt(0)
    canvas.layout(0, 0, 400, 800)
    drawGesture(canvas, 40f, 80f, 240f, 680f)

    assertTrue(view.elementsForTest().isEmpty())
    assertTrue(view.fibonacciRenderStateForTest()!!.selected)
    assertTrue(view.fibonacciRenderStateForTest()!!.handlesVisible)
}
```

Add a second test that drags the rendered end handle after creation and verifies the guide changes without adding a `DrawingElement`.

- [ ] **Step 2: Run focused overlay test to verify RED**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest`

Expected: compilation failure because test-only render-state access and unified auxiliary selection are not implemented.

- [ ] **Step 3: Route Fibonacci through the controller**

Remove `fibonacciStart`, `fibonacciEnd`, `fibonacciDrawing`, `draggingFibonacciEndpoint`, `isFibonacciEndpointHit`, and `drawFibonacciGuide` from `DrawingOverlayView`. Route `ACTION_DOWN/MOVE/UP/CANCEL` to the controller only when the active tool is Fibonacci. Route draw to `FibonacciGuideRenderer`. Keep golden-reference gesture behavior unchanged.

- [ ] **Step 4: Register the tool as an auxiliary entry**

Keep Fibonacci outside `PenSettings.TOOL_IDS`; add a small auxiliary-tool selection path that calls the same selection callbacks and UI refreshes as ordinary selection, while bypassing pen-style persistence. The more-tools entry must be a `FloatInkIconView` tagged `fibonacci-retracement`, have `contentDescription = "斐波那契回撤"`, selected background state, and a sibling text label `斐波那契回撤` for discoverability.

- [ ] **Step 5: Run focused overlay tests to verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.pxuzy.floatingpen.DrawingOverlayViewTest --tests com.pxuzy.floatingpen.FibonacciOverlayControllerTest --tests com.pxuzy.floatingpen.FloatInkIconViewTest --tests com.pxuzy.floatingpen.FibonacciGuideRendererTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Update user-facing documentation and commit**

Document that Fibonacci is a temporary, selectable overlay object with persistent visible endpoints during selection, not an ink stroke or saved session element.

```bash
git add app/src/main/java/com/pxuzy/floatingpen/DrawingOverlayView.kt \
  app/src/main/java/com/pxuzy/floatingpen/FibonacciGuideRenderer.kt \
  app/src/main/java/com/pxuzy/floatingpen/FloatInkIconView.kt \
  app/src/test/java/com/pxuzy/floatingpen/DrawingOverlayViewTest.kt \
  app/src/test/java/com/pxuzy/floatingpen/FloatInkIconViewTest.kt \
  app/src/test/java/com/pxuzy/floatingpen/FibonacciGuideRendererTest.kt \
  README.md docs/PROJECT_STATE.md
git commit -m "修复(黄金分割): 重建回撤图标与触摸交互" \
  -m "- 统一辅助工具选择状态并显示可拖动端点\n- 回撤对象适配旋转和窗口尺寸变化\n- 覆盖图标像素、选中和端点拖动回归测试"
```

### Task 4: Full Verification and Release

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/PROJECT_STATE.md`

- [ ] **Step 1: Bump patch release version**

Set `versionCode = 32` and `versionName = "0.3.18"`; update README version and project state release notes.

- [ ] **Step 2: Run full local verification**

Run: `./gradlew testDebugUnitTest :app:assembleDebug && git diff --check`

Expected: `BUILD SUCCESSFUL`; no `git diff --check` output.

- [ ] **Step 3: Verify Debug APK metadata**

Run:

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
$HOME/Android/Sdk/build-tools/35.0.0/aapt dump badging "$APK" | head -1
```

Expected: package `com.pxuzy.floatingpen`, `versionCode='32'`, `versionName='0.3.18'`.

- [ ] **Step 4: Stage only intended files, run gates, commit, push and tag**

```bash
bash scripts/check-project-gate.sh working-tree
git add README.md app/build.gradle.kts docs/PROJECT_STATE.md [all tracked Fibonacci refactor files]
git commit -m "发布(黄金分割): 修复回撤工具交互" \
  -m "- 发布可选中、可拖动并适配尺寸变化的斐波那契回撤\n- 已完成单测和 Debug 构建"
git push origin main
git tag -a v0.3.18 -m "发布 0.3.18"
git push origin v0.3.18
```

Expected: local pre-push range gate and clean checkout build pass; tag gate validates version and monotonic versionCode.

- [ ] **Step 5: Verify published Release asset**

Use `gh run watch` for the tag workflow; then retrieve the Release asset, verify its SHA-256 against GitHub asset metadata, verify ZIP integrity, package name/version, and the official release certificate fingerprint before delivery.
