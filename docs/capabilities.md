# Capabilities

This file records observed behavior, not planned functionality.

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
