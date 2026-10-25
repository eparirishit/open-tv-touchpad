# Architecture Status

## Approved Nord unified remote (2026-10-25)

The current direction is the Nord as a handheld remote. The approved
[N3 contract](superpowers/specs/2026-10-25-nord-tv-controls-design.md) combines
existing Bluetooth HID mouse/tap/scroll with Remote v2 TV keys over exact-pinned
native TLS. One Activity run owns both channels, its five-minute deadline and
Stop. A separate fail-closed TV enrollment store and non-exportable AndroidKeyStore
identity preserve the Bluetooth record. Initial TV-code pairing is a separate
no-input run; combined Connect enables input only after both channels are ready.
No router reservation, Mac bridge or CA installation is needed for this direction.
Native platform validation passes on the Nord; reviewed version0.9 integrates
the two channels and is installed. First owner TV pairing failed at an unknown
stage; combined TV behavior remains unverified. No trust-policy change is justified;
no N3 acceptance PASS yet. See [N3 evidence](gates/n3-report.md).
Installed0.10 retains this flow and adds only a closed failure-stage enum captured
atomically with the first terminal failure, then shown by the existing guarded
UI callback. Later cleanup cannot replace that stage or expose exception data.
Native and independent Mac evidence now identify the explicit peer self-signature
check as the TLS blocker. The [proposed leaf trust-anchor decision](superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md)
awaits owner approval; neither certificate policy nor pairing authentication has
been changed. TLS-only diagnostic instrumentation is explicit opt-in, sends no
pairing/control frames and does not mutate runtime identity or enrollment.
The older hub/projector architectures and gate evidence below remain historical
or deferred, and do not authorize an insecure runtime fallback.

## Browser-only hub implementation (2026-10-24)

The accepted local-only design now has certificate identity, native approval,
HTTPS/WSS sessions, socket-bound control leases and a bundled PWA implemented
for H1B testing. Real TV control is deliberately unavailable. Native ownership
and browser trust checks are documented in [setup](setup-hub.md) and
[H1B evidence](gates/h1b-report.md). Local verification passes103Python and36Node tests; scoped code reviews and
their fixes are complete. Neither H1A nor H1B has passed. The earlier H0 proof remains separate.

As of 2026-10-24, D1 is resolved: the owner requires protected projector transport
and authorized browser-only H1B implementation. H1A remains NOT PASSED; no
plaintext product exception is authorized. The accepted design supersedes the
earlier proposed-status descriptions below for this hub work only.

## Approved H0 experiment (2026-10-24)

The user approved a unified remote proposal with a local Mac/Windows hub:
phone/laptop PWA → authenticated hub → Android TV Remote v2 (normal controls)
and scrcpy input (pointer controls). This changes the proposed product ownership:
input coordination and controller trust would live in the hub. It requires an
awake host and separately verified projector transport security.

The active implementation is only `experiments/hub_remote/`: a native local Mac
window, a paired-certificate-bound Remote v2 adapter, and a version-pinned
control-only scrcpy client. It has no HTTP/WSS listener. The Android app and
its release boundary are unchanged. The user confirmed Remote v2 connection;
direct SDK gestures activated a Netflix profile and scrolled the browsing page
vertically, but the user reported no visible SDK cursor. The default is now
UHID-only `HardwarePointer`: relative mouse reports, button presses/releases and
vertical/horizontal wheels. Android owns the accelerated cursor position; the
hub never derives SDK touch coordinates from it or mixes the two input modes.
The SDK backend remains a separate diagnostic path. The user confirmed UHID cursor
visibility, movement, a mouse click opening a Netflix title tile, and both vertical
and horizontal wheel scrolling. H0 now passes as a scoped local Mac functional
proof; hardware reliability limitations remain below. The app calls `pad.request_stop()` synchronously
before scheduling cleanup. Further pointer input is fenced, with only cleanup
release/destroy allowed; already in-flight sends cannot be retracted. Runtime
idle channel loss checks run every 100 ms and stop both channels. Stop during old
pointer replacement prevents a new temporary session. All listed lifecycle fixes
passed regression tests and app/pointer re-review with no remaining actionable
findings; main freshly reran 55 tests with ResourceWarning treated as errors.
The recovery reviewer also found no actionable findings.
The user confirmed ordinary touchpad Stop, physical-remote use after Stop, and
touchpad reconnect. TV-channel Stop/reconnect subsequently passed by user report;
active-drag interruption remains untested on hardware. The user also verified Remote v2 Home, D-pad/OK,
Netflix launch, Back and volume alongside pointer selection in the same window;
the Netflix launch claim is contradicted by the later specific failure below.
Horizontal dragging and normal button release also passed by user observation
in Netflix; this does not establish release during Stop or link loss.
ADB recovery is confined to explicit Connect touchpad. If the configured
transport is missing, the client makes at most one eight-second connect attempt
to that exact private-IPv4:port or `.local`-hostname:port endpoint, then rechecks
readiness and validates XK03H and the pinned server hash. It never discovers or
substitutes a target, restarts the daemon automatically, or retries in the
background. Setup failure stops both channels and gives sanitized address/ADB
recovery advice without attributing the failure to TV pairing. A manual restart
of an empty daemon recovered the observed routing failure; that is not runtime
behavior. The no-click/no-key missing-transport recovery check passed. The user
also confirmed Home and pointer both work after Pair TV then Connect touchpad in
the reopened recovery snapshot. Full Stop/reconnect of both channels without
pairing and playback/pause/resume subsequently passed by user report. UHID profile
selection then passed explicitly: "Yes, it works". The Netflix shortcut subsequently
dropped both channels; this failure is retained in the evidence. Six remote-service
ActivityNotFoundException entries name the old `market://launch?id=com.netflix.mediaclient`
link. The reviewed fix keeps the same pinned Remote v2 path and sends only the
fixed `nflx://www.netflix.com/browse` URI; no arbitrary-link or ADB-launch fallback
is introduced. DEFAULT resolution to NetflixComLaunchActivity and a fresh 55-test
PASS verify resolution and local behavior. Separately, the reopened GUI's live
retest on snapshot 6be01a1d… passed: "Netflix opens and both stay connected" after
Home → Netflix, a 20-second wait and pointer movement. Stop-all behavior is preserved.
All H0 functional checks now have evidence. Abrupt hardware link/power loss and
active-drag interruption remain untested production reliability limitations,
despite passing local STOP tests. H0 PASS authorizes the next hub trust/PWA design
discussion and gate definition, not network implementation or PWA/Windows certification.

