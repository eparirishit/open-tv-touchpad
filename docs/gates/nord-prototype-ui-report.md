# Nord prototype UI refresh — installed; owner smoke pending

Owner supplied an HTML prototype and requested native implementation on2026-10-26.
Reference SHA256: `6bfff5d16faaaa5a9a4ce57707944392dc304639660a1dde1e6de7bff20636db`.
The original bundle stays local; no mock device address or prototype JavaScript
is part of the APK or repository. See the [design](../superpowers/specs/2026-10-26-nord-prototype-ui-design.md).

## Scope

Diagnostic0.14 presentation on top of0.13: Remote/Touchpad navigation, circular
D-pad, icon controls, grouped Setup, light/dark appearance and touchpad/buttons
modes. The Activity remains the sole owner of admission, pairing and lifecycle.
Known Bluetooth-loss cleanup remains blocked; no N3 PASS or daily-use release.

## Evidence

- Native regression against the original0.13 layout fails because the touchpad
  is visible on the Remote page; the prototype requires a separate tab.
- New native-view checks exercise navigation, pending-tap cancellation, fields
  retained during theme changes, masked/unsaved code entry and unchanged disabled
  admission. They also cover320/360dp portrait,800dp landscape, two font scales,
  both palettes and keyboard insets.
- Final unit suite:167 tests, zero failures/errors/skips. Lint:0errors/37warnings.
  Debug APK and androidTest assembly pass.
- API31 emulator runner reports24 tests:23 passed, one expected assumption skip
  for the opt-in real-projector handshake diagnostic. All8 RemoteScreen tests pass.
  Synthetic screenshots cover Remote connected/disconnected, Touchpad, Buttons,
  all three Setup sections and both palettes. Parent inspected representative
  screenshots; no real pairing code/address or projector gesture was captured.
- Independent full-change review and complete-change Codex review found no
  actionable defects. Same-signature installation status is recorded below.
- Version14/0.14-diagnostic APK SHA256:
  `d114708e95ed033b564a870b6b106796b2694cb473683d047fcac1b4dd01b90c`.
- No owner hardware UI acceptance is claimed yet. Existing backend/session code
  is unchanged from850bcc3; Bluetooth recovery remains blocked.

## Installation

Version14 was installed on the verified AC2001/API31 with its probe service
stopped and installed version13 confirmed. The new APK signature matches preserved
0.13/0.7 artifacts; source and APK hashes matched the reviewed snapshot. Both
Bluetooth and TV enrollment files were required before update and remained
byte-identical after installation and launch. The Activity was foreground;
no remote service/run started automatically. No data clearing or unpairing.
Owner UI/combined-control smoke is pending; emulator evidence is not projector
acceptance. The five-minute limit and Bluetooth-recovery block remain.
