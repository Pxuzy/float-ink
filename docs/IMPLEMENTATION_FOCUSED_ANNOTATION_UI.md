# FloatInk Focused Annotation UI Implementation Plan

**Goal:** Deliver the approved focused-annotation UI without changing FloatInk's drawing, service, session, or persistence contracts.

**Architecture:** Keep overlay behavior in existing native Views. Migrate launcher identity to Android adaptive icon layers, correct the Canvas-drawn undo glyph, then preserve the existing horizontal-scroll toolbar/fixed-exit interaction while adding visual-contract coverage.

**Tech Stack:** Kotlin, Android Views/Canvas, Android adaptive icon XML, Robolectric, Gradle.

## Global Constraints

- Start from v0.3.1 on `feat/focused-annotation-ui`.
- Keep `main` untouched until all slices are verified and reviewed.
- No new dependencies, permissions, drawing primitives, persistence keys, or service/window-flag changes.
- Retain 48dp minimum actionable overlay targets.
- Keep Exit fixed and all other operations horizontally scrollable.

## File Map

- `AndroidManifest.xml`: point app and round icons to adaptive resources.
- `res/mipmap-anydpi-v26/ic_launcher*.xml`: adaptive layer declarations.
- `res/drawable/ic_launcher_*`: graphite background, transparent foreground, monochrome launcher resources.
- `DrawingOverlayView.kt`: Canvas undo geometry and existing toolbar visual contract.
- `DrawingOverlayViewTest.kt`: overlay structural, interaction, and narrow-layout regression coverage.
- `MainActivityTest.kt` or a new focused resource test: manifest/adaptive-resource contract where Robolectric supports it.
- `docs/PROJECT_STATE.md`: record completed UI slice and outstanding device checks.

## Task 1: Adaptive Launcher Icon

- [ ] Add a failing manifest/resource contract test or APK-level assertion proving the launcher no longer points to `ic_launcher_floatink`.
- [ ] Define a graphite full-bleed adaptive background, transparent stylus-plus-cyan-stroke foreground, and monochrome foreground inside Android's safe zone.
- [ ] Point `android:icon` and `android:roundIcon` to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- [ ] Run focused tests and `:app:assembleDebug`; inspect APK resources and manifest.
- [ ] Commit the verified icon migration.

## Task 2: Undo Icon Semantics

- [ ] Add a failing `ToolIconView` geometry contract that distinguishes undo from refresh/redo through path direction or a deterministic rendering probe.
- [ ] Change the `undo` branch to a counterclockwise, left/up-leading curved arrow using existing Canvas paint rules.
- [ ] Run `DrawingOverlayViewTest`; inspect the source for no emoji or stale direction constants.
- [ ] Commit the verified icon correction.

## Task 3: Focused Overlay Toolbar

- [ ] Add/extend narrow-width tests for fixed Exit, scroll-contained operations, Clear discoverability, and 48dp action slots.
- [ ] Keep the existing bottom single-line toolbar: scroll region for drag/color/tools/undo/clear/boards/more and fixed Exit on the right.
- [ ] Ensure tool labels, swatches, and active state follow the graphite/cyan/white visual contract without changing tool behavior.
- [ ] Run focused overlay tests and commit the verified slice.

## Task 4: Final Verification and Documentation

- [ ] Update project state with the completed visual changes and device test list.
- [ ] Run `./gradlew testDebugUnitTest :app:assembleDebug`.
- [ ] Verify APK manifest/version/resources and ZIP integrity.
- [ ] Perform a code review focused on adaptive resource packaging, icon semantics, overlay lifecycle boundaries, and narrow-screen regressions.
- [ ] Commit documentation only if separate from the final code slice.
