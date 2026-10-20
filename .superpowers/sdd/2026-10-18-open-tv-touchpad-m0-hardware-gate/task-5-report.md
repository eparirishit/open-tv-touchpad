# Task 5 Implementation, Test, and Self-Review Report

## Source

- Branch: `task-5-dpad-gate-controller`
- Task 4 base: `e9d8723`
- Test commit: `b6925c8` (`test: define D-pad gate behavior`)
- Gesture-outcome test commit: `46c9fdd` (`test: define gesture outcome reporting`)
- Implementation commit: `e5d4e0b` (`feat: add D-pad hardware gate controls`)
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

## Security Review

- No foreground-application inspection or identification was added.
- No accessibility window-content retrieval or broad accessibility action capability was added.
- No overlay permission was added; the existing non-focusable, non-touchable accessibility overlay remains the only cursor surface.
- No manifest permission, exported-component, dependency, network, route, protocol, telemetry, or storage changes were made.
- Gesture paths remain fixed, conservative, and derived only from current display bounds.
- No runtime gesture coordinates, device identifiers, or sensitive diagnostics were added to logs or reports.

## Self-Review

Review range: Task 4 base `e9d8723` through implementation `e5d4e0b`, including untracked files before commit and all connected callers.

- Confirmed all `GestureCoordinator` callers remain source-compatible through the default outcome listener.
- Confirmed stale callbacks still cannot finish successor gestures and timeout remains fail-closed.
- Found and fixed one actionable race: accessibility service loss between connection and bounds checks could retain stale state or crash service calls. Added a focused RED-to-GREEN regression test and null-safe service adapter calls.
- Investigated a possible in-flight focus loss. A connected regression test showed Android moves focus to the enabled cancel action; no production workaround was needed.
- Confirmed deterministic action focus order, in-flight disabling, cancel availability, settings guidance, and key-repeat behavior through focused tests.
- Confirmed the manifest and app dependency configuration have no diff.
- Final verdict: ship Task 5 for emulator/local verification. No open Critical, Important, or Minor findings.

This is an author self-review because subagents were explicitly prohibited; it is weaker than an independent review.

## Changed Files

- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateActivity.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/setup/GateViewModel.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/TouchAccessibilityService.kt`
- `android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureCoordinator.kt`
- `android/app/src/main/res/layout/activity_gate.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/kotlin/dev/opentvtouchpad/setup/GateViewModelTest.kt`
- `android/app/src/test/kotlin/dev/opentvtouchpad/input/GestureCoordinatorTest.kt`
- `android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/GateActivityDpadTest.kt`

## Concerns and Limits

- Verification used the configured API 31 Android TV emulator. No certified projector claim is made.
- Physical D-pad feel, real accessibility gesture delivery, callback timing, and cancellation behavior still require the separately planned hardware validation; that later task was not read or implemented.
- `.kotlin/` build scratch was removed before the implementation commit. `local.properties` remains ignored and uncommitted.
