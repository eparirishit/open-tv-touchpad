# Open TV Touchpad M0.1 Gesture Investigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and execute one bounded, debug-only investigation that determines whether system tap timing or removal of the focusable M0 controller allows accessibility-dispatched taps to activate a Netflix profile tile on the certified XGIMI projector.

**Architecture:** Keep the existing gesture port and product boundary intact. Add a debug-only closed probe model, a pure generation-fenced controller-handoff state machine, a small Android scheduler/executor, a first-party calibration activity, and a separate fail-closed M0.1 evidence verifier. The original M0 report remains immutable; only a verified `PROCEED_TO_M0_RETEST` result may trigger a complete M0 rerun on the same APK.

**Tech Stack:** Kotlin/JVM 17, Android API 26-36, Android AccessibilityService, `Choreographer`, `Handler`, JUnit 4, AndroidX Test, POSIX shell, Gradle 9.6.

**Spec:** `docs/superpowers/specs/2026-10-20-open-tv-touchpad-m0-1-gesture-investigation-design.md`

## Global Constraints

- Preserve `docs/gates/m0-report.md` as the immutable 2026-10-20 `FAIL` result.
- M0.1 exposes exactly `BASELINE_80_MS`, `SYSTEM_TAP_TIMEOUT`, and `CONTROLLER_HANDOFF`; accept no free-form mode, duration, delay, coordinate, package, intent, or gesture path.
- Use `ViewConfiguration.getTapTimeout()` for both non-baseline modes; do not replace it with a guessed constant.
- Consume the matching D-pad key-up before removing the controller; no confirmation key event may reach Netflix.
- Race the handoff frame against a 250 ms monotonic deadline and restore the controller exactly once after 100 ms only for a completed gesture in the same healthy service generation.
- Rejection, cancellation, callback timeout, frame timeout, interruption, disablement, unbind, and shutdown clear pending work; lifecycle shutdown removes both overlays.
- No automatic retry. A mode gets a second attempt only after its first `COMPLETED_ACTIVATED` result, and never gets a third attempt.
- A first-party calibration result is diagnostic only and cannot satisfy M0.
- Use ADB only for installation and read-only diagnostics. Never use `adb shell input` or equivalent injection.
- Keep screen contents, profile names, raw key events, exact gesture coordinates, package inventories, and raw protocol or ADB output out of persisted logs and evidence.
- All M0.1 activities, classes, layouts, strings, and diagnostics remain debug-only and absent from release artifacts.
- Keep accessibility manually enabled, `canRetrieveWindowContent=false`, event types empty, and filter-key-event capability disabled.
- M1 remains blocked until a separate complete M0 report verifies as `PASS`.
- Every connected-test command must set `ANDROID_SERIAL=emulator-5554`; never allow Gradle to select the projector.

## Review Focus

- **Confirmation key leakage:** removing the controller before consuming key-up could activate Netflix through the remote rather than the accessibility tap; Task 4 adds an instrumentation test that proves removal occurs only after matching key-up is consumed.
- **Late callback resurrection:** a frame, gesture, or restoration callback from an old service generation could re-add the blocking controller after shutdown; Tasks 3 and 4 test generation fencing across every late callback type.
- **Evidence-order bypass:** mixed artifacts, a third attempt, or a failed confirmation could incorrectly authorize a rerun; Task 1 supplies rejecting fixtures for each case.
- **Missing-frame deadlock:** `Choreographer` may never deliver a frame; Tasks 3 and 4 prove the 250 ms deadline produces `HANDOFF_FRAME_TIMEOUT`, removes the controller, and ignores the late frame.
- **Release leakage:** new debug classes or resources could enter the release APK through main-source references; Task 5 mutates each new descriptor/resource class and proves the real release verifier rejects it.

---

### Task 1: Add the M0.1 Evidence Contract and Fail-Closed Verifier

**Files:**
- Create: `scripts/verify-m0-1-report.sh`
- Create: `scripts/test-verify-m0-1-report.sh`
- Create: `scripts/testdata/m0-1-proceed.md`
- Create: `scripts/testdata/m0-1-stop-exhausted.md`
- Create: `scripts/testdata/m0-1-stop-safety.md`
- Create: `scripts/testdata/m0-1-mixed-artifact.md`
- Create: `scripts/testdata/m0-1-out-of-order.md`
- Create: `scripts/testdata/m0-1-third-attempt.md`
- Create: `scripts/testdata/m0-1-failed-confirmation.md`
- Create: `scripts/testdata/m0-1-incomplete-safety.md`
- Create: `scripts/testdata/m0-1-missing-identity.md`
- Create: `scripts/testdata/m0-1-mixed-winner.md`
- Create: `scripts/testdata/m0-1-cumulative-investigation.md`
- Create: `docs/gates/m0-1-report.md`
- Create: `docs/gates/m0-1-checklist.md`

