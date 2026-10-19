# Open TV Touchpad M0 Hardware Gesture Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that the certified XGIMI projector can display an accessibility cursor over Netflix and reliably dispatch tap, horizontal/vertical swipe, drag, and cancellation gestures without ADB in the control path.

**Architecture:** Create the repository and the smallest Android TV application needed for the gate. A D-pad-only gate activity controls an accessibility overlay and a narrow `GesturePort`; no gateway, certificate, PWA, pairing, or production input engine is built. The gate produces a reproducible report tied to one commit, APK hash, firmware, and Netflix version.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Android API 26-36, AndroidX TV-compatible views, AccessibilityService, JUnit 4, AndroidX Test, Espresso accessibility assertions.

**Spec:** `docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`

## Global Constraints

- Future repository path: `open-tv-touchpad/`; package and application ID: `dev.opentvtouchpad`.
- `minSdk=26`, `compileSdk=36`, `targetSdk=36`; release tool versions are pinned in the version catalog and Gradle wrapper.
- Gate UI must be fully operable with the projector D-pad and must have a Leanback launcher entry and banner.
- Accessibility metadata sets `canPerformGestures=true` and `canRetrieveWindowContent=false`.
- Use `TYPE_ACCESSIBILITY_OVERLAY`; do not request `SYSTEM_ALERT_WINDOW`.
- ADB may install and inspect the gate build, but no test gesture is injected with ADB.
- Do not add network, certificate, pairing, WebView, analytics, storage, or app-inspection code.
- M0 passes only on `com.netflix.mediaclient`; success in another app does not count.

## Review Focus

- **Display rotation or size changes:** cursor clamps to current real display bounds and never uses cached 1920x1080 values; covered by `CursorBoundsTest`.
- **Accessibility disabled mid-drag:** active gesture state cancels and the overlay hides; covered by `GestureCoordinatorTest` and a device trial.
- **Repeated D-pad activation:** button key-repeat cannot dispatch duplicate taps or concurrent gestures; covered by `GateViewModelTest`.
- **Gesture callback never arrives:** timeout fails closed and requires an accessibility-service restart before another gesture; covered by `GestureCoordinatorTest`.
- **Partial prior report:** the report verifier rejects a PASS without every required observation, APK hash, and commit; covered by verifier fixtures.

---

### Task 1: Create the Repository and Reproducible Android Baseline

**Files:**
- Create: `open-tv-touchpad/.gitignore`
- Create: `open-tv-touchpad/LICENSE`
- Create: `open-tv-touchpad/README.md`
- Create: `open-tv-touchpad/settings.gradle.kts`
- Create: `open-tv-touchpad/build.gradle.kts`
- Create: `open-tv-touchpad/gradle/libs.versions.toml`
- Create: `open-tv-touchpad/gradle/wrapper/gradle-wrapper.properties`
- Create: `open-tv-touchpad/android/app/build.gradle.kts`
- Create: `open-tv-touchpad/android/app/src/main/AndroidManifest.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/values/strings.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/drawable/tv_banner.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/BuildConfigurationTest.kt`
- Copy: approved `docs/superpowers/specs/` and `docs/superpowers/plans/` into `open-tv-touchpad/docs/superpowers/`

**Interfaces:**
- Consumes: Approved specification and plan set.
- Produces: Buildable Android TV application `dev.opentvtouchpad`, `GateActivity`, and pinned build configuration used by every later task.

- [ ] **Step 1: Initialize the repository and write the failing build-configuration test**

Create the future repository, initialize Git with default branch `main`, copy the approved planning documents, and add `BuildConfigurationTest` asserting application ID `dev.opentvtouchpad`, minimum SDK 26, target SDK 36, non-debuggable release, and no touchscreen requirement.

- [ ] **Step 2: Run the focused test and confirm the unconfigured project fails**

Run: `./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.BuildConfigurationTest`

Expected: FAIL because the Android module or generated build configuration is absent.

- [ ] **Step 3: Add the minimal pinned Gradle project and D-pad launcher activity**

Use Kotlin DSL, a version catalog, Java/Kotlin 17, `minSdk=26`, `compileSdk=36`, `targetSdk=36`, a Leanback launcher intent, `android.software.leanback`, TV banner, and no touchscreen requirement. `GateActivity` renders static M0 instructions with focusable controls only.

- [ ] **Step 4: Verify build and baseline test**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug`

Expected: PASS and `android/app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit the baseline**

