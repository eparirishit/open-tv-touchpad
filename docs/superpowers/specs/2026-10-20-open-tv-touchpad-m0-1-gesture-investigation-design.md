# Open TV Touchpad M0.1 Gesture Investigation Design

**Status:** Approved for implementation planning
**Date:** 2026-10-20  
**Product:** Open TV Touchpad  
**Parent specification:** `2026-10-18-open-tv-touchpad-design.md`

## 1. Purpose

M0.1 is one bounded, diagnostic-only investigation of the failed Netflix tap
path on the certified XGIMI Horizon XK03H. It determines whether the failure is
caused by tap timing, the focusable M0 controller overlay, coordinate delivery,
or Netflix rejecting an otherwise completed accessibility gesture.

M0.1 does not reopen M1, change the product input backend, or replace the
original M0 gate. Its only successful outcome is permission to rerun the full
M0 checklist with one exact APK.

## 2. Existing Evidence

The immutable M0 result remains `FAIL`:

- Source commit: `6223c19e4900c8086ba549e9fa326fd41d156c11`.
- APK SHA-256:
  `f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a`.
- Projector: XGIMI Horizon XK03H, Android TV 11,
  `RT.3.24.327 release-keys`.
- Netflix: `8.112.1 build 6 50665`, package
  `com.netflix.mediaclient`.
- The accessibility service bound, the controller and cursor appeared, and
  cursor movement worked.
- Two user-triggered taps over a centered Netflix profile tile did not activate
  the tile.
- No ADB gesture or input injection was used.

The first compatibility investigation fixed setup navigation after the XGIMI
resolved Android's accessibility-settings action to a framework stub. It did
not investigate gesture dispatch behavior.

## 3. Contract Amendment

The parent specification permits one compatibility investigation and stops on
a remaining Netflix gesture failure. M0.1 creates a narrowly scoped exception:

1. The recorded M0 `FAIL` is never rewritten or reclassified.
2. One additional investigation is allowed only for the tap delivery path.
3. The investigation has the fixed probe modes and order in this document.
4. Success in the first-party calibration screen is diagnostic only and never
   satisfies M0.
5. M0.1 cannot produce an M0 `PASS`; it can only authorize one complete M0
   rerun on the exact investigated APK.
6. If every fixed probe mode fails in Netflix, implementation stops again. No
   duration tuning, coordinate offset tuning, new target application, or new
   input backend may be added under M0.1.
7. M1 remains blocked until a complete M0 report passes the existing verifier.

## 4. Scope

### 4.1 In Scope

- Capture the synchronous gesture-admission result.
- Capture completed, cancelled, and callback-timeout outcomes.
- Replace the fixed 80 ms tap duration with Android's system tap timeout in a
  probe mode.
- Remove the focusable M0 controller from the window before dispatch in a probe
  mode while keeping the non-focusable cursor visible.
- Add a debug-only first-party calibration target.
- Record manual Netflix activation separately from Android's callback result.
- Preserve debug/release source-set isolation and release-artifact scans.

### 4.2 Out of Scope

- PWA, gateway, certificates, pairing, networking, or M1 work.
- Accessibility node inspection or `ACTION_CLICK`.
- Screen reading, screenshots, OCR, package enumeration, or foreground-app
  inspection in the product.
- ADB, shell, root, instrumentation, or hidden API input injection.
- Automatic retries or adaptive timing.
- Swipe, drag, cancellation, or playback retesting before tap succeeds.
- A Bluetooth or USB HID backend.

## 5. Probe Architecture

### 5.1 Closed Probe Modes

The debug controller exposes exactly three tap modes:

1. `BASELINE_80_MS` — current 80 ms tap with the controller present. This is
   rerun once to capture the missing admission and callback evidence.
2. `SYSTEM_TAP_TIMEOUT` — uses `ViewConfiguration.getTapTimeout()` with the
   controller present.
3. `CONTROLLER_HANDOFF` — uses the system tap timeout and removes the focusable
   controller before dispatch.

Modes are selected through a closed enum. No free-form duration, coordinate,
delay, path, or package value is accepted.

### 5.2 Controller Handoff

`CONTROLLER_HANDOFF` must prevent the remote confirmation key from leaking to
Netflix:

1. The controller consumes both key-down and the matching key-up.
2. Dispatch is armed on key-down but does not begin until key-up is consumed.
3. The controller overlay is removed.
4. Dispatch waits exactly one `Choreographer` frame so the window removal is
   committed before gesture admission.
5. The cursor overlay remains visible at the authoritative cursor position.
6. A single tap is submitted without automatic retry.

The frame callback races a 250 ms monotonic deadline. If the deadline expires,
handoff is cancelled, pending input is cleared, the cursor is hidden, restart
is required, and a late frame callback is ignored through a generation token.
The attempt result is `HANDOFF_FRAME_TIMEOUT`, and M0.1 stops immediately as a
safety failure without running calibration or another probe.

