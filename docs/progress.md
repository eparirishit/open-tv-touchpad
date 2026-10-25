# Project Progress

## Current Status

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
contract. No such change is assumed by this repository.

The approved narrow investigation contract is in
`docs/superpowers/specs/2026-10-20-open-tv-touchpad-m0-1-gesture-investigation-design.md`.
Its certified-device execution is complete and does not authorize an M0 rerun
or M1. Continuing requires an approved target, backend, or gate redesign.
