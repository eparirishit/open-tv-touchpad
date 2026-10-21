# Architecture Status

The approved architecture remains defined by
`docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`. This document
records the architectural effect of the certified-device M0 result.

## Failed Foundational Assumption

V1 assumes Android accessibility gesture dispatch can operate the sideloaded
Netflix mobile application on the XGIMI Horizon XK03H. The M0 debug runtime
successfully bound its accessibility service, displayed its cursor, and moved
that cursor, but two user-triggered taps did not activate a centered Netflix
profile tile.

The evidence does not establish whether Netflix rejected the dispatched
gesture, the firmware mishandled it, or the target surface could not consume
it. It does establish that the required end-to-end tap behavior was absent on
the certified setup.

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
