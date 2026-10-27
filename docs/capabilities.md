# Capabilities

## Diagnostic0.19 — prototype details and touchpad speed

Mouse and TV show independent colored dots beside truthful text, with a smaller
styled diagnostic message. Bluetooth setup has numbered instructions. Disabled
OK is muted blue. Touchpad offers a0.5×–2× sensitivity slider (default1×); changing
it cancels the current gesture. Scroll buttons remain needed for Netflix; the
secondary Buttons instead mode retains accessibility support. TV pairing uses six
visible OTP-style cells with native editing/paste/backspace and six-hex validation.
Pairing text is excluded from saved state/autofill/learning and clears on Stop/pause.
Verification and installation status are in the UI report; Wi-Fi testing is deferred.

## Diagnostic0.18 — header and theme alignment

Stop is hidden unless connected, immediately left of the rightmost Settings icon.
Each Display choice centers its icon directly above its label with a small gap.
The scrollbar is hidden; page content remains scrollable.
Notification Stop remains available during setup. Verification and installation
status are in the UI report.

## Diagnostic0.17 — connection states and icons

Header Stop is disabled until the connection admits input, then disables again
on disconnection/teardown. The existing notification Stop test can cancel setup.
OK looks disabled before TV admission and turns blue when available. Tabs use
selection/focus fill with no border highlights. Display adds sun/moon icons;
Netflix uses its colored N mark, dimmed while disabled. Verification/install
status is in the UI report.

## Diagnostic0.16 — requested UI refinements

Setup hides the bottom Remote/Touchpad tabs; the header toggle returns to the
previous page. Close is removed, Stop has a square icon, OK remains blue while
disabled and directional arrows lose their circles. Admission and Stop behavior
are unchanged. Verification/install evidence is in the UI report.

## Diagnostic0.15 — prototype UI with launch correction

Version0.14 failed real Activity launch despite its synthetic-view checks.
Version0.15 defers theme/window setup until attachment; actual Activity launch
and light/dark changes pass on API31 emulator. Installed0.15 owner presentation
smoke passes. Both app enrollment records were already absent before correction
installation; an app-data clear event is logged with initiator unknown. Pairing
recovery and combined control smoke remain pending.

## Diagnostic0.14 — prototype UI (superseded)

Remote presents the TV D-pad and playback controls; Touchpad presents pointer
input, scroll controls and an accessible button alternative. Setup separates
Projector, TV controls and Display. Both themes apply in place without changing
session admission or pairing. Reviewed0.14 is installed; both pairings are
preserved and owner UI smoke is pending. Automated and installation evidence is
tracked in the [UI report](gates/nord-prototype-ui-report.md). This is not a
Bluetooth recovery fix or daily-use release.

## Diagnostic0.13 — cleanup fix installed, hardware recovery blocked

After profile loss during Stop, the new build can finish cleanup only when
actual selected-disconnect/unregister callbacks and all other cleanup complete.
Explicit Start/Connect is still required; missing evidence and independent
failures still block restart. The regression and167 unit tests pass. This is
build/test evidence. Version0.13 is installed with both pairings preserved and
no automatic run. Owner Home/pointer smoke passes, but a Bluetooth interruption
still reaches the cleanup deadline without the required native acknowledgments.
Explicit recovery remains blocked; this is not a verified recovery fix.

## Nord unified remote0.12 — pairing and combined-control smoke verified

The app includes D-pad/OK, Home/Back, volume, play/pause and Netflix alongside the
Bluetooth touchpad and scroll controls. The owner confirmed TV-code pairing
completes and stops on0.12; inspection confirms separate persisted TV/Bluetooth
enrollment records and stopped state. The approved peer trust-anchor exception
retains code binding, exact certificate pinning, native TLS validation and
private-key proof. Negotiation includes optional power-status events, with no
power input command.

