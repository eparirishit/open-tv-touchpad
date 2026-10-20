# Task 5 Implementation, Test, and Self-Review Report

## Source

- Branch: `task-5-dpad-gate-controller`
- Task 4 base: `e9d8723`
- Test commit: `b6925c8` (`test: define D-pad gate behavior`)
- Gesture-outcome test commit: `46c9fdd` (`test: define gesture outcome reporting`)
- Implementation commit: `e5d4e0b` (`feat: add D-pad hardware gate controls`)
- Controller-overlay test commit: `9cdf3f5` (`test: cover service D-pad overlay`)
- Controller-overlay implementation commit: `79ba7c6` (`fix: add service-owned D-pad overlay`)
- Detached-outcome test commit: `76dfab7` (`test: cover detached gesture outcome replay`)
- Fix-round implementation commit: `b3189e6` (`fix: keep D-pad controls live over target apps`)
- Fix-round-2 bounds commit: `9636a2e` (`fix: refresh bounds before showing cursor`)
- Fix-round-2 service replay test commit: `23c108a` (`test: cover service outcome listener replay`)
- Final-review compact-overlay commit: `9282e33` (`fix: keep target apps visible under controls`)
- Final-review lifecycle commit: `23c46ee` (`fix: refresh gate on service lifecycle changes`)
- Final-review lint cleanup commit: `c850d0f` (`fix: avoid compact overlay overdraw`)
- Fix-round-4 connected-test commit: `143e898` (`test: close Task 5 connected coverage gaps`)
- Requirements source: `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/task-5-brief.md`

## Scope Implemented

- Replaced the placeholder gate activity with a D-pad-first hardware-gate screen.
- Added deterministic focus order for `show_cursor`, movement, tap, vertical swipe, horizontal swipe, drag, and cancel controls.
- Added a pure `GateViewModel` that derives conservative gesture paths from the current display bounds, tracks the cursor, suppresses repeated non-movement actions, and disables dispatch controls while a gesture is in flight.
- Kept cancel available during an in-flight gesture and restored normal controls after the terminal Android callback.
- Added accessibility-disabled guidance that opens the system accessibility settings screen.
- Displayed current bounds, last `SubmitResult`, callback duration, and cancellation reason.
- Extended the completed Task 4 coordinator/service boundary only enough to publish terminal gesture outcome timing and cancellation reason to the gate UI.

## Fixed M0 Behavior

- Cursor starts at display center and moves by a fixed five-percent step based on the shorter current display dimension.
- Tap uses the current cursor location and the existing conservative short duration.
- Swipe and drag paths stay within the central display area and are recomputed from current bounds.
- Confirm-key repeats are accepted only for movement actions. Tap, swipe, drag, show-cursor, and cancel do not duplicate on key repeat.
- Dispatch controls are disabled after `ACCEPTED`, `DUPLICATE`, or `IN_FLIGHT` until the terminal callback. Cancel remains enabled while in flight.
- A disconnect observed during refresh fails closed to the accessibility-disabled state. Service calls also tolerate a concurrent disconnect without crashing or broadening control capability.

## TDD Evidence

### Baseline

After adding an ignored `local.properties` SDK pointer, the unchanged Task 4 baseline passed:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 8s
85 actionable tasks: 3 executed, 82 up-to-date
```

### RED

The brief's exact focused command was run before production implementation:

```text
./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' :android:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest
```

Observed result:

- `GateViewModelTest`: 5 tests failed because the controller/runtime contract did not exist.
- Gradle stopped at the failed JVM task before running the connected task.
- The connected class was then run alone and failed 2/2 tests because `gate_status` and `show_cursor` did not exist.
- The gesture-outcome observer test failed before implementation because `GestureOutcome` did not exist.
- During self-review, the disconnect-race regression test failed because refresh retained a stale connected state.

These were expected feature-absence failures, not pre-existing failures.

### Focused GREEN

The exact focused command was rerun on the final reviewed code:

```text
BUILD SUCCESSFUL in 9s
76 actionable tasks: 7 executed, 69 up-to-date
```

### Complete Verification

The brief's complete verification command was rerun on the final reviewed code:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 15s
85 actionable tasks: 11 executed, 74 up-to-date
```

Result files contained 22 JVM test cases and 18 connected test cases, all passing. Lint and debug assembly also passed.

## Fix Round 1 Evidence

