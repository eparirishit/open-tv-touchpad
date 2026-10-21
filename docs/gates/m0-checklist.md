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
| Git commit | `6223c19e4900c8086ba549e9fa326fd41d156c11` |
| APK SHA-256 | `f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a` |
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
| APK digest recorded before installation | `COMPLETE` | SHA-256 is `f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a`. |
| APK installed with `adb install -r` on XK03H | `COMPLETE` | Coordinator verified replacement installation returned `Success`. |
| No ADB gesture/input command used through installation and evidence preparation | `COMPLETE` | Coordinator verified no `adb shell input` or equivalent gesture/input command was used through replacement installation. |
| Accessibility service enabled | `COMPLETE` | User enabled the service manually and confirmed the service-owned D-pad controller appeared over Netflix. |
| Accessibility shortcut assigned | `NOT-COMPLETED` | Gate execution stopped at the required tap failure before cancellation checks. |
| Post-run no-ADB gesture/input attestation | `COMPLETE` | Coordinator confirms no `adb shell input` or equivalent gesture/input injection was used. Through the M0 decision, ADB was limited to installation, read-only inspection, and non-input Netflix activity launch. After the recorded failure, a user-requested non-input `am force-stop dev.opentvtouchpad` and `pidof` check removed the blocking debug overlay; that cleanup is not cancellation-path gate evidence. |

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
| Controller D-pad focus with touch pass-through in Netflix | Result | `FAIL` |
| Controller D-pad focus with touch pass-through in Netflix | Notes | D-pad focus and cursor movement worked, but the required accessibility tap did not pass through to the Netflix profile tile. The controller retained D-pad focus on the Android TV home screen, so the coordinator used non-input `adb shell am start` only to launch `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`; no remote, tap, swipe, or gesture input was injected by ADB. |
| Overlay in Netflix browsing | Result | `NOT-RUN` |
| Overlay in Netflix browsing | Notes | Cursor visibility and movement were confirmed over Netflix `ProfileSelectionActivity`, but browsing UI was not reached because the required tap failed. |
| Tap | Result | `FAIL` |
| Tap | Notes | With Netflix `ProfileSelectionActivity` confirmed foreground and the bound service reporting gesture capability, the user centered the visible cursor over a profile tile and manually selected **Tap** twice. Neither attempt activated the tile. No ADB input was injected. |
| Vertical scroll | Result | `NOT-RUN` |
| Vertical scroll | Notes | Gate stopped after the required tap failed. |
| Horizontal scroll | Result | `NOT-RUN` |
| Horizontal scroll | Notes | Gate stopped after the required tap failed. |
| Drag | Result | `NOT-RUN` |
| Drag | Notes | Gate stopped after the required tap failed. |
| Overlay over Netflix playback controls | Result | `NOT-RUN` |
| Overlay over Netflix playback controls | Notes | Gate stopped before playback after the required tap failed. |
| 1080p playback usability | Result | `NOT-RUN` |
| 1080p playback usability | Notes | Gate stopped before playback after the required tap failed. |
| Explicit cancel | Result | `NOT-RUN` |
| Explicit cancel | Notes | Gate stopped after the required tap failed. |
| Accessibility service disable mid-drag | Result | `NOT-RUN` |
| Accessibility service disable mid-drag | Notes | Gate stopped after the required tap failed. |
| Final overlay removal after disabling the service | Result | `NOT-RUN` |
| Final overlay removal after disabling the service | Notes | Gate stopped after the required tap failed. |
| Forced process stop after drag start | Result | `NOT-RUN` |
| Forced process stop after drag start | Notes | Gate stopped after the required tap failed. |

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
| Count (`0` or `1`) | `1` |
| What failed | `android.settings.ACCESSIBILITY_SETTINGS` resolved to `com.google.android.tv.frameworkpackagestubs/.Stubs$SettingsStub` and showed no compatible app. |
| What changed | Debug-gate navigation now falls back to general TV Settings, with JVM regression and release-isolation coverage. |
| Observed result | `PASS` — replacement installation succeeded; the fallback opened a usable TV Settings path, and the user enabled the service and reached the controller over Netflix. |

## Decision and Stop Record

| Field | Recorded value |
| --- | --- |
| Decision (`PASS` or `FAIL`) | `FAIL` |
| Stop reason (`NONE` for complete PASS) | Required accessibility-dispatched tap did not activate a centered Netflix profile tile after two manual attempts; the single allowed compatibility investigation had already been used for the XGIMI settings-intent stub. |

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
