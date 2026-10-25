# N1 — Direct Nord touchpad

Status: **N1 DIAGNOSTIC BEHAVIOR ACCEPTANCE PASS (2026-10-25)** for the recorded
Nord/XGIMI artifact. Automated checks, independent review and the planned manual
gesture/lifecycle sequence passed. Live results are owner observations; this
does not certify daily-use readiness or a full accessibility/device matrix.
This report does not change H1A/H1B or the historical M0/M0.1/H0 outcomes.

## Artifact and local evidence (2026-10-25)

- Source: `fdb4905ccd55087b9562b43082888cdb518a3237`.
- Package: `dev.opentvtouchpad.hidprobe`, versionCode5, versionName0.5-diagnostic.
- Debug APK SHA256: `e159521439bbd801b1bfebeb4b9b9646838b0a9ea0f40d153fc75937578de666`.
- Full probe Gradle unit suite: **76 tests, 0 failures/errors/skips**. Includes
  15 gesture tests,7 shared-submission tests and1 source-linked Stop regression,
  alongside the previous53 session/enrollment/native-owner/trace tests.
- Gesture tests were observed RED against an empty helper, then GREEN. Shared
  submission tests first failed compilation for the missing implementation, then
  passed. Native Stop regression was observed failing its cleanup assertion on
  the original method, then passing after the catch correction.
- Lint: **0 errors,22 warnings**. Existing18 hardcoded/localization warnings and
  2 SDK/dependency warnings remain. New ViewConstructor warning reflects a
  programmatic-only View with supplied callbacks; ClickableViewAccessibility
  cannot follow the helper callback's `performClick`. Native arrow/Click buttons
  remain the accessible alternatives. These warnings are not suppressed; actual
  accessibility/layout checks remain pending.
- Debug assembly passes. Packaged permissions remain exactly Bluetooth CONNECT,
  ADVERTISE, FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE; no
  INTERNET permission. Backup/transfer exclusions, nonexported service and
  cleartext prohibition remain. The ordinary build excludes this opt-in module;
  its release variant remains disabled.
- HID descriptor compared unchanged with the0.4 source. Same application ID,
  saved-enrollment format and five-minute foreground-only session contract.

## Independent review

Initial review found one actionable error: IllegalArgumentException on Stop's
neutral report could escape the new touch/scheduled callback and skip remaining
cleanup. The native Stop/finishProfile catch boundaries now handle it alongside
the prior native exceptions. The regression executes the real extracted Run.stop
source with fake native collaborators, testing scheduled-movement failure and
tap-release failure followed by a failing neutral submission. Both must fence
input, cancel gestures and attempt remaining disconnect/unregister/service cleanup.
This is not Android framework execution or physical receiver-cleanup evidence.
Scoped re-review of the committed correction and regression passed for `fdb4905`:
spec implementation and code/security quality PASS, no actionable findings left.
The original finding remains recorded above; this is not hardware acceptance.

## Device evidence

Preflight found one existing authorized ADB connection matching OnePlus AC2001,
Android12/API31. The probe service was stopped. No identifiers were recorded and
ADB was not restarted. In-place `install -r` succeeded and installed versionCode5
was verified. The existing enrollment record was readable before and after;
bytes matched in memory without logging or persisting their contents. Launch
returned success and the probe process is running, but foreground/window focus
was not confirmed: the Nord display was asleep. No screenshot or gesture was
captured/injected. The owner was asked to unlock/open and test manually.

The owner subsequently reported **“Movement and tap both work”** in response to
the explicit saved-projector connection, four-direction movement/lift-reposition
and safe Netflix profile/title tap sequence. Record this as successful functional
movement and target activation by owner observation on0.5; it is not an automated
screen capture, a separate accessibility/layout audit or a lifecycle/reliability
claim. Accidental-click prevention and Stop checks were requested next.

The owner then reported **“All three checks pass”** for swipe/return-to-origin
without activation, second-finger cancellation followed by a working fresh
gesture after all-up, and Stop disabling pad input while the physical remote
still works. These are owner-observed results for0.5, not long-press, background,
timeout or crash checks. Long press, foreground loss/return and expiry were
requested as the next bounded sequence.

The owner's final **“All three checks pass”** confirmed a one-second stationary
hold/lift does not activate a Netflix title, leaving for the Nord home screen and
returning keeps input disabled, and a fresh session's five-minute expiry disables
pad input while preserving physical-remote operation. This completes the planned
N1 manual behavior sequence on the unchanged0.5 artifact. No timing measurement,
screen-reader/font-scale audit, repeated reconnect reliability or crash cleanup
is inferred from these responses.

| Required observation | Result |
| --- | --- |
| In-place update retains enrollment; version5 installed | PASS: verified install/version and unchanged enrollment bytes |
| Visible touchpad layout and accessible controls | Owner used touchpad; separate layout/accessibility checks pending |
| Explicit saved-projector connection enables touchpad | PASS by owner response to the requested sequence |
| Four-direction movement and lift/reposition without jumps | PASS by owner response: movement works |
| Stationary Netflix profile/title tap activates target | PASS by owner response: tap works |
| Swipe/return-to-origin does not click | PASS by owner response |
| Long press does not click | PASS by owner's final response |
| Multitouch cancels; fresh gesture after all-up works | PASS by owner response |
| Stop cancels input; physical remote still works | PASS by owner response |
| Foreground loss/return cannot resume stale input | PASS by owner's final response |
| Five-minute expiry disables touchpad without automatic resume | PASS by owner's final response; physical remote works |

Use the manual sequence in `experiments/hid_probe/README.md`. ADB never supplies
acceptance gestures. Keep the previous unexplained first-attempt0.3 reconnect
failure deferred, not fixed or attributed to user error. Native crash/link-loss
click release, exact firmware encryption, scrolling and TV-key integration are
not established by N1's local results.