Independent review identified three blocking defects: the activity controller
could not remain active over Netflix, a terminal gesture outcome could be lost
while an activity listener was detached, and action coordinates could be
derived from stale display bounds. The approved fix retained the activity as
setup guidance while moving global gate control to a separate service-owned,
focusable, non-touchable accessibility overlay.

### RED: Service-Owned Controller

The test-only commit `9cdf3f5` was exported to a temporary source directory and
the focused connected class was run on the API 31 Android TV emulator before
the implementation commit:

```text
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.accessibility.DpadControllerOverlayTest
```

Result: `BUILD FAILED`; 4 tests ran and all 4 failed with
`ClassNotFoundException: dev.opentvtouchpad.accessibility.DpadControllerOverlay`.
This was the expected missing-controller failure. The same class on the final
code reported `BUILD SUCCESSFUL`; 4/4 tests passed.

### RED: Detached Terminal Outcome

The test-only commit `76dfab7` was exported to a temporary source directory and
the detached-outcome regression was run before the production changes:

```text
./gradlew :android:app:testDebugUnitTest \
  --tests 'dev.opentvtouchpad.setup.GateViewModelTest.refreshRecoversTerminalOutcomeDeliveredWhileListenerWasDetached'
```

Result: `BUILD FAILED`; 1 test ran with exactly 1 failure at the assertion that
the stale in-flight state must clear after refresh. On the final code, the
focused setup suite passed 8/8, including this regression. The accessibility
service now retains the latest terminal outcome, supports the activity and
overlay as independent listeners, and publishes authoritative coordinator
in-flight state during refresh.

### RED: Current Bounds Per Action

The bounds regression was added to the preserved six-file worktree before its
production fix and run against that state:

```text
./gradlew :android:app:testDebugUnitTest \
  --tests 'dev.opentvtouchpad.setup.GateViewModelTest'
```

Result: `BUILD FAILED`; 8 tests ran with exactly 1 failure. The new test
expected published bounds `1280×720` but observed stale `1920×1080` bounds.
After the minimal shared bounds-refresh implementation, the same suite passed
8/8. The regression checks movement, tap, vertical swipe, horizontal swipe,
and drag against independently changed bounds and verifies that each new bound
is published before coordinate derivation.

### Focused GREEN

The exact Task 5 focused command passed on the final code:

```text
./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' \
  :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest
BUILD SUCCESSFUL in 12s
76 actionable tasks: 3 executed, 73 up-to-date
```

Result XML contained 8 setup JVM tests and 3 connected activity tests, with 0
failures, 0 errors, and 0 skipped tests.

### Complete GREEN

The complete required matrix passed with the documented Java 17, Android SDK,
and workspace-local Gradle user home:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest \
  :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 19s
85 actionable tasks: 19 executed, 66 up-to-date
```

- JVM XML: 24 tests, 0 failures, 0 errors, 0 skipped.
- Connected XML: 22 tests, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors and 7 pre-existing baseline warnings.
- Merged manifest: one exported Leanback launcher activity and one exported
  accessibility service protected by `BIND_ACCESSIBILITY_SERVICE`; no
  receivers, providers, or requested permissions, including no
  `SYSTEM_ALERT_WINDOW`.
- Accessibility metadata: `canPerformGestures=true`,
  `canRetrieveWindowContent=false`, with no key-filter capability or flag.
- `git diff --check`: clean before the behavior commit.

## Security Review

- No foreground-application inspection or identification was added.
- No accessibility window-content retrieval or broad accessibility action capability was added.
- No overlay permission was added; the existing non-focusable, non-touchable accessibility overlay remains the only cursor surface.
- No manifest permission, exported-component, dependency, network, route, protocol, telemetry, or storage changes were made.
- Gesture paths remain fixed, conservative, and derived only from current display bounds.
- No runtime gesture coordinates, device identifiers, or sensitive diagnostics were added to logs or reports.

## Fix Round 2 Evidence

Scoped re-review found that `SHOW_CURSOR` still used cached bounds and requested
one deterministic production-service regression for multi-listener terminal
delivery and replay.

### RED: Show Cursor Current Bounds

`SHOW_CURSOR` was added to the existing current-bounds regression before the
production change, then run alone:

```text
./gradlew :android:app:testDebugUnitTest \
  --tests 'dev.opentvtouchpad.setup.GateViewModelTest.movementAndEveryGestureRefreshCurrentBoundsBeforeDerivingCoordinates'