**Interfaces:**
- Consumes: The field and outcome rules in specification section 9.
- Produces: `scripts/verify-m0-1-report.sh <report>`, returning zero only for a complete `PROCEED_TO_M0_RETEST` or valid `STOP` report; immutable report/checklist templates for Task 7.

- [ ] **Step 1: Write the shell regression harness and fixtures**

The verifier contract uses these exact fields: `Gate`, `Outcome`, `Commit`, `APK-SHA256`, `Projector`, `Android-TV`, `Firmware`, `Netflix-Version`, `Tested-Application`, `Original-M0-Commit`, `Original-M0-APK-SHA256`, `Original-M0-Decision`, `Original-M0-Investigation-Count`, `M0.1-Investigation-Count`, two attempt fields for each of the three probe modes, three calibration fields, `Calibration-Classification`, `Winning-Mode`, `Key-Leakage`, `Controller-Lifecycle`, `No-ADB-Input`, `Evidence-Integrity`, and `Stop-Reason`.

`scripts/test-verify-m0-1-report.sh` must assert that the three valid fixtures pass and every invalid fixture fails for the named reason. It also derives unknown-field, duplicate-field, malformed-field, empty-value, and placeholder mutations from the valid proceed fixture and requires each to fail. Attempt values are restricted to `NOT_RUN`, `REJECTED`, `CANCELLED`, `TIMEOUT`, `HANDOFF_FRAME_TIMEOUT`, `COMPLETED_NOT_ACTIVATED`, and `COMPLETED_ACTIVATED`.

- [ ] **Step 2: Run the harness before implementation**

Run: `sh scripts/test-verify-m0-1-report.sh`

Expected: FAIL because `scripts/verify-m0-1-report.sh` does not exist.

- [ ] **Step 3: Implement the POSIX-shell verifier and evidence templates**

Use strict field parsing equivalent to `scripts/verify-gate-report.sh`: reject unknown, duplicate, missing, malformed, empty, and placeholder values. Enforce the exact identity, ordering, attempt-count, winning-mode, calibration, safety-stop, and separately scoped investigation-count rules from specification section 9. Keep `docs/gates/m0-1-report.md` unresolved and verifier-rejected until certified execution.

- [ ] **Step 4: Verify valid and invalid evidence behavior**

Run: `sh scripts/test-verify-m0-1-report.sh && sh -n scripts/verify-m0-1-report.sh scripts/test-verify-m0-1-report.sh`

Expected: PASS; the harness reports three accepted fixtures, eight rejected fixtures, and five rejected parser mutations, each for its intended rule.

- [ ] **Step 5: Commit the evidence contract**

```bash
git add scripts/verify-m0-1-report.sh scripts/test-verify-m0-1-report.sh scripts/testdata/m0-1-*.md docs/gates/m0-1-report.md docs/gates/m0-1-checklist.md
git commit -m "test: define M0.1 evidence contract"
```

### Task 2: Add Closed Tap Probe Modes

**Files:**
- Create: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/TapProbeMode.kt`
- Create: `android/app/src/testDebug/kotlin/dev/opentvtouchpad/setup/TapProbeModeTest.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- Modify: `android/app/src/testDebug/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`

**Interfaces:**
- Consumes: `GestureSpec.Tap`, `SubmitResult`, current display bounds, and an injected `systemTapTimeoutMillis` obtained by Android callers from `ViewConfiguration.getTapTimeout()`.
- Produces: `TapProbeMode`, `TapProbeConfig`, `TapProbeRequest`, `TapProbePolicy.config(mode, systemTapTimeoutMillis)`, `GateViewModel.selectTapProbeMode(mode: TapProbeMode)`, `GateViewModel.prepareTapProbe(): TapProbeRequest?`, `GateViewModel.submitTapProbe(request: TapProbeRequest): SubmitResult`, `GateViewModel.setTapProbeOutcomeListener(listener: ((GestureOutcome) -> Unit)?)`, and probe-mode state consumed by Task 4.

- [ ] **Step 1: Write failing closed-mode and ViewModel tests**