The mouse descriptor is adapted from scrcpy v5.0.1
`app/src/hid/hid_mouse.c`, with attribution in `pointer.py` and license in
`experiments/hub_remote/scrcpy-LICENSE`.

The H0 contract is `docs/superpowers/specs/2026-10-24-unified-remote-hub-proof.md`.
Only that experiment supersedes the old prohibition on runtime ADB and normal
remote commands. Historical M0 FAIL and M0.1 STOP remain unchanged. A product
PWA must satisfy a new trust/browser gate before shipping. In particular, TLS
to a hub does not protect a separate legacy plaintext ADB device link.

## Proposed local-only PWA hub contract

The owner selected per-device certificate trust without a VPN or account.
[Design](superpowers/specs/2026-10-24-local-pwa-hub-design.md) and
[implementation plan](superpowers/plans/2026-10-24-local-pwa-hub.md) propose a
reserved-IP HTTPS origin, native Mac enrollment/revocation, opaque sessions,
one socket-bound controller lease, and reuse of the tested adapters.
A constrained intermediate supports same-address leaf renewal; actual phone
trust/fingerprint ceremony and negative certificate tests are H1B requirements.
The new contract is still proposed. D1 must resolve whether the projector link
requires proven protection or an explicitly accepted legacy-ADB beta exception.
Current ADB capability flags do not establish encryption or projector identity.
No PWA control service is implemented and no old gate is superseded by this draft.

## Historical projector-hosted architecture

The approved architecture remains defined by
`docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`. This document
records the architectural effect of the certified-device M0 result.

## Failed Foundational Assumption

V1 assumes Android accessibility gesture dispatch can operate the sideloaded
Netflix mobile application on the XGIMI Horizon XK03H. The M0 debug runtime
successfully bound its accessibility service, displayed its cursor, and moved
that cursor, but two user-triggered taps did not activate a centered Netflix
profile tile.

The completed M0.1 investigation isolates the failure below Netflix: all three
fixed gesture modes were accepted and then cancelled on both Netflix and the
first-party calibration target. The verified classification is
`PROJECTOR_OR_GESTURE_DISPATCH`.

## Consequence

- Accessibility dispatch remains the only approved V1 input backend.
- Netflix success on the certified projector remains a hard M0 gate.
- The one allowed compatibility investigation was used for the XGIMI settings
  intent stub and did not change the input backend.
- M1 trust/network work and later product implementation must not begin.
- ADB input, arbitrary shell control, insecure browser transport, or another
  unreviewed input fallback must not be added to bypass the failed gate.

## Required Design Decision

Further implementation requires a separately reviewed change to at least one
of the certified device/application target, the input backend, or the M0 gate
contract. Until then, the repository preserves the debug evidence and remains
stopped at M0.

## M0.1 Result

The bounded investigation completed on 2026-10-24 with verified outcome
`STOP` and stop reason `COMPATIBILITY_EXHAUSTED`. Controller handoff did not
prevent cancellation. The approved accessibility-dispatch architecture is not
viable on the certified XGIMI setup; an M0 rerun and M1 remain blocked.