Combined Connect verifies TV first and enables input only when both channels are
ready; either active-channel failure is designed to stop both. The owner verified
Start/Connect, Home/D-pad/OK, Netflix launch, pointer/title tap/scroll, Back/volume,
and Stop with the physical remote still usable. Saved reconnect after full app
Force stop/reopen also passes: input waits for explicit Start/Connect and TV keys,
pointer and scrolling work without re-pairing. Foreground loss/return and explicit
five-minute expiry also pass owner checks: all app controls stop and the physical
remote remains usable. Projector Wi-Fi loss also stops both channels; restoring
Wi-Fi does not resume control, by owner observation with the Nord app visible.
The Bluetooth-loss trial reports both channels disconnected and input disabled,
and the owner confirmed those controls send no input while the physical remote
works. Cleanup is unconfirmed; Android Force stop/reopen successfully restores
explicit Start/Connect without re-pairing. A fresh trace identifies profile-service
loss during Stop as a permanent restart block even when disconnect/unregister
callbacks follow; no recovery-policy change has been made. Full controlled Bluetooth-loss
acceptance remains pending. On0.12, the owner also verified at least20 seconds
of post-Netflix-launch connectivity with pointer title selection/Back, and
Play/Pause visibly pausing, resuming and pausing playback, followed by Stop.
Profile selection by Nord pointer/tap and all four scroll directions also pass
on0.12 without unintended title opening or pointer movement. Remote/Setup
status and all controls remain readable/reachable in both orientations at
normal and largest configured font size by owner observation. The live Nord
keyboard also leaves Setup fields, Confirm, status, Stop and Close readable and
reachable in both orientations at the usual font size; the stopped check used
the address field and did not change enrollment or repeat code entry.
The five-minute foreground-only diagnostic limit remains. No Mac, router
reservation or CA setup is required. See [N3 evidence](gates/n3-report.md).

## Nord UI refresh — owner smoke passed (2026-10-25)

Version0.7 is installed with saved enrollment preserved. Owner confirmed layout
and controls work for the requested orientation, reconnect, Netflix input, page
switching and Stop checks.
It provides a charcoal Remote page with touchpad, scrolling and mouse
alternatives, plus a separate Setup page and fixed Stop/Close footer. Labels scale,
controls wrap and content scrolls on small screens. It preserves explicit Start/
Connect and the five-minute foreground-only diagnostic. No TV-key capabilities
are added by0.7. N3 implementation is approved and in progress behind its native
TLS gate; N4 remains pending. See [UI report](gates/nord-ui-report.md).

## Nord scrolling — diagnostic behavior accepted (2026-10-25)

Version0.6 adds four labeled scroll buttons, one wheel/pan step per admitted
click, and immediate cancellation of pending pad input. The sole report format
is five bytes; version2 enrollment requires one fresh compared pairing after
manual removal of only the test bond on both devices. Existing version1 records
cannot authorize input. Automated tests/build and independent review pass with no actionable findings;
version6 is installed in place after owner-confirmed removal of both test bonds,
with legacy enrollment bytes preserved. Owner confirms usable portrait/landscape layouts and disabled input before
pairing. Owner subsequently confirmed fresh pairing/connection, pointer movement and
Netflix tapping work on0.6. Owner also confirmed vertical up/down and horizontal left/right work without
unwanted clicks or pointer jumps. Owner confirmed Stop blocks input while the physical remote works, and explicit
saved reconnect restores movement/scrolling without re-pairing. The final owner
response confirms leaving/returning stays stopped and five-minute expiry
disables input while the physical remote works. N2 diagnostic behavior acceptance
passes for this artifact/setup; the five-minute foreground-only limit remains.
See [N2 report](gates/n2-report.md). No new network capability or product claim.

## Nord touchpad diagnostic implementation (2026-10-25)