On a completed callback, the controller returns exactly once after a fixed
100 ms post-completion delay only if the same service generation remains bound
and healthy. On synchronous rejection, cancellation, or callback timeout, the
controller remains removed, the cursor is hidden, pending input is cleared,
and the service must be restarted before another probe. Interruption,
disablement, unbind, and shutdown cancel pending frame and restoration
callbacks, clear input, remove both overlays, and ignore all late callbacks.
This fail-safe leaves normal TV navigation available instead of restoring a
blocking overlay into a degraded state.

### 5.3 Calibration Target

A debug-only, full-screen calibration activity provides large fixed targets.
It records only whether the intended target received the tap. It does not read
another application's screen or accessibility tree.

The calibration target runs only after all three Netflix modes fail. Its
purpose is classification:

- Calibration success with Netflix failure indicates an application-specific
  compatibility problem.
- Calibration failure indicates a projector, coordinate, timing, or gesture
  dispatch problem.

Neither result changes the failed M0 decision.

## 6. Diagnostics and Evidence

The TV UI may display these ephemeral fields:

- Probe mode.
- Gesture admission: `ACCEPTED` or `REJECTED`.
- Callback: `COMPLETED`, `CANCELLED`, or `TIMEOUT`.
- Callback duration in milliseconds.
- Current display width and height.
- Controller state: `PRESENT`, `HANDOFF`, or `REMOVED`.
- Manual target result: `ACTIVATED` or `NOT_ACTIVATED`.

Android callback completion means only that Android completed gesture delivery;
it does not claim that Netflix handled the tap. The user's observed target
activation is the end-to-end result.

Exact gesture coordinates, screen contents, profile names, raw key events,
package inventories, and raw protocol or ADB output are not persisted, logged,
committed, or placed in review artifacts.

## 7. Certified Execution Order

Use one clean debug APK tied to a commit and SHA-256 digest:

1. Install with ADB, then use no ADB input command.
2. Enable the accessibility service manually.
3. Open Netflix and center the cursor over a profile tile.
4. Run `BASELINE_80_MS` once and record admission, callback, and manual result.
5. If a mode activates the intended tile, return to the profile screen without
   ADB input and repeat that mode once. Two consecutive activations establish a
   `PROCEED_TO_M0_RETEST` candidate.
   If the confirmation attempt fails, that mode fails, no third attempt is
   allowed, and execution advances to the next untested mode.
6. Otherwise restart the service if required, recenter, and run
   `SYSTEM_TAP_TIMEOUT` once with the same evidence and repeat-on-success rule.
7. Otherwise restart if required, recenter, and run `CONTROLLER_HANDOFF` once
   with the same evidence and repeat-on-success rule.
8. If every Netflix mode fails, run each fixed mode once against the calibration
   target, classify the failure, record `STOP`, and end implementation.
9. Complete `docs/gates/m0-1-report.md` and run
   `sh scripts/verify-m0-1-report.sh docs/gates/m0-1-report.md`.
10. Only verifier exit zero with `Outcome: PROCEED_TO_M0_RETEST` authorizes the
    complete M0 rerun. Observations alone never authorize it.

No later probe or calibration step runs after a safety invariant fails, an
unexpected key reaches Netflix, the controller remains stuck, or evidence
identity becomes uncertain. Evidence finalization and fail-closed report
verification still run.

## 8. Outcomes

### 8.1 `PROCEED_TO_M0_RETEST`

This outcome requires:

- One fixed probe mode is admitted by Android.
- The callback completes without cancellation or timeout.
- The intended Netflix profile tile activates on two consecutive attempts.
- No D-pad confirmation event leaks to Netflix.
- The controller and cursor follow the lifecycle and fail-safe rules.
- No ADB input injection occurs.

The same APK then runs the entire original M0 checklist. Only that complete run
can replace the project blocker with a new M0 `PASS` or `FAIL` report.

### 8.2 `STOP`

M0.1 stops when any of these is true:

- All fixed Netflix probe modes fail to activate the intended tile.
- The remote confirmation key leaks to Netflix.
- Controller removal or restoration violates the lifecycle contract.
- The calibration result cannot classify the failure reliably.

A synchronous rejection, cancellation, or timeout fails that probe mode,
removes the controller, and requires a service restart. It does not end M0.1
until the remaining fixed modes and permitted calibration checks are exhausted,
unless it also violates a safety invariant.

After `STOP`, the next architecture decision is outside this design, such as an
Android Bluetooth HID bridge or a dedicated HID device.

## 9. Evidence Contract and Verifier

M0.1 has a separate fail-closed report schema and verifier. The existing M0
verifier is unchanged and does not accept M0.1 outcomes.

The M0.1 report contains at least:

- `Gate: M0.1`.
- `Outcome: PROCEED_TO_M0_RETEST` or `STOP`.
- Exact source commit, APK SHA-256, projector, Android TV, firmware, Netflix
  version, and tested package.
