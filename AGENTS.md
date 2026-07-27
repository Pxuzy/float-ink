# Tablet Floating Pen

## Project goal

Build a small Android phone/tablet app: a draggable floating button opens a transparent drawing overlay above the current app. The user draws temporary annotations, then exits and returns to the underlying app.

## Current scope: V0 prototype only

Implement only:
- Android phone and tablet
- User-granted overlay permission
- Draggable floating button
- Tap floating button to open transparent drawing overlay
- Freehand drawing with finger or stylus
- Visible Exit button
- Exit removes the drawing overlay and clears all strokes

Do not implement yet:
- iPad
- Screenshots, screen recording, image export, saving or uploading
- Login, cloud sync, analytics, cloud data sync
- Shapes, arrows, colors, line widths, undo, eraser, full toolbar
- Background persistence, Google Play release work, multi-device compatibility matrix

## Product rules

- The drawing is temporary. Exiting discards it.
- Do not use AccessibilityService, MediaProjection, PixelCopy, camera, microphone, or broad storage permissions. The only network use is the explicit GitHub Releases update check.
- The V0 prototype only needs to prove the core flow on a real Android phone or tablet.
- Use native Android Kotlin. Do not use WebView for the drawing overlay.

## Workflow

1. Read `README.md` and `docs/PROJECT_STATE.md` at the start of every task.
2. Make the smallest working change.
3. Run the relevant build/test before claiming success.
4. Update `docs/PROJECT_STATE.md` after each meaningful milestone.
5. Keep commits small, using Quant-style Chinese messages: `类型(范围): 中文标题`, followed by a blank line and `-` detail bullets.

## Commands

Before implementation or release work, verify Java, Android SDK, and Gradle wrapper availability. Do not claim APK build success until a real Gradle build has completed.
