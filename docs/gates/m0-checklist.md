# M0 Hardware Gate Checklist

Use this checklist only on the certified XGIMI Horizon XK03H. Installation may
use ADB. The process-stop check may use a non-input process-stop command when
its method is recorded. No gate gesture or input may be injected with
`adb shell input` or an equivalent command. Success in another application or
on another device does not satisfy M0. Keep every manual result `PENDING` until
it is observed on the projector.

## Evidence Identity

| Field | Recorded value |
| --- | --- |
| Git commit | `22c5587ca98c3c9805bda05401e19622b3f32ce8` |
| APK SHA-256 | `7c1b966b8757760c036423168e8ac478345bd36a3a702767a22f3a7b4c99ec61` |
| Projector | `XGIMI Horizon XK03H` |
| Android TV | `11` |
| Firmware | `RT.3.24.327 release-keys` |
| Netflix | `8.112.1 build 6 50665` |
| Tested application | `com.netflix.mediaclient` |

## Prerequisite and Attestation Record

Only coordinator-verified preparation facts are marked `COMPLETE`.
Accessibility service activation remains a manual prerequisite and must not be
inferred from the successful installation. The final no-input attestation must
remain `PENDING` until every manual check is finished.

| Prerequisite | Status | Evidence or remaining action |
| --- | --- | --- |
| Clean unit, lint, and debug APK build | `COMPLETE` | Coordinator verified the clean unit-test, lint, and debug-build run passed. |
| APK digest recorded before installation | `COMPLETE` | SHA-256 is `7c1b966b8757760c036423168e8ac478345bd36a3a702767a22f3a7b4c99ec61`. |
| APK installed with `adb install -r` on XK03H | `COMPLETE` | Coordinator verified installation returned `Success`. |
| No ADB gesture/input command used through installation and evidence preparation | `COMPLETE` | Coordinator verified no `adb shell input` or equivalent gesture/input command was used through installation and preparation. |
| Accessibility service enabled and shortcut assigned | `PENDING` | Open the launcher with the projector remote, select **Open accessibility settings and assign shortcut**, then enable the gesture-only service and assign the system accessibility shortcut manually. |
| Post-run no-ADB gesture/input attestation | `PENDING` | After every manual check, attest that no `adb shell input` or equivalent gesture/input injection was used during the hardware run. A recorded non-input process-stop command does not fail this attestation. |

## Controller Setup

1. Complete the pending accessibility prerequisite with the projector remote,
   then launch Netflix.
2. Record the controller D-pad focus with touch pass-through check, followed by
   the normal browsing and playback checks through 1080p playback usability.
3. Run explicit cancel: with the projector remote, select **Drag path**, then
   immediately press/select **Cancel active gesture** while that control is
   focused and enabled.
4. Run accessibility service disable mid-drag. Record both the cancellation
   result and final controller/cursor overlay removal, then use only the
   projector remote to re-enable the service and relaunch the app and Netflix.
5. Run forced process stop after drag start last. Record the process-stop
   method. A non-input ADB process-stop command is allowed, but ADB gesture or
   input injection is not. Relaunch and re-enable with the projector remote
   only if later evidence is needed.
6. Complete the post-run no-ADB gesture/input attestation after all manual
   evidence is recorded.

## Manual Execution Record

Perform every gesture row below while Netflix is in the foreground. Do not use
`adb shell input` or any equivalent gesture/input injection. For each check,
replace `PENDING` with `PASS` or `FAIL` only after observing the behavior. Keep
the notes row factual and specific enough for another reviewer to understand
what was exercised and what happened.