Add tests named `probeModesHaveFixedOrderAndNoFreeFormValues`, `baselineKeepsEightyMillisecondTap`, `systemModesUseInjectedAndroidTapTimeout`, `prepareTapRefreshesBoundsWithoutDispatching`, `submitTapDispatchesPreparedRequestExactlyOnce`, `probeOutcomeListenerReceivesOnlyThePreparedAttempt`, and `modeSelectionDoesNotAdvancePastControllerHandoff`. Assert the exact mode order, 80 ms baseline, injected system timeout, current-bound cursor point, and `requiresControllerHandoff` only for `CONTROLLER_HANDOFF`.

- [ ] **Step 2: Run the focused tests before implementation**

Run: `./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.setup.TapProbeModeTest --tests dev.opentvtouchpad.setup.GateViewModelTest`

Expected: FAIL because the probe types and ViewModel interfaces are absent.

- [ ] **Step 3: Implement the closed probe model**

Define:

```kotlin
internal enum class TapProbeMode {
    BASELINE_80_MS,
    SYSTEM_TAP_TIMEOUT,
    CONTROLLER_HANDOFF,
}

internal data class TapProbeConfig(
    val mode: TapProbeMode,
    val durationMillis: Long,
    val requiresControllerHandoff: Boolean,
)

internal data class TapProbeRequest(
    val config: TapProbeConfig,
    val gesture: GestureSpec.Tap,
)

internal object TapProbePolicy {
    fun config(mode: TapProbeMode, systemTapTimeoutMillis: Long): TapProbeConfig
}
```

Require `GateViewModel(runtime, systemTapTimeoutMillis)` with no production default. Android callers pass `ViewConfiguration.getTapTimeout().toLong()`; tests inject exact values. Mode selection is a closed D-pad action and never accepts text or numbers.

`prepareTapProbe()` returns `null` unless the runtime is connected, controls are enabled, and no gesture is in flight. A prepared request is opaque to the UI and can be submitted once; duplicate submission returns `SubmitResult.REJECTED`. `setTapProbeOutcomeListener` is one observer for the active prepared attempt and is cleared by `detach()`.

- [ ] **Step 4: Run focused and complete debug JVM tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.setup.TapProbeModeTest --tests dev.opentvtouchpad.setup.GateViewModelTest && ./gradlew :android:app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit the closed probe modes**

```bash
git add android/app/src/debug/kotlin/dev/opentvtouchpad/setup android/app/src/testDebug/kotlin/dev/opentvtouchpad/setup
git commit -m "feat: add closed tap probe modes"
```

### Task 3: Add the Generation-Fenced Controller Handoff State Machine

**Files:**
- Create: `android/app/src/debug/kotlin/dev/opentvtouchpad/accessibility/ControllerHandoffMachine.kt`
- Create: `android/app/src/testDebug/kotlin/dev/opentvtouchpad/accessibility/ControllerHandoffMachineTest.kt`

**Interfaces:**
- Consumes: Matching confirm down/up events, frame and deadline callbacks, `SubmitResult`, gesture terminal outcomes, restoration deadline, and lifecycle stop.
- Produces: `ControllerHandoffMachine.handle(event: ControllerHandoffEvent): List<ControllerHandoffEffect>`, monotonically increasing `ControllerHandoffToken` values, and closed states `IDLE`, `ARMED`, `WAITING_FOR_FRAME`, `DISPATCHING`, `RESTORE_PENDING`, and `DEGRADED` for Task 4. Events are `ConfirmDown`, `ConfirmUp`, `FrameReady`, `FrameDeadline`, `DispatchReturned`, `GestureFinished`, `RestoreDeadline`, and `LifecycleStopped`; every asynchronous event carries its token.

- [ ] **Step 1: Write failing state-machine tests**

Cover: repeat key-down ignored; mismatched, missing, and duplicate key-up cannot remove or re-remove the controller; matching key-up emits remove-controller, frame request, and 250 ms deadline effects once; frame-ready emits dispatch once; frame timeout emits hide-cursor, clear-input, restart-required, and `HANDOFF_FRAME_TIMEOUT`; late frame after timeout is ignored; accepted completion schedules one 100 ms restoration; rejection/cancellation/callback timeout degrade without restoration; no transition retries automatically; late frame, gesture, or restoration callbacks after lifecycle stop are ignored; lifecycle stop removes both overlays and fences every old generation.

- [ ] **Step 2: Run the state-machine tests before implementation**

Run: `./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.accessibility.ControllerHandoffMachineTest`

Expected: FAIL because `ControllerHandoffMachine` is absent.

