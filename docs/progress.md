# Project Progress

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

## Current Status

The user selected local-only phone/laptop access with per-device certificate trust,
no account and no VPN. The [hub design](superpowers/specs/2026-10-24-local-pwa-hub-design.md)
and [seven-stage plan](superpowers/plans/2026-10-24-local-pwa-hub.md) are drafted.
Builder/security findings have explicit lifecycle and recovery rules. D1 remains
an owner decision: require proven protected projector transport, or explicitly
accept the existing unencrypted ADB hop for a trusted-LAN beta. Local HTTPS does
not resolve that hop. No product listener, trust installation or product backend
has been added. H1A/H1B/H2/H3 have not passed.

On 2026-10-24 the user approved a separate unified-remote hub proof (H0).
The isolated native Mac window has passed the scoped H0 functional proof.
It combines Android TV Remote v2 buttons and a no-video scrcpy input channel.
The user confirmed TV remote pairing/connection; diagnostic screenshots
confirmed direct SDK profile activation and vertical Netflix swiping. The user
reported NO visible cursor with the SDK pad. The default is now UHID-only
`HardwarePointer`, with mouse buttons and wheel scrolling; it never combines
accelerated cursor movement with SDK coordinates. The user confirmed "Pointer
appears and moves" in the UHID-only window: visibility/movement PASS. The user also
confirmed a UHID mouse click opens a Netflix title tile in the updated window.
The user confirmed both vertical and horizontal wheel scrolling work in Netflix.
The user also confirmed touchpad Stop, physical-remote use after Stop, and
touchpad reconnect. Home, D-pad/OK, Netflix shortcut, Back and both volume buttons
were initially reported working together; the shortcut claim is contradicted by
the later specific failure below. The user confirmed
horizontal dragging and normal release work in Netflix. The user subsequently
confirmed the remaining reconnect check and playback/pause/resume work.
All listed lifecycle findings are fixed, with no
remaining actionable findings in the app and pointer re-reviews. Main independently
freshly reran 55 tests with ResourceWarning treated as errors: PASS. Idle channel loss is
checked every 100 ms; Stop fences further input but cannot retract in-flight sends.
A later Connect touchpad failure was traced to a missing ADB transport, an
unresolvable configured hostname and daemon routing failure despite direct TCP
reachability. Main manually restarted an empty daemon (no devices or forwards)
and verified XK03H. Explicit Connect now permits at most one eight-second connect
to the exact configured private-IP/.local endpoint; no daemon restart or discovery
is automated. Missing-transport recovery passed without clicks/keys. The recovery
review found no actionable findings. In the reopened recovery snapshot the user
confirmed "Both stay connected and work" after Pair TV then Connect touchpad,
testing Home and pointer: original combined-connect regression PASS. Full
Stop/reconnect of both channels without pairing and playback/pause/resume were
subsequently confirmed by the user. UHID profile selection now also passed by
user observation: "Yes, it works". A subsequent Netflix-button failure dropped
both channels. Six remote-service ActivityNotFoundException entries name
`market://launch?id=com.netflix.mediaclient`, contradicting the earlier shortcut PASS.
The implemented/reviewed fix sends the fixed URI `nflx://www.netflix.com/browse`;
DEFAULT resolution finds NetflixComLaunchActivity, and main's fresh 55-test run
passed with ResourceWarning treated as errors. Neither resolver nor local TLS
evidence alone establishes live launch. The corrected GUI's live retest now passed:
"Netflix opens and both stay connected" after Home → Netflix, a 20-second wait
and pointer movement on tv_remote.py snapshot 6be01a1d… (full hash in the report).
All enumerated H0 functional checks have evidence: **H0 PASS, scoped local Mac proof**.
Abrupt hardware link/power loss and active-drag interruption remain untested
production reliability limitations; local STOP tests do not certify them.
Next is new hub trust/PWA design and its acceptance gate, not network implementation.
No PWA, Windows or universal robustness claim follows from this PASS.
See `docs/gates/h0-report.md`. No PWA/LAN service has been implemented.

The following status describes the original projector-hosted architecture:

Open TV Touchpad is stopped at the M0 certified-hardware gate. M0 recorded a
`FAIL` on 2026-10-20, so M1 and all product milestones remain blocked.

The gesture-focused M0.1 investigation completed on 2026-10-24 with a verified
`STOP`. All three Netflix modes and all three first-party calibration modes
were accepted by the service and then cancelled without activation. The
calibration result classifies the failure as `PROJECTOR_OR_GESTURE_DISPATCH`.
The original M0 failure and M1 block remain in force.

## M0 Evidence

- Source commit: `6223c19e4900c8086ba549e9fa326fd41d156c11`
- APK SHA-256:
  `f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a`
- Certified device: XGIMI Horizon XK03H, Android TV 11,
  `RT.3.24.327 release-keys`
- Tested app: Netflix `8.112.1 build 6 50665`, package
  `com.netflix.mediaclient`
- Result: the service could be enabled, its controller and cursor appeared,
  and cursor movement worked. Two user-triggered accessibility taps over a
  centered Netflix profile tile did not activate the tile.
- No ADB gesture or input injection was used. Later gesture, cancellation, and
  playback checks were not run after the required tap failed.

The detailed record is in `docs/gates/m0-report.md` and
`docs/gates/m0-checklist.md`.

## M0.1 Evidence

- Reviewed source commit:
  `a6f1b518961d80af9ca67b35b96bcdb4d292b45b`
- Debug APK SHA-256:
  `66173e45f1f0e392de9ce418cee55fdcdb1f5a5670a8ef77aa152e08d1af37bd`
- Emulator verification used only `emulator-5554`; unit, connected, lint,
  debug assembly, release assembly, evidence-verifier, and release-isolation
  checks passed.
- Certified-device result: all six permitted attempts ended `CANCELLED`; no
  intended Netflix or calibration target activated.
- Safety result: no observed key leakage, every cancellation removed the
  controller and cursor, and no ADB input command was used.
- `docs/gates/m0-1-report.md` verifies as `M0.1 report valid: STOP`.

## Milestones

| Milestone | Status | Notes |
| --- | --- | --- |
| M0 — Hardware Gesture Gate | `FAIL` | Required Netflix tap failed after the one allowed compatibility investigation. |
| M0.1 — Gesture Investigation | `STOP` | Compatibility exhausted; first-party calibration identifies projector or gesture-dispatch failure. |
| M1 — Local Trust and PWA Gate | `BLOCKED` | Must not start without a verified M0 PASS. Any redesign must first update the approved contract and pass its applicable prerequisite gate. |
| M2A–M5 | `BLOCKED` | Depend on the preceding hard gates. |

## Decision Required

The approved design makes Android accessibility gesture dispatch the only V1
input backend and Netflix success on this projector a hard gate. Continuing
requires explicit approval of a revised product target, input backend, or gate
contract. The approved H0 proof is scoped separately and does not reopen these gates.

The approved narrow investigation contract is in
`docs/superpowers/specs/2026-10-20-open-tv-touchpad-m0-1-gesture-investigation-design.md`.
Its certified-device execution is complete and does not authorize an M0 rerun
or M1. Continuing requires an approved target, backend, or gate redesign.
