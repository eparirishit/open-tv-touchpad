# Nord N1 touchpad implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add finger movement and tap-to-click to the isolated Nord HID probe.

**Architecture:** A platform-independent gesture state machine supplies bounded
motion to a native Android touch surface. ProbeActivity owns native submission
and the existing connection gate; all input callers share exception-to-Stop handling.

**Tech Stack:** Existing Java17, Android API31+, Gradle and JUnit; no dependencies.

**Spec:** `docs/superpowers/specs/2026-10-25-nord-direct-remote-design.md` (N1 only).

## Global constraints

- Keep the three-byte HID descriptor, application ID, enrollment, permissions,
  five-minute foreground-only run and explicit Start/Connect unchanged.
- One finger, gain1.0 count/dp, platform touch slop, tap at most250ms.
- One bounded motion accumulator (254 counts/axis), reports [-127,127], at most30Hz.
- No held drag, scrolling, network, background input, replay or automatic reconnect.
- Stop/disable/pause/detach cancels queued movement and tap recognition.
- Preserve accepted OS-managed bond assumptions; all existing gates remain pending.

## Review focus

- Permission/radio failure inside a scheduled report must Stop, not crash (task2).
- A queued callback must not send into a later gesture or connection run (task1).
- Move ending near its origin must never become a click (task1).
- Parent scrolling/focus loss must not leave an active touch recognizer (tasks1–2).
- Disconnect/re-enable while a finger is held must require a new down (tasks1–2).

## Task 1: Gesture engine and native touch surface

**Files:** create `TouchpadGesture.java`, `TouchpadView.java` in
`experiments/hid_probe/src/main/java/dev/opentvtouchpad/hidprobe/`; add
`TouchpadGestureTest.java` under the corresponding test package.

**Interface:** `TouchpadView(Context, BiConsumer<Integer,Integer> move, Runnable tap)`;
`void cancelGesture()`; standard `setEnabled(boolean)` cancels on disable. All
callbacks run on the main thread. Native callback exceptions are handled by the
Activity-owned methods passed in task2. Internal pure helper API is builder-owned.

- [x] Write failing JUnit tests covering slop, short/long taps, movement returning
  to origin, fractional/signed bounds, burst clamping and30Hz coalescing, up/cancel
  discard, multitouch through all-up, disabled input and stale callback epochs.
- [x] Observe missing behavior fail, then implement the pure helper. Use monotonic
  millisecond timestamps, density-independent input and exactly one pending frame.
  Retain the dispatch-rate timestamp across gestures, so rapid down/up cannot
  bypass the rate limit. No input history or unbounded runnable queue.
- [x] Implement the View adapter, including pointer IDs, all-up after multitouch,
  ACTION_UP/CANCEL, enable/detach/focus cancellation and parent interception. Keep
  label/drawing simple, disabled state visible, and callback generation fencing.
- [x] Run focused JUnit via standalone javac/JUnit if needed to avoid concurrent
  Gradle writes; integration verification uses the actual Gradle module in task2.
- [x] Report changed files, test commands and any remaining native-only checks.

## Task 2: Shared submission safety and integrated probe

**Files:** create `InputSubmission.java` and `InputSubmissionTest.java`; modify
`ProbeActivity.java`, `experiments/hid_probe/build.gradle.kts`, README and progress.

**Interface:** package-private `InputSubmission.guard(Runnable, Runnable failure)`
catches SecurityException/IllegalStateException/IllegalArgumentException and runs
failure once; `boolean click(BooleanSupplier press, BooleanSupplier release)`
always attempts release in finally and returns both submission results.

- [x] Add failing tests proving exception-to-Stop behavior and release after false
  or throwing press; release failure must also Stop. Exercise direct and scheduled
  call patterns through the same real guard, without mocking native Bluetooth.
- [x] Implement the small submission helper and route existing move/click through
  it. All trust/permission/session/native-run checks stay inside guarded actions.
- [x] Integrate task1 View. Keep Stop/Close fixed outside the setup ScrollView;
  give the pad visible bounded space, preserve accessible arrows/Click and48dp
  button targets. Cancel gestures before every Stop and on focus/lifecycle loss.
- [x] Bump to versionCode5/versionName0.5-diagnostic, update README and acceptance
  instructions; do not change the HID descriptor or stored enrollment format.
- [x] Run `:experiments:hid_probe:testDebugUnitTest`, `lintDebug`, `assembleDebug`
  with `-PhidProbe=true --offline --console=plain`; record actual failures/warnings.
- [x] Inspect packaged permissions/backup rules and ordinary-build exclusion.
- [x] Independently review the complete N1 diff and connected callers, fix
  in-scope findings with regression tests, re-review fixes and commit small changes.

## Task 3: Installation and actual-device acceptance

- [x] Record source/APK SHA256. Discover only existing authorized ADB connections;
  select a unique verified Nord AC2001/API31 without exposing identifiers.
- [x] If reachable, install reviewed APK in place and verify version; preserve
  enrollment. Never restart ADB, inject gate input or silently repair the bond.
- [x] Ask the owner to connect and test four-direction movement, lift/reposition,
  Netflix tap and no swipe-click. Then check multitouch, Stop, foreground loss
  and expiry; record only observed results. No live availability means pending.
- [x] Update capabilities/progress/backlog with implemented versus observed status.

## Execution notes

Owner explicitly requested subagent development and now implementation. No second
plan-approval pause: execute the approved N1 design. Reuse the existing isolated
`hub-remote-proof` worktree; unrelated hub/browser code is untouched. Task1 agent
and task2 main integration have disjoint write sets. Main owns Gradle execution;
agent uses isolated javac outputs. Independent review covers the integrated diff.

Implementation source: `fdb4905`; all76 probe tests pass, lint0errors/22warnings,
debug build and independent correction review pass. Version5 installed with
enrollment bytes unchanged. After the initial asleep-screen launch, the owner
completed all requested gesture/lifecycle sequences successfully. N1 diagnostic
behavior acceptance passes for this artifact; broader checks retain their limits.
See `docs/gates/n1-report.md` for artifact hashes and exact evidence.