- Immutable original-M0 commit, APK SHA-256, decision, and investigation count.
- `M0.1-Investigation-Count: 1`.
- Attempt count and closed result for each probe mode.
- Winning mode or `NONE`.
- Calibration results and classification or `NOT_RUN`.
- Key-leakage, controller-lifecycle, no-ADB-input, and evidence-integrity
  attestations.
- Stop reason or `NONE`.

Each attempt result is exactly one of `NOT_RUN`, `REJECTED`, `CANCELLED`,
`TIMEOUT`, `HANDOFF_FRAME_TIMEOUT`, `COMPLETED_NOT_ACTIVATED`, or
`COMPLETED_ACTIVATED`. `TIMEOUT` means Android admitted the gesture but its
completion callback missed the bounded deadline. `HANDOFF_FRAME_TIMEOUT` means
the pre-dispatch frame deadline expired and is always an immediate safety
`STOP`.

`scripts/verify-m0-1-report.sh` returns zero only when all identity and safety
fields are complete and one of these closed outcome rules is satisfied:

- `PROCEED_TO_M0_RETEST`: the winning mode has exactly two attempts and both
  are `COMPLETED_ACTIVATED`; the attempts use the same artifact identity; prior
  modes follow the required order; later modes and calibration are `NOT_RUN`;
  no key leakage occurred; and all safety attestations are `PASS`.
- `STOP` after compatibility exhaustion: every Netflix mode has its required
  failed attempt, a second attempt exists only after an initial activation and
  is the single failed confirmation, no mode has a third attempt, all required
  calibration attempts and classification are complete, and all safety
  attestations are `PASS`.
- `STOP` on safety failure: the stop reason identifies the failed invariant,
  all later modes and calibration are `NOT_RUN`, no further attempt follows the
  failure, and the no-ADB-input and evidence-integrity attestations remain
  complete. `HANDOFF_FRAME_TIMEOUT` is validated only through this branch.

The original M0 report permanently retains `Investigation-Count: 1`. M0.1 is
recorded only in its separate report and is never added to that count. If M0.1
authorizes a complete M0 rerun, the new M0 report counts only investigations
performed during that rerun; its investigation count is fixed at `0`, and no
additional compatibility change is allowed during the rerun.

## 10. Verification Requirements

### 10.1 Host Tests

- Closed probe-mode parsing and ordering.
- System tap-timeout selection without a hardcoded replacement value.
- Key-down arms and matching key-up starts controller handoff exactly once.
- Repeat, mismatched, duplicate, and missing key-up events cannot dispatch.
- Rejection, cancellation, timeout, interruption, and shutdown clear pending
  input and leave the controller removed.
- Completion restores the controller once within the bounded delay.
- Missing-frame timeout clears handoff, requires restart, and fences a late
  frame callback.
- Interruption, disablement, unbind, and shutdown fence every late frame,
  gesture, and restoration callback and remove both overlays.
- No automatic retry occurs.
- The M0.1 verifier rejects missing identity, out-of-order attempts, a third
  attempt, mixed winning modes, incomplete safety evidence, and cumulative
  investigation counts.

### 10.2 Emulator Tests

- Controller overlay is removed before the handoff gesture is submitted.
- Cursor overlay remains non-focusable and non-touchable during handoff.
- One rendered frame separates controller removal and gesture admission.
- Calibration target reports only its fixed target result.
- Service restart is required after every degraded outcome.
- Accessibility remains manually enabled and `canRetrieveWindowContent=false`.

### 10.3 Release Isolation

Release assembly and the existing isolation verifier must reject every M0.1
activity, mode, string, layout, class, diagnostic, and calibration resource.
No M0.1 code or identity may be reachable from a release artifact.

### 10.4 Hardware Evidence

The M0.1 report records exact source commit, APK SHA-256, projector firmware,
Android TV version, Netflix version, probe outcome, separately scoped original
M0 and M0.1 investigation counts, and no-ADB-input attestation. A PASS-only M0
verifier is not weakened to accept M0.1.

## 11. Deliverables

- Debug-only M0.1 probe implementation and tests.
- `docs/gates/m0-1-report.md` with outcome
  `PROCEED_TO_M0_RETEST` or `STOP`.
- `docs/gates/m0-1-checklist.md` with the ordered observations.
- `scripts/verify-m0-1-report.sh` with passing and fail-closed fixtures.
- Extended release-isolation verification.
- Updated progress, capabilities, architecture, and backlog records.
- If authorized by M0.1, a separate full M0 rerun report tied to the same APK.

## 12. Security Invariants

- ADB remains installation and read-only diagnostics only.
- No screen or accessibility-node content is collected.
- No arbitrary gesture, coordinate, duration, package, intent, or shell input
  enters the runtime.
- Every timer and transition is bounded and lifecycle-cancellable.
- Every frame, gesture, and restoration callback is generation-fenced.
- Gesture failure never restores a blocking controller automatically.
- M0.1 cannot start M1, ship product behavior, or weaken the existing M0
  verifier.
