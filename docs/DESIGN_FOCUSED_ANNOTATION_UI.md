# FloatInk Focused Annotation UI Design

**Date:** 2026-07-29
**Status:** Approved for planning
**Baseline:** v0.3.1 (`29a5e2d`)

## Goal

Refine FloatInk into a low-interference temporary screen annotation tool. The UI must make drawing, undo, clear, layer selection, and exit fast to reach while keeping configuration and history inside the launcher app.

This is not a whiteboard redesign. The scope excludes screenshots, recording, cloud sync, collaboration, and new drawing primitives.

## Product Principles

1. A short bubble tap enters drawing immediately with persisted settings.
2. The overlay prioritizes drawing over configuration.
3. Exit is always visible and never competes for horizontal space.
4. Destructive clear is recoverable for a short, explicit window.
5. Color is meaningful only for ink. Overlay chrome remains neutral.
6. Phone, tablet, landscape, and narrow split-screen layouts obey the same interaction model.

## Visual Contract

- Background: graphite `#0C1015`; raised surfaces `#151F29`; thin cool-gray borders.
- Primary text: near-white; secondary text: muted blue-gray.
- Accent: cyan ink state only. No decorative gradients, color-coded tool buttons, or emoji icons.
- Panels and controls: 8dp corner radius maximum.
- Touch targets: 48dp minimum for all actionable overlay controls.
- Active tool: white outline plus subtle neutral fill; current ink color stays in the swatch only.

## Launcher Information Architecture

### Home

The home page is the control center: service status, one primary `进入绘图` action, persisted tool/color/width context, and recent sessions. Fixed bottom navigation remains Home, Pen, and Settings.

### Pen

The pen page owns persistent drawing defaults: current tool selector, horizontally scrollable 48dp color palette, explicit Manage mode for custom-color deletion, current-tool width and geometry-aware preview, and toolbar layout ordering.

### Settings

Settings owns bubble opacity, auto-hide, update checking, and session/history administration. It does not duplicate active drawing controls.

## Overlay Layout

Use one compact toolbar on the overlay bottom edge.

```text
[ horizontally scrollable operation region                         ][ fixed Exit ]
[ drag | color | tool 1..n | undo | clear | boards/layers | more? ][    X       ]
```

- The scroll region contains all non-exit operations and can overflow horizontally on narrow screens.
- Exit is a fixed 48dp button at the far right and remains inside the toolbar at 220dp split-screen width.
- `more` appears only when configured tools exceed the primary visible set.
- Clear remains a first-level operation and affects the current layer only.
- Clear opens a six-second restore bar. Restore expires on a new completed draw, selection change, board/layer deletion, session replacement, timeout, or overlay detach.

### Undo Icon

The undo icon is a counterclockwise curved arrow. Its arrowhead points left/upward at the leading end of one continuous round-capped arc. It must not resemble refresh, rotate, back navigation, or redo. `ToolIconView` draws it using the same grid, stroke width, caps, and joins as other overlay icons.

## Board and Layer Panel

The panel has two bounded scroll sections:

- Boards: selection row, header add action, row-end overflow menu.
- Layers: color swatch, visibility eye, selection row, header add action, row-end overflow menu.
- Panels anchor above the toolbar when possible, fall below only when necessary, and clamp to the current overlay window.

## Adaptive Launcher Icon

Replace the direct standalone PNG launcher reference with Android adaptive resources:

- Manifest uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- Background is opaque graphite `#10161D` over the complete 108dp viewport.
- Foreground is a transparent white stylus plus short cyan ink stroke.
- Critical foreground content sits inside Android's centered 66dp safe zone of the 108dp viewport.
- Include a monochrome layer when practical for themed launcher icons.
- Do not use baked-background `ic_launcher_floatink.png` as an adaptive foreground.

Android reference: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

## Proven Reference Patterns

- DrawPen: lightweight direct annotation with settings separate from the active toolbar. https://drawpen.app
- MarkerOn: compact drawing controls, direct undo/clear workflow, expand only when needed. https://github.com/ifer47/markeron
- Drawix Pro: Android screen-annotation positioning and local/privacy-first posture. https://github.com/Xposed-Modules-Repo/xyz.siwane.drawix.pro

These projects inform interaction patterns only. FloatInk does not copy their code, assets, or branding.

## Implementation Boundaries

Do not change overlay permissions, foreground-service type, window touch flags, bubble drag/long-press thresholds, drawing element model, vector session codec, autosave format, service-stop session clearing, or existing per-tool style persistence keys.

## Verification Matrix

### Unit and structural tests

- Undo path direction is counterclockwise and distinct from a clockwise/refresh arc.
- Adaptive icon XML references foreground/background resources; the manifest no longer references the standalone launcher PNG.
- Exit stays within bounds at 220dp, 320dp, and 700dp widths.
- Every toolbar action has a 48dp touch slot.
- Clear remains discoverable and opens scoped restore.
- Restore disappears after each invalidating transition.
- Overlay color, panel, board, and layer tags remain stable.

### APK checks

- `./gradlew testDebugUnitTest :app:assembleDebug` passes.
- `aapt dump badging` reports the expected package/version.
- APK packages adaptive icon XML and required drawable layers.

### Real-device checks

- Test the launcher icon on round/squircle OEM and standard Android launchers.
- Test narrow split-screen, landscape, tablet, and large-font overlay layouts.
- Confirm the undo symbol reads as undo before labels are consulted.
- Confirm fixed Exit remains reachable while toolbar operations scroll.

## Delivery Slices

1. Icon resource migration and adaptive packaging.
2. Undo geometry correction with visual-contract tests.
3. Overlay toolbar refinement and touch-target verification.
4. Launcher layout refinement and final visual/device validation.