```

Result: `BUILD FAILED`; 1 test ran with exactly 1 failure. After bounds changed
from `1920×1080` to `800×600`, the test expected the new bounds to be published
and the cursor clamped to `(799, 540)`, but `SHOW_CURSOR` retained the cached
bounds. The same focused test passed after routing `SHOW_CURSOR` through the
shared `publishCurrentBounds()` path before showing the cursor.

### RED: Service Multi-Listener Replay

The connected regression was first added against the production service API.
Before the existing outcome callback body was extracted into
`publishGestureOutcome`, the smallest compile command failed as expected:

```text
./gradlew :android:app:compileDebugAndroidTestKotlin
```

Result: `BUILD FAILED` with the single relevant compiler error
`Unresolved reference 'publishGestureOutcome'`. After extraction, an initial
connected run exposed a test-fixture equality issue: bound `MutableList::add`
references collapsed in the service's linked set while both lists were empty.
The test was corrected to use identity-distinct lambdas.

A minimal mutation then removed the production membership re-check before
queued delivery. The focused connected test provided behavioral RED evidence:

```text
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='dev.opentvtouchpad.accessibility.AndroidGesturePortTest#serviceSkipsDetachedListenerAndRetainsOutcomeForRefresh'
```

Result XML: 1 test, 1 failure, 0 errors, 0 skipped; failure occurred at the
assertion that the listener removed before queued delivery must receive no
outcome. The mutation was restored, and the same test passed 1/1. The final
test registers two listeners in the real `TouchAccessibilityService` set,
removes one before deterministic queued delivery, verifies exactly one outcome
for the surviving listener, then replaces that listener and verifies the
terminal outcome remains authoritative through `currentGestureState()` without
an unsolicited replay to either detached or newly attached listeners.

### Focused GREEN

The setup and accessibility-focused suites passed:

```text
./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' \
  :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.accessibility.AndroidGesturePortTest
BUILD SUCCESSFUL in 6s
```

Result XML contained 8 setup JVM tests and 12 accessibility connected tests,
with 0 failures, 0 errors, and 0 skipped tests.

The exact Task 5 focused command then passed:

```text
./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' \
  :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest
BUILD SUCCESSFUL in 9s
```

Result XML contained 8 setup JVM tests and 3 connected activity tests, with 0
failures, 0 errors, and 0 skipped tests.

### Complete GREEN

The complete required matrix passed with the documented Java 17, Android SDK,
and workspace-local Gradle user home:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest \
  :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 15s
85 actionable tasks: 11 executed, 74 up-to-date
```

- JVM XML: 24 tests, 0 failures, 0 errors, 0 skipped.
- Connected XML: 23 tests, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors and 7 pre-existing baseline warnings.
- Merged manifest: only the exported Leanback launcher activity and exported,
  `BIND_ACCESSIBILITY_SERVICE`-protected accessibility service; no requested
  permissions, including no `SYSTEM_ALERT_WINDOW`.
- Debug APK assembled successfully.
- `git diff --check`: clean after restoring the RED mutation.

### Fix Round 2 Changed Files

- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/task-5-report.md`
- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/progress.md`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/AndroidGesturePortTest.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- `android/app/src/test/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`

## Final Complete-Review Fix Evidence

The final complete review identified two actionable findings: the
service-owned controller reused the full-screen launcher layout and obscured
the target application, and a visible launcher activity did not observe
accessibility-service disconnection.

### RED: Compact Service Controller

A connected regression was added before production changes:

```text
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='dev.opentvtouchpad.accessibility.DpadControllerOverlayTest#controllerUsesCompactEndAnchoredWindow'
```

Result XML: 1 test, 1 failure, 0 errors, 0 skipped. The test expected
`WRAP_CONTENT` (`-2`) but observed the existing `MATCH_PARENT` (`-1`) window.
The service overlay now inflates a dedicated two-column compact controller,
uses `WRAP_CONTENT × WRAP_CONTENT`, and is anchored at
`END | CENTER_VERTICAL`. The launcher continues to use the separate full
diagnostic layout. The complete controller class passed 5/5, including every
Task 5 action, deterministic focus order, focusable/non-touchable flags, and
service teardown.

### RED: Visible Activity Service Loss

A connected lifecycle regression was added before the observer path:

```text
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='dev.opentvtouchpad.setup.GateActivityDpadTest#visibleServiceLossRecoversGuidanceAndStoppedActivityIgnoresNotifications'
```

Result XML: 1 test, 1 failure, 0 errors, 0 skipped. After service destruction,
the visible activity still displayed the connected state instead of enabling
settings guidance and disabling actions. The service now publishes connection
and disconnection synchronously through a membership-fenced listener set.
`GateActivity` registers while started, unregisters before detaching while
stopped, refreshes immediately on transitions, ignores notifications while
stopped, and reconciles current state when restarted. The complete activity
class passed 4/4, and the affected service class passed 12/12.

