# Architecture Status

## N4 candidate implementation

RemoteConnectionService owns one process RemoteRuntime and its native callbacks,
resources, storage and lifetime. Activity-local polling runs only while resumed
and focused;
immutable snapshots and attachment-tagged requests carry no UI references into
runtime. InputVisibilityGate is a separate synchronized epoch authority and
SessionLifetime owns monotonic setup/active/idle/ended transitions. UI loss
revokes input and anchors idle before native readiness I/O. Activity loss clears
local input and unbinds without fetching health; the started service independently
retains established links and can actually destroy itself on Stop/expiry while
the UI stays hidden. Focused resume rebinds with a new attachment, fencing late
binding callbacks. Pending ordinary Connect cancels from before registration;
explicit attended setup retains its bounded system-dialog exception. Header Stop
validates attachment and revokes before attendance or health observations. Requests retain their
epoch through the TV writer, which closes uncertain user frames and reports
completion only after real workers/socket closure. Protocol deadlines stay bounded
independently of active duration. Setup retains its attended five-minute ceiling.
The0.21 candidate keeps application ID, signing identity, enrollment/Keystore and
backup boundaries. Its [software report](gates/n4-candidate-report.md) records
verification; Nord retained-link/endurance acceptance remains separate.

## N4 ownership target — approved2026-10-27

The owner's selected idle-background/visible-input direction requires separating
transport lifetime from input authorization. The
[N4 design](superpowers/specs/2026-10-27-nord-daily-use-design.md) specifies promoting
the existing foreground service to the sole RemoteRuntime owner, with no retained
Activity/views. The Activity would render snapshots and send epoch-scoped input;
visibility loss would revoke input immediately while healthy links idle within a
proposed30-minute limit. Protocol, trust and actual native-cleanup bounds remain.
R1 ownership extraction is committed/reviewed inb1d3256/62d0e6a. The candidate
now integrates the separate idle/visibility policy; Nord acceptance is pending.
Current0.20 still uses PairingGuardService, an Activity-owned Run, a five-minute
deadline and pause-stop. The [N4 matrix](gates/n4-readiness.md) records unresolved
Bluetooth completion, TLS fixture and hardware platform proof. No runtime change
or alternative cleanup evidence is implied by planning or emulator-only checks.

Diagnostic0.20 flattens Touchpad to pad, scroll controls and sensitivity. Alternate
mouse-button pages and Activity bindings are removed; native accessibility actions
on TouchpadView invoke the existing bounded move/click callbacks and cancel pending
touch before acting. Activity admission remains authoritative. Numbered setup rows
disable baseline alignment and use natural multiline height/spacing;72dp OTP cells
retain the same editor/privacy behavior. Tab labels use native bold type.

Diagnostic0.19 keeps new UI details in the native view layer. The Activity passes
actual TV transport readiness separately from mouse input readiness; dots never
grant admission. PairingCodeField is one native EditText with six rectangular
cells and owner-requested visible OTP text, retaining filtering and no state/
autofill/learning. It feeds the unchanged pairing/clear-on-pause/Stop path.
TouchpadGesture scales physical-dp deltas after slop recognition and before its
bounded accumulator; the view cancels pending frames on sensitivity changes.
A native seven-position0.5×–2× slider persists only a bounded non-secret step.

Diagnostic0.18 derives header Stop visibility in RemoteScreen.setConnected from
the same Activity-owned readiness value already used for input and Stop enabling.
Header ordering places Settings last. Theme choices use native top compound
drawables so icon and label center together without custom measuring/drawing.
The content ScrollView hides its vertical scrollbar while retaining scrolling.

Diagnostic0.17 has Activity updateControls apply the existing run.ready() result
to header Stop as well as pointer input. The service notification still offers
Stop test during setup; lifecycle and timeout teardown remain authoritative.
RemoteScreen gives disabled OK a neutral palette, preserves enabled blue, removes
tab strokes while retaining selected/focus fill and adds bundled theme/Netflix
vectors. No protocol, transport, pairing or new dependency is introduced.

Diagnostic0.16 keeps footer visibility owned by RemoteScreen from Setup and IME
state together. Close is removed from the header and Activity bindings; Stop and
Android lifecycle shutdown remain unchanged. Native Stop icon and D-pad palette
states are presentation only; disabled controls remain disabled.

Diagnostic0.15 defers system-bar access until view attachment because the Activity
window decor is unavailable during RemoteScreen construction. The real launch path
is covered by a native Activity test.