| Check | Record | Value |
| --- | --- | --- |
| Controller D-pad focus with touch pass-through in Netflix | Result | `PENDING` |
| Controller D-pad focus with touch pass-through in Netflix | Notes | `PENDING` — record D-pad focus behavior and whether touch gestures continued to reach Netflix beneath the controller. |
| Overlay in Netflix browsing | Result | `PENDING` |
| Overlay in Netflix browsing | Notes | `PENDING` — record where the cursor was visible and any obstruction or disappearance. |
| Tap | Result | `PENDING` |
| Tap | Notes | `PENDING` — record the intended target and the target actually activated. |
| Vertical scroll | Result | `PENDING` |
| Vertical scroll | Notes | `PENDING` — record the Netflix surface, direction, and observed movement. |
| Horizontal scroll | Result | `PENDING` |
| Horizontal scroll | Notes | `PENDING` — record the Netflix rail, direction, and observed movement. |
| Drag | Result | `PENDING` |
| Drag | Notes | `PENDING` — record the drag path, completion behavior, and whether any press remained stuck. |
| Overlay over Netflix playback controls | Result | `PENDING` |
| Overlay over Netflix playback controls | Notes | `PENDING` — record playback-control state and whether the cursor remained visible. |
| 1080p playback usability | Result | `PENDING` |
| 1080p playback usability | Notes | `PENDING` — record the 1080p title/state and whether playback and controls remained usable with the overlay active. |
| Explicit cancel | Result | `PENDING` |
| Explicit cancel | Notes | `PENDING` — with the projector remote, select **Drag path**, then immediately press/select **Cancel active gesture** while it is focused and enabled; record whether any press remained stuck. |
| Accessibility service disable mid-drag | Result | `PENDING` |
| Accessibility service disable mid-drag | Notes | `PENDING` — start a drag, disable the service with the system shortcut, and record whether the active press was released. |
| Final overlay removal after disabling the service | Result | `PENDING` |
| Final overlay removal after disabling the service | Notes | `PENDING` — record whether both controller and cursor overlays were removed, then record remote-only service re-enable and app/Netflix relaunch recovery. |
| Forced process stop after drag start | Result | `PENDING` |
| Forced process stop after drag start | Notes | `PENDING` — start a drag, force-stop the process, record the process-stop method and whether any press remained stuck; recover by remote-only relaunch/re-enable only if later evidence is needed. |

## Aggregate Report Fields

- Set report `Overlay` to `PASS` only when both overlay checks are `PASS`.
  If either overlay check is `FAIL`, set report `Overlay` to `FAIL`.
- Set report `Cancel` to `PASS` only when explicit cancel, accessibility
  service disable mid-drag, and forced process stop after drag start are all
  `PASS`. If any cancellation check is `FAIL`, set report `Cancel` to `FAIL`.
- Copy tap, vertical scroll, horizontal scroll, drag, and 1080p playback
  results directly to their matching report fields. The controller D-pad and
  final overlay-removal checks remain separately required checklist evidence.
  Leave an aggregate field unresolved until all of its source checks are
  resolved.

## Compatibility Investigation Record

At most one compatibility investigation is allowed. If none occurs, set the
count to `0`, each detail below to `NONE`, and report
`Investigation-Details` to `NONE`. If one occurs, set the count to `1` and
complete every detail; the report details must summarize the same evidence.

| Investigation field | Recorded value |
| --- | --- |
| Count (`0` or `1`) | `PENDING` |
| What failed | `PENDING` |
| What changed | `PENDING` |
| Observed result | `PENDING` |

## Decision and Stop Record

| Field | Recorded value |
| --- | --- |
| Decision (`PASS` or `FAIL`) | `PENDING` |
| Stop reason (`NONE` for complete PASS) | `PENDING` |

- Verifier exit zero is reserved for a complete `PASS` report.
- Set report `Decision` to `PASS` only when every manual execution result and
  required report observation is `PASS`, both aggregate rules are satisfied,
  the post-run no-ADB gesture/input attestation is complete, and the
  investigation count is `0` or `1` with matching details.
- On any hardware failure, set report `Decision` to `FAIL`, name the failed
  check as the stop reason above, preserve the actual result and notes, commit
  the evidence, run the verifier expecting rejection, and stop before M1.
- Validate the report with:

```sh
sh scripts/verify-gate-report.sh docs/gates/m0-report.md M0
```