```bash
git add .
git commit -m "build: create Android TV gate project"
```

### Task 2: Add the Gate Report Contract

**Files:**
- Create: `open-tv-touchpad/docs/gates/m0-report.md`
- Create: `open-tv-touchpad/docs/gates/m0-checklist.md`
- Create: `open-tv-touchpad/scripts/verify-gate-report.sh`
- Create: `open-tv-touchpad/scripts/testdata/m0-pass.md`
- Create: `open-tv-touchpad/scripts/testdata/m0-missing-observation.md`

**Interfaces:**
- Consumes: Git revision and built APK SHA-256.
- Produces: `verify-gate-report.sh <report> M0`, returning zero only for a complete PASS report.

- [ ] **Step 1: Write verifier fixtures and expected shell assertions**

The PASS fixture must contain exact fields `Gate`, `Decision`, `Commit`, `APK-SHA256`, `Projector`, `Android-TV`, `Firmware`, `Netflix-Version`, `Overlay`, `Tap`, `Vertical-Scroll`, `Horizontal-Scroll`, `Drag`, `Cancel`, `Playback-1080p`, and `Investigation-Count`. The missing-observation fixture omits `Cancel`.

- [ ] **Step 2: Run the verifier before implementation**

Run: `sh scripts/verify-gate-report.sh scripts/testdata/m0-pass.md M0`

Expected: FAIL because the verifier does not exist.

- [ ] **Step 3: Implement the strict POSIX-shell verifier and report template**

Reject duplicate fields, empty values, unknown gate names, `Decision: PASS` with any observation other than `PASS`, an investigation count above one, non-hex commit/hash fields, or unresolved placeholder text.

- [ ] **Step 4: Verify positive and negative fixtures**

Run: `sh scripts/verify-gate-report.sh scripts/testdata/m0-pass.md M0 && ! sh scripts/verify-gate-report.sh scripts/testdata/m0-missing-observation.md M0`

Expected: PASS.

- [ ] **Step 5: Commit the gate contract**

```bash
git add docs/gates scripts
git commit -m "test: define M0 gate report contract"
```

### Task 3: Implement Display Geometry and Gesture Coordination

**Files:**
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/DisplayBounds.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureSpec.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GesturePort.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureCoordinator.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/input/CursorBoundsTest.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/input/GestureCoordinatorTest.kt`

**Interfaces:**
- Consumes: Current display width/height and a platform gesture adapter.
- Produces: `DisplayBounds.clamp(point: PointF): PointF`; sealed `GestureSpec` variants `Tap`, `Swipe`, and `Drag`; `GesturePort.dispatch(spec: GestureSpec, callback: GestureCallback): Boolean`; `GestureCoordinator.submit(spec: GestureSpec): SubmitResult`; `GestureCoordinator.cancel(reason: CancelReason)`.

- [ ] **Step 1: Write failing geometry and coordinator tests**

Cover clamping at every edge, changed display bounds, one in-flight gesture, duplicate submit rejection, completion, Android cancellation, explicit cancellation, and callback timeout. Assert ordinary cancellation returns to `Idle`, timeout requires coordinator recreation, and no queued gesture starts automatically.

- [ ] **Step 2: Run focused unit tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.input.*'`

Expected: FAIL with missing types.

- [ ] **Step 3: Implement the minimal immutable geometry and single-flight coordinator**

Use monotonic time for the timeout. Keep Android classes behind `GesturePort` so unit tests use a fake. Do not add product queues, acceleration, or protocol state.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.input.*'`

Expected: PASS.

- [ ] **Step 5: Commit the gate input core**

```bash
git add android/app/src/main/kotlin/dev/opentvtouchpad/input android/app/src/test/kotlin/dev/opentvtouchpad/input
git commit -m "feat: add bounded M0 gesture coordinator"
```

### Task 4: Add the Accessibility Service and Cursor Overlay