### Focused GREEN

The exact Task 5 focused command passed:

```text
./gradlew :android:app:testDebugUnitTest --tests 'dev.opentvtouchpad.setup.*' \
  :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest
BUILD SUCCESSFUL in 9s
```

Result XML contained 8 setup JVM tests and 4 connected activity tests, with 0
failures, 0 errors, and 0 skipped tests. Additional focused connected runs
passed the compact controller class 5/5 and accessibility service class 12/12.

### Complete GREEN

The complete required matrix passed on the final committed source with the
documented Java 17, Android SDK, and workspace-local Gradle user home:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest \
  :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 11s
85 actionable tasks: 3 executed, 82 up-to-date
```

- JVM XML: 24 tests, 0 failures, 0 errors, 0 skipped.
- Connected XML: 25 tests, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors and 7 pre-existing baseline warnings. An initially introduced
  compact-layout overdraw warning was removed before final verification.
- Merged manifest: only the exported Leanback launcher activity and exported,
  `BIND_ACCESSIBILITY_SERVICE`-protected accessibility service; no requested
  permissions, including no `SYSTEM_ALERT_WINDOW`.
- Accessibility metadata remains gesture-only with
  `canPerformGestures=true`, `canRetrieveWindowContent=false`, and no key-filter
  capability, foreground inspection, or package restriction.
- Cursor overlay remains non-focusable/non-touchable; controller overlay
  remains focusable/non-touchable.
- Debug APK assembled successfully; `git diff --check` was clean.

### Final Review Changed Files

- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/task-5-report.md`
- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/progress.md`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/CursorOverlayTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlay.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- `android/app/src/main/res/layout/controller_overlay.xml`

## Fix Round 4 Test-Only Coverage Closure

Scoped review requested exactly two connected-test closures. Production code,
resources, manifest, metadata, dependencies, and capability remained unchanged.

### RED: Live Disconnected-to-Connected Activity Transition

The prior lifecycle test used reflection to assign `connectedService` and invoke
`GateActivity.render()`. It was replaced with a system-driven scenario that
starts from a visible disconnected activity, enables the real accessibility
service through secure settings while using
`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, waits for Android to call
`TouchAccessibilityService.onServiceConnected()`, and therefore exercises the
production `publishConnectedService(this)` notification path. The test verifies
connected status, every normal action enabled, cancel disabled, and settings
guidance disabled. It then stops the activity, disables the service, verifies
the stopped UI ignored the disconnect notification, and verifies restart
reconciles to disabled guidance.

A temporary mutation removed only
`TouchAccessibilityService.addConnectionListener(serviceConnectionListener)`
from `GateActivity.onStart()`. The focused connected test bound the real service
in under one second and then failed at `assertConnectedControls`, proving the
activity remained disconnected without the production listener path. The line
was restored before GREEN verification; no production mutation was committed.

### RED: Real Inflated Overlay Occupancy

`controllerUsesCompactEndAnchoredWindow` now measures and lays out the actual
inflated controller root under `1920×1080` `AT_MOST` bounds. In addition to the
existing `WRAP_CONTENT × WRAP_CONTENT` and `END | CENTER_VERTICAL` assertions,
it requires occupied width below `960`, height below `810`, and occupied area
below `691200` square pixels. The one-third-area cap leaves more than two thirds
of the target visible while the independent action, focus-order, teardown, and
focusable/non-touchable security tests remain intact.

A temporary layout mutation changed only root padding from `8dp` to `400dp`,
preserving the existing window parameters. The focused connected test failed at
the new occupied-width assertion, proving an oversized root can no longer pass
through `WRAP_CONTENT` alone. The padding was restored before GREEN
verification; no resource mutation was committed.

### Focused GREEN

Both complete connected classes passed independently on the API 31 Android TV
emulator:

```text
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.setup.GateActivityDpadTest
BUILD SUCCESSFUL in 10s
4 tests, 0 failures, 0 errors, 0 skipped

./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.opentvtouchpad.accessibility.DpadControllerOverlayTest
BUILD SUCCESSFUL in 4s
5 tests, 0 failures, 0 errors, 0 skipped
```

### Complete GREEN

The complete required matrix passed on commit `143e898` with Java 17, the
documented Android SDK, workspace-local Gradle state, and
`ANDROID_SERIAL=emulator-5554`:

```text
./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest \
  :android:app:lintDebug :android:app:assembleDebug
BUILD SUCCESSFUL in 14s
85 actionable tasks: 5 executed, 80 up-to-date
```

- JVM XML: 24 tests, 0 failures, 0 errors, 0 skipped.
- Connected XML: 25 tests, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors and 7 pre-existing baseline warnings.
- Merged manifest: one exported Leanback launcher activity and one exported,
  `BIND_ACCESSIBILITY_SERVICE`-protected accessibility service; no receivers,
  providers, or requested permissions, including no `SYSTEM_ALERT_WINDOW`.
- Accessibility metadata remains gesture-only with
  `canPerformGestures=true`, `canRetrieveWindowContent=false`, and no key-filter
  capability, foreground inspection, or package restriction.
- Debug APK assembled at `android/app/build/outputs/apk/debug/app-debug.apk`.
- Commit `143e898` changes only the two connected-test classes, and both the
  commit-range and worktree `git diff --check` checks were clean.

### Fix Round 4 Changed Files

- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/task-5-report.md`
- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/progress.md`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`

## Self-Review

Review range: Task 4 base `e9d8723` through fix-round-4 connected-test commit
`143e898`, including all connected callers and final-review tests.

- Confirmed all `GestureCoordinator` callers remain source-compatible through the default outcome listener.
- Confirmed stale callbacks still cannot finish successor gestures and timeout remains fail-closed.
- Found and fixed one actionable race: accessibility service loss between connection and bounds checks could retain stale state or crash service calls. Added a focused RED-to-GREEN regression test and null-safe service adapter calls.
- Investigated a possible in-flight focus loss. A connected regression test showed Android moves focus to the enabled cancel action; no production workaround was needed.
- Confirmed deterministic action focus order, in-flight disabling, cancel availability, settings guidance, and key-repeat behavior through focused tests.
- Confirmed the service-owned controller remains focusable/non-touchable while the cursor remains non-focusable/non-touchable, and that no key filtering, foreground inspection, overlay permission, or exported component was added.
- Confirmed detached terminal outcomes and authoritative in-flight state reconcile on refresh, and all coordinate actions publish fresh bounds before derivation.
- Confirmed `SHOW_CURSOR` now publishes and clamps against current bounds before display.
- Confirmed queued service delivery skips a listener removed after outcome capture, delivers exactly once to a still-attached peer, and retains the terminal outcome for refresh/re-attach.
- Confirmed the compact controller leaves the target application visible while retaining every Task 5 action and deterministic D-pad traversal.
- Confirmed service connection notifications are synchronous, membership-fenced, registered only while the launcher is started, and removed before view-model detachment.
- Confirmed the live-connection regression uses Android's real accessibility-service bind and production publication path rather than reflective state assignment or manual rendering, restores secure settings in `finally`, and proves stopped-listener cleanup behavior.
- Confirmed the compactness regression measures the real inflated overlay against fixed display bounds and fails for oversized content even when window parameters remain `WRAP_CONTENT`.
- Confirmed the manifest and app dependency configuration have no diff.
- Final verdict: ship Task 5 for emulator/local verification. No open Critical, Important, or Minor findings.

This is an author self-review because subagents were explicitly prohibited; it is weaker than an independent review.

## Changed Files

- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/task-5-report.md`
- `.superpowers/sdd/2026-10-18-open-tv-touchpad-m0-hardware-gate/progress.md`
- `README.md`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/AccessibilityManifestTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/AndroidGesturePortTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlayTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/DpadControllerOverlay.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureCoordinator.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/ServiceGateRuntime.kt`
- `android/app/src/main/res/layout/activity_gate.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`
- `android/app/src/test/kotlin/dev/opentvtouchpad/input/GestureCoordinatorTest.kt`
- `docs/gates/m0-checklist.md`
- `docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`

## Concerns and Limits

- Verification used the configured API 31 Android TV emulator. No certified projector claim is made.
- The emulator verifies window flags and D-pad behavior but cannot prove that
  the certified XGIMI accessibility shortcut keeps controller focus over
  Netflix; that remains a Task 6 hardware PASS/FAIL observation.
- Physical D-pad feel, real accessibility gesture delivery, callback timing, and cancellation behavior still require the separately planned hardware validation; Task 6 was not implemented.
- Lint retains 7 baseline warnings unrelated to this fix; there are 0 errors.
- `.kotlin/` build scratch was removed before the implementation commit. `local.properties` remains ignored and uncommitted.
