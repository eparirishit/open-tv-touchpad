# Nord native UI verification — 0.7-diagnostic

Date: 2026-10-25. Branch: `nord-remote-ui`; published base `f5cee1c`.
Implementation source commit: `b26c05c6f163291150996781ee815c0f141dafe5`. APK SHA-256:
`900897a0790c07baab74b92267544539f479af1322f98e38465fe0c4890eefb2`.

## Scope and evidence

Presentation only: native RemoteScreen composition, dark theme and touchpad drawing.
The actual connected Google TV Android app was inspected locally as the visual
reference; its private screenshot is ignored and not bundled. No Google assets,
new runtime dependency, network permission, TV commands or lifecycle extension.

- Debug/test APK assembly passes with the pinned offline toolchain.
- 101 existing unit tests: zero failures, errors or skips.
- Lint: zero errors, 26 warnings (24 baseline; two additional native touch-listener
  accessibility warnings). Listeners return false and preserve native click handling;
  warnings are not suppressed. This is not a TalkBack certification.
- Two API31 emulator native UI tests pass with fake callbacks: disabled startup,
  360x800/800x360dp and font scales1.0/1.5, reachable labeled48dp controls, fixed
  footer, Setup toggle DOWN/direct native click cancellation of a pending tap,
  and page switching without authorization. Reachability now also checks the
  ScrollView viewport, not just root bounds. An earlier harness-only accumulating
  scrollBy error was fixed to scrollTo; the final run passes.
- Inspected detached native Remote/Setup renders, including enlarged text.
  Landscape uses vertical scrolling; controls are not all visible simultaneously.
  These tests do not certify attached system-bar insets or physical finger scrolling.
- Independent Codex subagent reviewed all changed/untracked code and connected
  callers against the accepted contract: no actionable code/security defect.
  Scoped re-review of the strengthened tests and final documentation also passed.
- MouseReport, ScrollInput, TouchpadGesture, ProbeSession, EnrollmentStore,
  NativeRunGate and PairingGuardService are byte-identical to the published base.
  Touchpad event/scheduling code is unchanged. Session admission stays in Activity.
- APK signature verifies and matches preserved0.6. Packaged permissions remain
  exactly BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, FOREGROUND_SERVICE and
  FOREGROUND_SERVICE_CONNECTED_DEVICE. Manifest backup/export rules unchanged;
  release build remains disabled and module remains opt-in.

## Installation and owner acceptance

Read-only preflight found the unique authorized Nord AC2001/API31 with no running
probe service. In-place update succeeded: versionCode7/versionName0.7-diagnostic
verified, enrollment bytes compared in memory before/after and unchanged. Launch
returned success but foreground focus was not confirmed; owner asked to unlock
and open manually. No Start/Connect/input was injected. The existing OS bond was
not changed, and no storage was cleared. Preserved0.6 APK is local/ignored; downgrade has not been tested.

Owner responded “Layout and controls work” to the requested sequence: both
orientations, saved Start/Connect, Netflix pointer movement, one safe title tap,
scrolling, Setup/Remote switching and Stop. **Version0.7 owner smoke PASS** for
this artifact/setup. This is owner-observed evidence, not automated TV verification.
Five-minute foreground-only diagnostic limits remain. The projector reference is
XGIMI Horizon XK03H/AndroidTV11/RT.3.24.327, Netflix8.112.1 build6 50665. Full accessibility, crash cleanup, downgrade and
broader daily-use reliability remain unverified. No H1A/H1B or product gate becomes PASS.