**Files:**
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/AndroidGesturePort.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/CursorOverlay.kt`
- Create: `open-tv-touchpad/android/app/src/main/res/xml/accessibility_service.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/drawable/cursor_ring.xml`
- Modify: `open-tv-touchpad/android/app/src/main/AndroidManifest.xml`
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/AccessibilityManifestTest.kt`
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/CursorOverlayTest.kt`

**Interfaces:**
- Consumes: `GesturePort`, `GestureCoordinator`, and current `DisplayBounds`.
- Produces: system-bound `TouchAccessibilityService`; `CursorOverlay.showAt(x: Int, y: Int)`, `moveTo`, `hide`, and `remove`; Android `dispatchGesture` adapter.

- [ ] **Step 1: Write failing manifest and overlay instrumentation tests**

Assert the accessibility service requires `BIND_ACCESSIBILITY_SERVICE`, is the only non-launcher exported component, reports `canPerformGestures=true`, reports `canRetrieveWindowContent=false`, and creates a non-focusable/non-touchable `TYPE_ACCESSIBILITY_OVERLAY` clamped within test display bounds.

- [ ] **Step 2: Run instrumented tests on an emulator or attached device**

Run: `./gradlew :android:app:connectedDebugAndroidTest`

Expected: FAIL with missing service and overlay.

- [ ] **Step 3: Implement the service, adapter, and overlay**

Translate `GestureSpec` to `GestureDescription`; serialize through `GestureCoordinator`; hide and cancel on `onInterrupt`, `onUnbind`, or disabled service; never inspect windows or accessibility nodes.

- [ ] **Step 4: Verify unit, instrumentation, lint, and APK manifest**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug`

Expected: PASS; merged manifest contains no overlay permission and no unexpected export.

- [ ] **Step 5: Commit accessibility support**

```bash
git add android/app
git commit -m "feat: add M0 accessibility cursor and gestures"
```

### Task 5: Add the D-Pad Gate Controller

**Files:**
- Modify: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- Create: `open-tv-touchpad/android/app/src/main/res/layout/activity_gate.xml`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`

**Interfaces:**
- Consumes: accessibility connection state, `CursorOverlay`, and `GestureCoordinator`.
- Produces: D-pad actions `show_cursor`, `move_up/down/left/right`, `tap`, `swipe_vertical`, `swipe_horizontal`, `drag_path`, `cancel`, and status text.

- [ ] **Step 1: Write failing view-model and D-pad navigation tests**

Assert every action is reachable in a deterministic focus order, key-repeat moves but does not duplicate tap/drag, actions disable while a gesture is in flight, and accessibility-disabled status exposes a settings button.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' :android:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest`

Expected: FAIL with missing controller behavior.

- [ ] **Step 3: Implement the focused gate UI**

Use fixed conservative M0 gesture paths derived from current display bounds. Display current bounds, last dispatch result, callback duration, and cancellation reason; do not inspect or identify the foreground application.

- [ ] **Step 4: Verify the complete local M0 suite**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug`

Expected: PASS.

- [ ] **Step 5: Commit the gate controller**

```bash
git add android/app
git commit -m "feat: add D-pad hardware gate controls"
```

### Task 6: Execute and Record the Certified-Device Gate

**Files:**
- Modify: `open-tv-touchpad/docs/gates/m0-report.md`
- Modify: `open-tv-touchpad/docs/gates/m0-checklist.md`

**Interfaces:**
- Consumes: Built gate APK, projector access, manual accessibility enablement, and Netflix.
- Produces: Validated M0 PASS or FAIL decision; no code interface.

- [ ] **Step 1: Produce and hash a clean gate APK**

Run: `./gradlew clean :android:app:testDebugUnitTest :android:app:lintDebug :android:app:assembleDebug && shasum -a 256 android/app/build/outputs/apk/debug/app-debug.apk`

Expected: tests/lint PASS and one SHA-256 value recorded before installation.

- [ ] **Step 2: Install with local ADB, then stop using ADB for input**

Run: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`. Open the app with the projector remote, manually enable accessibility, and do not run `adb shell input` or any equivalent gesture command.

- [ ] **Step 3: Run the checklist in Netflix**

Record overlay visibility in browsing and playback, tap accuracy, vertical scroll, horizontal scroll, drag, explicit cancel, service disable mid-drag, forced process stop after drag start, and 1080p playback stability. Allow at most one documented compatibility investigation.

- [ ] **Step 4: Validate the report**

Run: `sh scripts/verify-gate-report.sh docs/gates/m0-report.md M0`

Expected: either `M0 report valid: PASS` or a valid FAIL report naming the stop reason. Do not start M1 on FAIL.

- [ ] **Step 5: Commit the immutable gate evidence**

```bash
git add docs/gates/m0-report.md docs/gates/m0-checklist.md
git commit -m "docs: record M0 hardware gate"
```