- [ ] **Step 3: Implement the pure handoff machine**

Define sealed `ControllerHandoffEvent` and `ControllerHandoffEffect` types with generation-bearing frame, timeout, dispatch, gesture, and restore events. `ConfirmDown` carries the matching confirm key code and the opaque `TapProbeRequest`; `ConfirmUp` carries only its key code. Effects are closed to controller removal/restoration, frame/deadline scheduling and cancellation, one request submission, cursor hiding, pending-input clearing, terminal-state publication, and both-overlay removal. Keep Android classes out of this file. Constants are exactly `FRAME_TIMEOUT_MILLIS = 250L` and `RESTORE_DELAY_MILLIS = 100L`. Every transition returns explicit effects and no transition schedules unbounded work.

- [ ] **Step 4: Run mutation-sensitive focused tests**

Run the focused test, then temporarily remove the generation comparison for one late callback and rerun to observe at least one failure. Restore the comparison and rerun.

Run: `./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.accessibility.ControllerHandoffMachineTest`

Expected: RED under mutation, then PASS after restoration.

- [ ] **Step 5: Commit the handoff state machine**

```bash
git add android/app/src/debug/kotlin/dev/opentvtouchpad/accessibility/ControllerHandoffMachine.kt android/app/src/testDebug/kotlin/dev/opentvtouchpad/accessibility/ControllerHandoffMachineTest.kt
git commit -m "feat: add bounded controller handoff"
```

### Task 4: Integrate Controller Handoff with the Android Overlay

**Files:**
- Create: `android/app/src/debug/kotlin/dev/opentvtouchpad/accessibility/AndroidControllerHandoffExecutor.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlay.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/ServiceGateRuntime.kt`
- Modify: `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- Modify: `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt`
- Modify: `android/app/src/testDebug/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 `TapProbeRequest`; Task 3 events/effects; Android main `Choreographer`, main-thread `Handler`, `WindowManager`, and existing `GateRuntime` gesture state.
- Produces: `AndroidControllerHandoffExecutor.handle(event: ControllerHandoffEvent)`, `AndroidControllerHandoffExecutor.shutdown()`, matching key-up handoff in `DpadControllerOverlay`, lifecycle-safe window detach/reattach, `GateRuntime.hideCursor()`, and visible `HANDOFF_FRAME_TIMEOUT`/restart-required probe state for Task 5.

- [ ] **Step 1: Write failing integration and instrumentation tests**

Add host tests for `GateRuntime.hideCursor()` and terminal handoff state publication. Add connected tests proving: key-down alone does not remove the overlay; matching key-up is consumed before removal; duplicate key-up is inert; the controller window is absent when gesture submission occurs; submission occurs only from the first `Choreographer` frame callback after removal; the cursor remains visible, non-focusable, and non-touchable during the frame wait; every degraded result (`REJECTED`, `CANCELLED`, callback `TIMEOUT`, and frame timeout) removes the controller, hides the cursor, and requires restart; completion restores once after 100 ms; interruption, unbind, and destruction before frame, gesture callback, or restoration remove both overlays and ignore queued callbacks.

- [ ] **Step 2: Run focused tests before integration**

Run:

```bash
./gradlew :android:app:testDebugUnitTest --tests dev.opentvtouchpad.setup.GateViewModelTest
ANDROID_SERIAL=emulator-5554 ./gradlew :android:app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.accessibility.DpadControllerOverlayTest'
```

Expected: FAIL because the Android executor and handoff integration are absent.

- [ ] **Step 3: Implement the Android effect executor and overlay handoff**

`AndroidControllerHandoffExecutor` maps machine effects to `Choreographer.postFrameCallback`, `SystemClock.elapsedRealtime()`-based main-handler deadlines, controller window detach/attach, cursor hide, and one prepared tap submission. Constructor dependencies include small frame-scheduler, monotonic-deadline, and controller-window adapters so ordering and late-callback behavior are deterministic in tests. It stores cancellable callbacks per token and clears them on `shutdown()`. `DpadControllerOverlay.remove()` calls executor shutdown before stopping its ViewModel and removing windows. Handoff detaches the window without detaching the ViewModel so the accepted gesture callback remains observable. `TouchAccessibilityService.onInterrupt()`, unbind, and destruction invoke the same fail-closed overlay shutdown path.

- [ ] **Step 4: Run focused and complete emulator tests**

Run:

```bash
./gradlew :android:app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :android:app:connectedDebugAndroidTest
```

Expected: PASS; connected results contain only `emulator-5554`.

