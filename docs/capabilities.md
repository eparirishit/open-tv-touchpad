# Capabilities

This file records observed behavior, not planned functionality.

## Available in the Debug Hardware-Gate APK

- A TV-launcher setup screen navigable with a projector remote.
- A manual path to Android accessibility settings, with a debug-only fallback
  to general TV Settings for the XGIMI framework-package stub.
- A service-owned D-pad controller and separate cursor overlay.
- Cursor movement through the controller on the certified XGIMI Horizon XK03H.
- Local diagnostic display of current bounds and gesture dispatch state.

## Failed on the Certified Setup

- Accessibility tap activation in Netflix. Two attempts over a centered
  profile tile failed.

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
