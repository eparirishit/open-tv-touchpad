# Project Progress

## Current Status

Open TV Touchpad is stopped at the M0 certified-hardware gate. M0 recorded a
`FAIL` on 2026-10-20, so M1 and all product milestones remain blocked pending
an explicit architecture decision.

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

## Milestones

| Milestone | Status | Notes |
| --- | --- | --- |
| M0 — Hardware Gesture Gate | `FAIL` | Required Netflix tap failed after the one allowed compatibility investigation. |
| M1 — Local Trust and PWA Gate | `BLOCKED` | Must not start without a verified M0 PASS. Any redesign must first update the approved contract and pass its applicable prerequisite gate. |
| M2A–M5 | `BLOCKED` | Depend on the preceding hard gates. |

## Decision Required

The approved design makes Android accessibility gesture dispatch the only V1
input backend and Netflix success on this projector a hard gate. Continuing
requires explicit approval of a revised product target, input backend, or gate
contract. No such change is assumed by this repository.