- [ ] **Step 5: Commit Android handoff integration**

```bash
git add android/app/src/debug/kotlin/dev/opentvtouchpad android/app/src/testDebug/kotlin/dev/opentvtouchpad android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt
git commit -m "feat: integrate controller handoff"
```

### Task 5: Add Calibration, Probe Diagnostics, and Release Isolation

**Files:**
- Create: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/CalibrationActivity.kt`
- Create: `android/app/src/debug/res/layout/activity_calibration.xml`
- Modify: `android/app/src/debug/AndroidManifest.xml`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- Modify: `android/app/src/debug/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlay.kt`
- Modify: `android/app/src/debug/res/layout/activity_gate.xml`
- Modify: `android/app/src/debug/res/layout/controller_overlay.xml`
- Modify: `android/app/src/debug/res/values/strings.xml`
- Create: `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/CalibrationActivityTest.kt`
- Modify: `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`
- Modify: `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt`
- Modify: `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/AccessibilityManifestTest.kt`
- Modify: `scripts/verify-release-gate-isolation.sh`
- Modify: `scripts/test-verify-release-gate-isolation.sh`

**Interfaces:**
- Consumes: Task 2 probe-mode state and Task 4 controller/handoff state.
- Produces: A fixed debug-only 3×3 calibration target, D-pad controls for mode selection and manual `ACTIVATED`/`NOT_ACTIVATED` recording, ephemeral admission/callback/controller diagnostics, and release scans covering every M0.1 artifact.

- [ ] **Step 1: Write failing UI and release-isolation tests**

Assert that calibration is non-exported, contains exactly nine fixed targets, marks only the center target as intended, and records no coordinates or external screen data. Assert deterministic D-pad reachability for mode selection, tap, manual result, and calibration launch. Reassert that accessibility is manually enabled, `canRetrieveWindowContent=false`, event types are empty, and filter-key-event capability remains disabled. Extend the release-verifier regression to reject descriptors containing `TapProbe`, `ControllerHandoff`, `CalibrationActivity`, and `AndroidControllerHandoffExecutor`, plus `activity_calibration`, new probe layouts, and `m0_1_`/`probe_` strings.

- [ ] **Step 2: Run focused tests before implementation**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :android:app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.CalibrationActivityTest,dev.opentvtouchpad.setup.GateActivityDpadTest,dev.opentvtouchpad.accessibility.DpadControllerOverlayTest'
sh scripts/test-verify-release-gate-isolation.sh
```

Expected: FAIL because calibration/diagnostics are absent and the release denylist accepts the new fixture descriptors.

- [ ] **Step 3: Implement calibration and bounded diagnostics**

`CalibrationActivity` is debug-only and `android:exported="false"`. Launch uses one fixed in-app intent; no package or action input is accepted. UI diagnostics display only mode, admission, callback outcome/duration, display dimensions, controller state, and manual target result. Do not use Logcat for probe evidence.

- [ ] **Step 4: Extend real release isolation and verify**

Run:

```bash
sh scripts/test-verify-release-gate-isolation.sh
./gradlew :android:app:assembleRelease :android:app:verifyReleaseGateIsolation
ANDROID_SERIAL=emulator-5554 ./gradlew :android:app:connectedDebugAndroidTest
```

Expected: PASS; the release APK contains no M0/M0.1 manifest entry, class, layout, or string.

- [ ] **Step 5: Commit calibration and isolation**

```bash
git add android/app/src/debug android/app/src/androidTest scripts/verify-release-gate-isolation.sh scripts/test-verify-release-gate-isolation.sh
git commit -m "feat: add M0.1 calibration diagnostics"
```

### Task 6: Produce the Reviewed Pre-Hardware APK

**Files:**
- Modify: `docs/gates/m0-1-report.md`
- Modify: `docs/gates/m0-1-checklist.md`
- Modify: `docs/progress.md`
- Modify: `docs/capabilities.md`

**Interfaces:**
- Consumes: Tasks 1-5 and emulator `emulator-5554`.
- Produces: Reviewed source commit and clean debug APK SHA-256 ready for certified-device installation; no hardware result.