Diagnostic0.14 updates only native presentation to the owner-supplied prototype.
RemoteScreen owns view navigation and appearance; ProbeActivity still owns all
session state, enabled controls and action binding. The same view instances survive
theme changes, preserving field privacy and callbacks. All presentation navigation
cancels pending pad gestures. Appearance persistence contains only a dark-mode flag;
no new network, permission, dependency, lifecycle or enrollment path is introduced.
See the [UI design](superpowers/specs/2026-10-26-nord-prototype-ui-design.md).

Diagnostic0.13 preserves the N3 teardown barrier while classifying profile loss
during Stop as pending cleanup. Detached/disabled proxies cannot prove cleanup:
require this Run's selected disconnect and unregister callbacks, no observed
foreign link, successful proxy closure and TV/service completion. Nonterminal
callbacks, native pending-link observations and connect attempts invalidate disconnect evidence; profile
loss before Stop and independently failed operations retain the permanent latch.
This follows the Android12 unregister contract. The0.13 hardware follow-up
still reached the deadline with no disconnect/unregister acknowledgment in its
captured trace. The existing barrier remains mandatory; shutdown state or a
detached proxy cannot replace those callbacks under the current contract.
See the [correction plan](superpowers/plans/2026-10-25-nord-bluetooth-cleanup.md).

## Approved Nord unified remote (2026-10-25)

The current direction is the Nord as a handheld remote. The approved
[N3 contract](superpowers/specs/2026-10-25-nord-tv-controls-design.md) combines
existing Bluetooth HID mouse/tap/scroll with Remote v2 TV keys over exact-pinned
native TLS. One Activity run owns both channels, its five-minute deadline and
Stop. A separate fail-closed TV enrollment store and non-exportable AndroidKeyStore
identity preserve the Bluetooth record. Initial TV-code pairing is a separate
no-input run; combined Connect enables input only after both channels are ready.
Installed0.12 includes optional Remote v2 POWER status negotiation alongside
ping/key/volume/app-link, matching the working reference while preserving the
same required features and ordered RemoteStart readiness. It exposes no power
input and does not treat a negotiation timeout as success.
No router reservation, Mac bridge or CA installation is needed for this direction.
Installed0.12 passes owner pairing and the combined-control smoke, including
Stop and continued physical-remote use. Owner checks also confirm at least20
seconds of post-Netflix-launch connectivity with both channels usable, and
Play/Pause changing playback through pause/resume/pause. Profile selection by
Nord pointer/tap and all four scroll directions pass on0.12 without unintended
title opening or pointer movement. Owner layout checks confirm Remote/Setup
controls and status are readable/reachable in both orientations at normal and
largest configured font size. The live Nord keyboard layout passes in both
orientations at the usual font size with the address field focused while stopped;
fields, Confirm, status, Stop and Close remain readable/reachable. No enrollment
change or fresh pairing followed from this layout-only check.
Saved reconnect after app Force stop/reopen
also passes, with explicit Start/Connect still required. Foreground loss/return
and explicit five-minute expiry disable all app input by owner observation; the
physical remote remains usable. Owner-observed projector Wi-Fi loss stops both
channels and restoration does not resume input while the Nord app stays visible.
The Bluetooth-loss trial reached the reported stopped state with cleanup
unconfirmed; the documented process latch requires Force stop/reopen. Owner
checks confirm input rejection, physical-remote use and recovery without
re-pairing, with explicit Start/Connect required. A fresh trace confirms
PROFILE_LOST during Stop sets the permanent failure latch before later disconnect
and unregister callbacks. This establishes one blocker, not completion of all
remaining native cleanup. Controlled Bluetooth-loss acceptance remains partial;
no overall N3 PASS is claimed.
See [N3 evidence](gates/n3-report.md). Version0.10 added a closed failure-stage enum captured
atomically with the first terminal failure, then shown by the existing guarded
UI callback. Later cleanup cannot replace that stage or expose exception data.
Native and independent Mac evidence now identify the explicit peer self-signature
check as the TLS blocker. The [approved leaf trust-anchor decision](superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md)
removes only explicit peer self-signature verification in installed0.11 after
regression proof on emulator and Nord. Real projector pairing TLS now completes;
owner code enrollment and combined-input smoke pass on0.12, with remaining
hardware acceptance tracked separately.
Pairing authentication and native validation are preserved. TLS-only diagnostic instrumentation is explicit opt-in, sends no
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
