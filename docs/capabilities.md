# Capabilities

## Browser-only hub implementation (2026-10-24)

The accepted local-only design now has certificate identity, native approval,
HTTPS/WSS sessions, socket-bound control leases and a bundled PWA implemented
for H1B testing. Real TV control is deliberately unavailable. Native ownership
and browser trust checks are documented in [setup](setup-hub.md) and
[H1B evidence](gates/h1b-report.md). Local verification passes103Python and36Node tests; scoped code reviews and
their fixes are complete. Neither H1A nor H1B has passed. The earlier H0 proof remains separate.

This file records observed behavior, not planned functionality.

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