- [ ] **Step 1: Run the clean emulator-only verification matrix**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew clean :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug :android:app:assembleRelease
sh scripts/test-verify-m0-1-report.sh
sh scripts/test-verify-release-gate-isolation.sh
sh scripts/verify-release-gate-isolation.sh
git diff --exit-code a192429 -- docs/gates/m0-report.md
git diff --check
```

Expected: PASS; the connected report contains only `emulator-5554`, and the original M0 report is byte-for-byte unchanged from its evidence commit.

- [ ] **Step 2: Run complete Codex review and fix actionable findings**

Review the entire branch against the M0.1 spec, including untracked files, release artifacts, lifecycle callers, and report fixtures. Fix all in-scope findings with RED→GREEN tests, commit each acceptance-criterion batch, and repeat review until clean.

- [ ] **Step 3: Rerun the clean matrix after review fixes**

Run the exact Step 1 matrix again.

Expected: PASS.

- [ ] **Step 4: Record final build identity without claiming hardware behavior**

Run: `git rev-parse HEAD && shasum -a 256 android/app/build/outputs/apk/debug/app-debug.apk`

Record the post-review commit and digest in the M0.1 report/checklist. Keep every manual probe result unresolved and keep the verifier failing closed. Do not modify executable code after this step without repeating Steps 1-4 and replacing the APK identity.

- [ ] **Step 5: Commit pre-hardware evidence**

```bash
git add docs/gates/m0-1-report.md docs/gates/m0-1-checklist.md docs/progress.md docs/capabilities.md
git commit -m "docs: prepare M0.1 hardware evidence"
```

### Task 7: Execute the Certified M0.1 Investigation

**Files:**
- Modify: `docs/gates/m0-1-report.md`
- Modify: `docs/gates/m0-1-checklist.md`
- Conditionally create: `docs/gates/m0-rerun-report.md`
- Conditionally create: `docs/gates/m0-rerun-checklist.md`
- Modify: `docs/progress.md`
- Modify: `docs/capabilities.md`
- Modify: `docs/architecture.md`
- Modify: `BACKLOG.md`

**Interfaces:**
- Consumes: Task 6 APK, certified XGIMI access, manual accessibility enablement, Netflix, and the fixed execution order.
- Produces: Verified M0.1 `STOP` or `PROCEED_TO_M0_RETEST`; conditionally a complete verified M0 rerun. No code interface.

- [ ] **Step 1: Install and attest without ADB input**

Install the exact Task 6 APK with `adb install -r`. Record installation success and read-only identity. After installation, do not use `adb shell input`, accessibility enablement commands, or any equivalent input injection.

- [ ] **Step 2: Execute the fixed Netflix probes**

Follow specification section 7 exactly. Record admission, callback, manual activation, controller lifecycle, key leakage, restart, and attempt counts. Never run a third attempt. On a safety failure, stop probes immediately but still finalize evidence.

- [ ] **Step 3: Execute calibration only when required**

Run the three fixed calibration attempts only after compatibility exhaustion, never after a safety stop or successful Netflix confirmation. Record only closed outcomes and classification.

- [ ] **Step 4: Verify the M0.1 outcome**

Run: `sh scripts/verify-m0-1-report.sh docs/gates/m0-1-report.md`

Expected: exit zero with either valid `STOP` or `PROCEED_TO_M0_RETEST`. Any rejection means evidence is incomplete or inconsistent; do not rerun probes merely to make the report pass.

- [ ] **Step 5: Conditionally rerun complete M0**

Only for verified `PROCEED_TO_M0_RETEST`, use the same APK and execute every original M0 check without further compatibility changes. Preserve `docs/gates/m0-report.md`; write the new evidence to `docs/gates/m0-rerun-report.md` and `docs/gates/m0-rerun-checklist.md` with `Investigation-Count: 0`.

Run: `sh scripts/verify-gate-report.sh docs/gates/m0-rerun-report.md M0`

Expected: `M0 report valid: PASS` to unblock M1, or verifier rejection with a recorded `FAIL` stop reason that keeps M1 blocked.

- [ ] **Step 6: Update project status and commit immutable evidence**

Update progress, capabilities, architecture, and backlog with only observed results. Commit:

```bash
git add docs/gates docs/progress.md docs/capabilities.md docs/architecture.md BACKLOG.md
git commit -m "docs: record M0.1 investigation"
```

## Plan Completion Check

Before Task 7 hardware execution, all of these must pass:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew clean :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug :android:app:assembleRelease
sh scripts/test-verify-m0-1-report.sh
sh scripts/test-verify-release-gate-isolation.sh
sh scripts/verify-release-gate-isolation.sh
git diff --check
```

After Task 7, `scripts/verify-m0-1-report.sh` must exit zero. M1 remains blocked unless the conditional full M0 rerun also exits zero with `M0 report valid: PASS`.
