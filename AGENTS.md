# Tablet Floating Pen

## Project goal

Build a small Android phone/tablet app: a draggable floating button opens a transparent drawing overlay above the current app. The user draws temporary annotations, then exits and returns to the underlying app.

## Current scope: v0.3.0 stabilization

The current main branch is based on `v0.3.0`. Stabilize and verify the existing Android phone/tablet experience before adding new product features:
- Android phone and tablet
- User-granted overlay permission
- Draggable, edge-snapping, auto-hiding floating button
- Transparent drawing overlay
- Finger and stylus drawing
- Pen, line, arrow, rectangle, and circle tools
- Undo, clear, and visible Exit action
- Multiple boards and layers with visibility, ordering, and layer-scoped editing
- Persisted per-tool color, width, and arrow scale settings
- GitHub Releases APK update flow

Do not implement or reintroduce:
- iPad
- Screenshots, screen recording, image export, saving or uploading
- Login, cloud sync, analytics, cloud data sync
- Screenshots, screen recording, image export, cloud sync, or broad storage access
- AccessibilityService or MediaProjection
- iPad support
- Unrelated server-side tools or large dependencies

## Product rules

- The current drawing session is temporary in memory. Exiting the drawing overlay may retain the current session; stopping the overlay service is the explicit session-clear boundary.
- Do not use AccessibilityService, MediaProjection, PixelCopy, camera, microphone, or broad storage permissions. The only network use is the explicit GitHub Releases update check.
- The V0 prototype only needs to prove the core flow on a real Android phone or tablet.
- Use native Android Kotlin. Do not use WebView for the drawing overlay.

## Workflow

1. Read `README.md` and `docs/PROJECT_STATE.md` at the start of every task.
2. Make the smallest working change from the `v0.3.0` baseline.
3. Run the relevant build/test before claiming success.
4. Update `docs/PROJECT_STATE.md` and `docs/GOAL.md` after each meaningful milestone.
5. Keep commits small, using Quant-style Chinese messages: `类型(范围): 中文标题`, followed by a blank line and `-` detail bullets.

## Commands

Before implementation or release work, verify Java, Android SDK, and Gradle wrapper availability. Do not claim APK build success until a real Gradle build has completed.