Version0.5 (`fdb4905`) implements one-finger relative movement and stationary
tap-to-click using the existing Bluetooth mouse report. Motion is bounded and
coalesced, with cancellation on lift/Stop/disable/focus loss and no swipe-click
or automatic reconnect. Stop/Close stay outside the scrolling setup area; native
directional/Click buttons remain. Local tests/build and independent review pass.
Version5 is installed on the Nord with enrollment bytes unchanged. Launch/process
are verified; initial tool-requested foreground was not confirmed with the display
asleep. The owner subsequently confirmed “Movement and tap both work” using the
new touchpad on Netflix: functional movement/target activation are observed.
The owner also confirmed swipe-return and multitouch do not click, a fresh
gesture works after all fingers lift, and Stop disables pad input while the
physical TV remote remains usable. The final owner response confirmed long press
does not click, foreground loss/return stays stopped, and five-minute expiry
disables pad input while preserving physical-remote use. The planned N1 diagnostic
behavior sequence passes; see the [N1 report](gates/n1-report.md). Broader
accessibility, crash cleanup and daily-use reliability remain unverified. Scrolling, held drag,
TV buttons and a daily-use session policy are not part of0.5.

## Bounded probe diagnostics (2026-10-25)

Probe0.4 (`d0a8c44`) adds fixed-event, sequence/elapsed-time Android logs for the
restart investigation, capped at 64 lines per run. It adds no connection or input
behavior. Local verification passes 53 tests/build/lint; independent review found
no actionable issues and installation/version4 is verified on the Nord. Initial
tool-requested foreground launch was not confirmed; the owner subsequently
performed the manual restart test and confirmed connection and actual left/up
pointer movement. The first requested0.4 attempt passed, corroborated by a
contiguous fixed-tag trace through registration, explicit connection, neutral
submission and movement reports. The earlier0.3 failure did not reproduce and
remains unexplained; reliability is not established by one successful attempt.
There is no new network permission or trace export endpoint.

## Browser-only hub implementation (2026-10-24)

The accepted local-only design now has certificate identity, native approval,
HTTPS/WSS sessions, socket-bound control leases and a bundled PWA implemented
for H1B testing. Real TV control is deliberately unavailable. Native ownership
and browser trust checks are documented in [setup](setup-hub.md) and
[H1B evidence](gates/h1b-report.md). Local verification passes103Python and36Node tests; scoped code reviews and
their fixes are complete. Neither H1A nor H1B has passed. The earlier H0 proof remains separate.

This file records observed behavior, not planned functionality.

## Spare-phone diagnostic result (2026-10-24)

