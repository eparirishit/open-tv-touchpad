# M0 Hardware Gate Checklist

Use this checklist only on the certified XGIMI Horizon XK03H. Installation
may use ADB, but no gate gesture may be injected with ADB or an equivalent
command. Success in another application or on another device does not satisfy
M0. Keep every manual result `PENDING` until it is observed on the projector.

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

## Prerequisite Record

Only coordinator-verified facts are marked `COMPLETE`. Accessibility service
activation remains a manual prerequisite and must not be inferred from the
successful installation.

| Prerequisite | Status | Evidence or remaining action |
| --- | --- | --- |
| Clean unit, lint, and debug APK build | `COMPLETE` | Coordinator verified the clean unit-test, lint, and debug-build run passed. |
| APK digest recorded before installation | `COMPLETE` | SHA-256 is `7c1b966b8757760c036423168e8ac478345bd36a3a702767a22f3a7b4c99ec61`. |
| APK installed with `adb install -r` on XK03H | `COMPLETE` | Coordinator verified installation returned `Success`. |
| No ADB input command used | `COMPLETE` | Coordinator verified no `adb shell input` or equivalent gesture command was used. |
| Accessibility service enabled and shortcut assigned | `PENDING` | Open the launcher with the projector remote, select **Open accessibility settings and assign shortcut**, then enable the gesture-only service and assign the system accessibility shortcut manually. |

## Controller Setup

1. Complete the pending accessibility prerequisite with the projector remote.
2. Launch Netflix and confirm the service-owned D-pad controller keeps focus
   over Netflix while touch gestures pass through to Netflix beneath it.
3. Perform every observation below without ADB or an equivalent command
   injecting a gate gesture.
4. After the run, use the system accessibility shortcut to disable the
   service and confirm the controller and cursor overlays are removed.

## Manual Execution Record

For each check, replace `PENDING` with `PASS` or `FAIL` only after observing
the behavior. Keep the notes row factual and specific enough for another
reviewer to understand what was exercised and what happened.

| Check | Record | Value |
| --- | --- | --- |
| Overlay in Netflix browsing | Result | `PENDING` |
| Overlay in Netflix browsing | Notes | `PENDING` — record where the cursor was visible and any obstruction or disappearance. |
| Overlay over Netflix playback controls | Result | `PENDING` |
| Overlay over Netflix playback controls | Notes | `PENDING` — record playback-control state and whether the cursor remained visible. |
| Tap | Result | `PENDING` |
| Tap | Notes | `PENDING` — record the intended target and the target actually activated. |
| Vertical scroll | Result | `PENDING` |
| Vertical scroll | Notes | `PENDING` — record the Netflix surface, direction, and observed movement. |
| Horizontal scroll | Result | `PENDING` |
| Horizontal scroll | Notes | `PENDING` — record the Netflix rail, direction, and observed movement. |
| Drag | Result | `PENDING` |
| Drag | Notes | `PENDING` — record the drag path, completion behavior, and whether any press remained stuck. |
| Explicit cancel | Result | `PENDING` |
| Explicit cancel | Notes | `PENDING` — start a drag, explicitly cancel it, and record whether any press remained stuck. |
| Accessibility service disable mid-drag | Result | `PENDING` |
| Accessibility service disable mid-drag | Notes | `PENDING` — start a drag, disable the service with the system shortcut, and record release and overlay removal. |
| Forced process stop after drag start | Result | `PENDING` |
| Forced process stop after drag start | Notes | `PENDING` — start a drag, use Android TV settings with the projector remote to force-stop the process, and record whether any press remained stuck. |
| 1080p playback usability | Result | `PENDING` |
| 1080p playback usability | Notes | `PENDING` — record the 1080p title/state and whether playback and controls remained usable with the overlay active. |

## Aggregate Report Fields

- Set report `Overlay` to `PASS` only when both overlay checks are `PASS`.
  If either overlay check is `FAIL`, set report `Overlay` to `FAIL`.
- Set report `Cancel` to `PASS` only when explicit cancel, accessibility
  service disable mid-drag, and forced process stop after drag start are all
  `PASS`. If any cancellation check is `FAIL`, set report `Cancel` to `FAIL`.
- Copy each other manual result directly to its matching report field. Leave
  an aggregate field unresolved until all of its source checks are resolved.

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
- Set report `Decision` to `PASS` only when every required report observation
  is `PASS`, both aggregate rules are satisfied, and the investigation count
  is `0` or `1` with matching details.
- On any hardware failure, set report `Decision` to `FAIL`, name the failed
  check as the stop reason above, preserve the actual result and notes, commit
  the evidence, run the verifier expecting rejection, and stop before M1.
- Validate the report with:

```sh
sh scripts/verify-gate-report.sh docs/gates/m0-report.md M0
```
