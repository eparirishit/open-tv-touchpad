# M0 Hardware Gate Checklist

Use this checklist only on the certified XGIMI Horizon XK03H. Installation
may use ADB, but no gate gesture may be injected with ADB or an equivalent
command. Success in another application or on another device does not satisfy
M0.

## Controller Setup

- Open the Open TV Touchpad launcher activity and select **Open accessibility
  settings and assign shortcut**.
- Enable the gesture-only accessibility service and assign the system
  accessibility shortcut. Do not use ADB, package commands, or an overlay-app
  permission to activate control.
- Launch Netflix. Confirm the service-owned D-pad controller keeps focus over
  Netflix while touch gestures pass through to Netflix beneath it.
- Use the system accessibility shortcut to disable the service after the run;
  confirm the controller and cursor overlays are removed.

## Evidence Identity

- Record the full 40-character Git commit used to build the APK.
- Build a clean APK and record its 64-character SHA-256 digest before install.
- Confirm the projector reports model `XGIMI Horizon XK03H`, then record the
  Android TV version, firmware version, and Netflix version shown by the device.
- Confirm the target application is `com.netflix.mediaclient` and record that
  exact package in `Tested-Application`.

## Manual Observations

Record each result as `PASS` or `FAIL` in `m0-report.md`.

- `Overlay`: the accessibility cursor remains visible over Netflix browsing UI
  and playback controls.
- `Tap`: an accessibility-dispatched tap activates the intended Netflix target.
- `Vertical-Scroll`: a vertical accessibility gesture scrolls Netflix content.
- `Horizontal-Scroll`: a horizontal accessibility gesture scrolls Netflix
  content.
- `Drag`: an accessibility-dispatched drag completes without a stuck press.
- `Cancel`: explicit cancellation, service disable mid-drag, and forced process
  stop after drag start leave no stuck press.
- `Playback-1080p`: 1080p playback remains usable while the overlay is active.

## Decision

- Allow at most one compatibility investigation. Record `NONE` when no
  investigation occurred; otherwise document what failed, what changed, and
  the observed result in `Investigation-Details`.
- Set `Decision` to `PASS` only when every required observation is `PASS` and
  `Investigation-Count` is `0` or `1`.
- Leave the report invalid and stop on any failed observation; do not claim M0
  PASS or begin M1.
- Validate a completed PASS report with:

```sh
sh scripts/verify-gate-report.sh docs/gates/m0-report.md M0
```