The Nord AC2001 on Android12/API31 has an enabled HID Device profile and a
running `HidDeviceService` after the owner turns Bluetooth on. This is a
configuration observation, not an available phone remote capability. A separate
foreground HID probe was subsequently built, reviewed and installed/launched
on the Nord (source `954d666`). The user confirmed “HID registered”; that status
requires the actual successful app registration callback. The user subsequently
confirmed matching numbers on both pairing screens and the TV's connected
indication. The next attempt hit the probe's foreground-loss Stop state before
mouse input could be tested. Discovery-only stayed active; fresh pairing
reproduced the stopped state on selecting Pair. A timed native foreground-service
fix is implemented with foreground-only input, reviewed and installed on the
Nord as 0.2-diagnostic (`93da0d0`). The user confirmed “Pairing stays active;
pointer moves”: pairing-screen survival and Bluetooth pointer movement passed
the live retest. The user then confirmed a Netflix target click opens it, manual
Stop disables probe controls, and the physical remote works. Basic HID
compatibility is PASS for this setup/artifact. Native disconnect completion,
held-input crash release, negotiated-link security and reconnect safety remain
unverified; the Mac-to-phone bridge is unbuilt. H1A remains NOT PASSED; see the
[evidence report](gates/h1a-report.md#nord-live-configuration-check-2026-10-24).

Restart with an existing bond: the user confirmed comparison/input controls
remain disabled without fresh pairing. No TV Connect option was available;
an incoming reconnect attempt was not tested. This confirms startup rejection,
not a usable reconnect capability.

Foreground loss after confirmation: PASS by user report. Leaving a working
session and returning left controls disabled except Stop/Close, displayed the
foreground-loss stopped status, and preserved physical-remote operation. Returning
did not resume input. This is not a process-crash or radio-loss result.

Controlled Nord adapter loss/restoration: PASS. Bluetooth OFF and restored ON
were verified while the probe was left foreground. The user confirmed the probe
stayed stopped with movement/Click disabled and the physical remote still worked.
No automatic input resumption was observed. This is not successful reconnect,
out-of-range loss or process-crash/held-button cleanup evidence.

The owner accepted [retained OS-managed enrollment](superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md)
with explicit Connect, including the inability to verify original-key continuity
after missed bond replacement. Probe 0.3 (`7643aa8`) is now implemented, reviewed
and installed/open on the verified Nord. Its UI provides Start, Connect saved
projector, verified first enrollment, Forget and Stop. Local verification passes
48 tests and build/lint. The user confirmed “Reconnect works; physical remote
works” for the first fresh enrollment → Stop → Start → Connect saved projector
trial without another pairing ceremony. Explicit saved-projector reconnect is
observed on this setup. In the full process-restart trial (2026-10-25), the first
saved connection failed with a disconnect/disabled-input status; a retry connected
by user report. The same session's left/up check then moved the pointer without
another restart/pairing step. Saved enrollment and working reconnect after process
restart are observed on retry. Explicit Forget followed by Start with the OS
bond retained passed by user observation: no saved projector, Connect and movement
disabled. The remaining bond alone did not restore control. Reliable first-attempt
reconnect, OS bond-change revocation, durable Forget across process death and
fault cleanup are not established. No background or network control was added.

## Separate H0 local Mac proof

- Local native window combines fixed TV buttons and a scrcpy touchpad.
- Android TV Remote v2 pairing/verified connection: user confirmed connected.
- Control-only scrcpy connection: observed on the XK03H, no video/audio stream.
- Direct SDK proof-client tap: PASS, activated the Netflix profile tile (capture).
- Direct SDK proof-client vertical swipe: PASS, moved Netflix browsing content (capture).
- SDK cursor visibility: FAIL, user reported NO visible cursor in the SDK pad.
- New default `HardwarePointer` sends UHID-only relative mouse movement, buttons
  and wheel scrolling; it never mixes accelerated cursor movement with SDK coordinates.
- UHID cursor visibility/movement: PASS by user observation, "Pointer appears and moves".
- UHID Netflix title tile click: PASS by user observation, "Yes, the tile opens".
- UHID vertical/horizontal wheel scrolling: PASS by user observation in Netflix.
- Touchpad Stop, physical-remote use after Stop, and touchpad reconnect: PASS
  by user observation, "All three pass".
- Combined UI Home, D-pad/OK, Netflix shortcut, Back and volume: PASS by user
  observation alongside pointer selection, "All listed controls work", historically.
  The Netflix shortcut claim is contradicted by the later failure: button press
  drops both channels, and six remote-service ActivityNotFoundException entries
  name `market://launch?id=com.netflix.mediaclient`.
- UHID horizontal drag and normal release: PASS by user observation in Netflix.
- Playback/pause/resume and full Stop/reconnect without pairing: PASS by user
  report, "works, and the play/pause also works", responding to those checks.
- UHID profile selection: PASS by user observation, "Yes, it works", separately
  from the earlier SDK test.
- Netflix shortcut's prior failure is resolved by the implemented/reviewed fixed URI
  `nflx://www.netflix.com/browse` resolves with DEFAULT to NetflixComLaunchActivity;
  its local TLS payload test passes. The user confirmed "Netflix opens and both
  stay connected" after Home → Netflix, a 20-second wait and pointer movement
  on the corrected 6be01a1d… snapshot: live Remote v2 shortcut PASS.
- Active-drag interruption and abrupt hardware link/power loss remain untested
  production reliability limitations; local STOP tests do not establish those results.
- Main freshly reran 55 local tests after the fixed-link change, with ResourceWarning treated as errors:
  PASS. All listed lifecycle findings are fixed; app and pointer re-reviews found
  no remaining actionable findings. Tests cover UHID framing, bounds, dispatcher
  lifecycle, the STOP fence, certificate identity rejection and bounded exact-endpoint
  ADB recovery. The recovery reviewer found no actionable findings.
  A native-window render/Stop smoke passed; local tests do not establish hardware acceptance.
- Idle channel loss is checked every 100 ms. Stop synchronously fences further
  pointer input while allowing cleanup release/destroy; in-flight sends cannot be retracted.
- Missing-ADB-transport recovery: PASS in main's no-click/no-key check after
  disconnecting only the selected projector transport. Explicit Connect permits
  at most one eight-second connect to the configured private-IP/.local endpoint;
  it does not restart the daemon, discover devices or substitute another target.
- Combined connection after recovery: PASS by user observation, "Both stay
  connected and work" after Pair TV then Connect touchpad, testing Home and pointer
  in the reopened recovery snapshot. The user subsequently confirmed full
  Stop/reconnect and playback/pause/resume.
- No browser endpoint, PWA, Windows package, or released input runtime exists.

**H0 PASS: scoped local Mac functional proof.** Next is new hub trust/PWA design
and an acceptance gate; no network implementation, PWA, Windows or universal
robustness is certified. The sections below describe the earlier Android
accessibility gate only.

## Available in the Debug Hardware-Gate APK

- A TV-launcher setup screen navigable with a projector remote.
- A manual path to Android accessibility settings, with a debug-only fallback
  to general TV Settings for the XGIMI framework-package stub.
- A service-owned D-pad controller and separate cursor overlay.
- Cursor movement through the controller on the certified XGIMI Horizon XK03H.
- Local diagnostic display of current bounds and gesture dispatch state.

## M0.1 Certified Result

- Three fixed probe modes: baseline 80 ms, Android system tap timeout, and a
  controller-handoff mode with bounded frame and restoration deadlines.
- Closed manual activation recording and a fixed first-party 3×3 calibration
  target whose result resets before every probe.
- Fail-closed diagnostics for rejection, cancellation, callback timeout, frame
  timeout, interruption, and lifecycle shutdown.
- Emulator-only verification and release-isolation checks passed for source
  commit `a6f1b518961d80af9ca67b35b96bcdb4d292b45b` and debug APK SHA-256
  `66173e45f1f0e392de9ce418cee55fdcdb1f5a5670a8ef77aa152e08d1af37bd`.
- On the certified projector, all three Netflix probes and all three fixed
  calibration probes were admitted and then cancelled without activation.
- The verified classification is `PROJECTOR_OR_GESTURE_DISPATCH`; the result
  is `STOP` and does not authorize M0 rerun or M1.

## Failed on the Certified Setup

- Accessibility tap activation in Netflix and the first-party calibration
  target. Every permitted M0.1 mode was cancelled before activation.

## Not Run After the Tap Failure

- Vertical or horizontal Netflix scrolling.
- Drag and all cancellation paths.
- Cursor visibility over playback controls.
- 1080p playback usability while overlays are active.

These later checks are not implied to fail or pass.

## Not Implemented

- PWA or browser controller.
- Local HTTPS/WSS gateway, discovery, certificates, pairing, authenticated
  sessions, leases, or device revocation.
- Cross-device control from iPhone, Android, macOS, or Windows.
- Any release runtime input path. M0 controller classes and resources are
  excluded from release artifacts.

## Operational Limitation

The focusable M0 controller can retain D-pad focus and prevent normal launcher
navigation while enabled. It is a disposable debug gate, not a usable remote
control product. Disable its accessibility service or stop the debug app to
restore normal TV use.
