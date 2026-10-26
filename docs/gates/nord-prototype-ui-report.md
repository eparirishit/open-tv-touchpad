# Nord prototype UI refresh — 0.15 presentation PASS; control smoke pending

##0.17 connection states, tabs and icons

Header Stop starts disabled and follows the existing Activity input-readiness
result. It disables again during failure/teardown. The foreground-notification
Stop test remains available during setup. OK is visibly disabled before TV
admission and blue when available. All tab states use fill rather than a border;
selected-state accessibility and fill-based keyboard focus remain. Display has
native sun/moon icons; Netflix uses a bundled colored N mark with disabled alpha
and the existing Open Netflix accessible label.

The new real-Activity Stop/default-state and missing-icon regressions failed on
0.16 before implementation. Final unit suite:167 pass, no failures/errors/skips.
API31 runner:28 tests,27 pass and one expected opt-in real-projector handshake
skip. Lint:0errors/37warnings. Both APK builds pass. Native checks cover default
Stop state after real Activity launch/theme changes, disabled/enabled D-pad
backgrounds, tab selection/press/focus borders, icon layout and accessibility,
plus earlier small/large/IME reachability and unchanged admission. Synthetic
connected screenshots prove presentation only; no live connection is claimed.
Light/dark disconnected, connected and Display screenshots were inspected.
Independent and complete Codex reviews found no actionable issues. Source commit
`adf0e8b` produced the reviewed artifact. Installed17 on the authorized AC2001/API31
with installed16 and no probe service verified first. APK signer matches0.16/0.7;
source and artifact hashes match the reviewed snapshot. Current enrollment files
remain byte-identical after install, launch and six-second stable-process check.
The initial foreground snapshot preceded resume; all subsequent checks confirmed
the app foreground with a stable PID. No automatic service/run, data clearing or
unpairing was performed. Owner visual/connected-state acceptance is pending;
connected screenshots are synthetic. Bluetooth cleanup and app enrollment recovery
remain blocked.

Version17/0.17-diagnostic APK SHA256:
`40eb83ed4ef92c232edf6341ffc84a1a260de0c130056ee29395565397b87318`.

##0.16 requested refinements

Settings now hides Remote/Touchpad tabs through keyboard open/close transitions;
its header toggle returns to the previous page. Close is removed, Stop has a native
leading square, OK remains blue in all states and directional arrows have no
circular backgrounds. A rectangular keyboard-focus outline remains. Disabled
admission, Stop and lifecycle shutdown are unchanged, as is the0.15 launch fix.

The focused footer regression failed on0.15 before implementation. Final checks:
167 unit tests pass; API31 native runner reports27 tests (26 pass, one expected
opt-in real-projector diagnostic skip); lint has0errors/37warnings; both APKs build.
Native tests cover Setup/IME restoration, rendered D-pad states, Stop icon layout,
actual Activity launch, palettes, small/large text and input admission. Screenshots
were inspected in light/dark; the synthetic fixture now inherits layout direction
like the real Window so the leading Stop icon is rendered. Independent review
found a test-only left/right assumption; corrected and rereviewed, then native
checks rerun. Complete Codex review against850bcc3 found no actionable regressions.

Version16/0.16-diagnostic APK SHA256:
`2d7ebf2e8b132400fc16c1a6dd2113e70faa75d66a1dfbfc649e3af279896e1f`.
Same-signature install verified the authorized AC2001/API31, installed15 and no
probe service first. Current enrollment bytes were compared before update,
after install and after launch; unchanged. Source/APK hashes matched the reviewed
snapshot. The launched process stayed foreground with the same PID for six seconds,
with no automatic service/run. No data clearing or unpairing was performed.
Owner visual acceptance of0.16 is pending. Prior enrollment recovery and Bluetooth
cleanup blocks remain; this is presentation evidence, not combined-control PASS.

Owner supplied an HTML prototype and requested native implementation on2026-10-26.
Reference SHA256: `6bfff5d16faaaa5a9a4ce57707944392dc304639660a1dde1e6de7bff20636db`.
The original bundle stays local; no mock device address or prototype JavaScript
is part of the APK or repository. See the [design](../superpowers/specs/2026-10-26-nord-prototype-ui-design.md).

##0.14 launch failure and0.15 correction

Owner reported0.14 crashed on launch. Nord AndroidRuntime identifies a
NullPointerException in PhoneWindow.getInsetsController from RemoteScreen's
constructor-time applySystemBars call, before ProbeActivity.setContentView.
The same real Activity launch failed on the API31 emulator. The earlier synthetic
view tests used an application ContextThemeWrapper and missed this Activity-only
path; a transient foreground observation after install was insufficient proof.

Version0.15 defers system-bar access until root attachment. The existing attach
callback applies the current palette; subsequent theme changes remain in place.
A new native regression starts the actual ProbeActivity, switches Dark/Light and
checks bar colors, icon appearance, disabled Connect and available Stop. The full
runner reports25 tests:24 pass and one opt-in real-projector diagnostic is skipped.
All167 unit tests pass; lint0errors/37warnings and APK/test assembly pass.
Independent review of the correction and complete-change Codex re-review found
no actionable issue. Source commit `dcd2a93` produced the recorded0.15 APK.

Version15/0.15-diagnostic APK SHA256:
`08c0394fabb741d8a63159ae6891578ed6ec14282cb751de6e403d517aa5fc8d`.
Historical0.14 evidence below is retained; its hardware UI acceptance FAILED.

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

Source commit `3cf705c` produced the recorded APK.
Version14 was installed on the verified AC2001/API31 with its probe service
stopped and installed version13 confirmed. The new APK signature matches preserved
0.13/0.7 artifacts; source and APK hashes matched the reviewed snapshot. Both
Bluetooth and TV enrollment files were required before update and remained
byte-identical after installation and launch. The Activity was foreground;
no remote service/run started automatically. No data clearing or unpairing.
Owner UI/combined-control smoke is pending; emulator evidence is not projector
acceptance. The five-minute limit and Bluetooth-recovery block remain.

##0.15 installation and owner result

The first correction install stopped BEFORE mutation because both enrollment
files were missing. Positive directory/stat checks confirmed user0 app-private
no_backup was empty, with no backup/new record files; shared_prefs was empty too.
The owner explicitly reports only attempting launch, not clearing data/reinstalling.
A package-specific system log records `clearApplicationUserData`. The initiator
is unknown; do not attribute this to the owner, the UI, or an OEM mechanism without
further evidence. Normal PACKAGE_REMOVED broadcasts during update are not proof
of a destructive uninstall. The earlier0.14 preservation checks remain historical
observations, not a guarantee about subsequent system actions.

The0.15 same-signature installer required and preserved that verified-empty state,
validated reviewed APK/source hashes, and did not clear data, unpair or restore
trusted enrollment automatically. The launched process remained stable/foreground
for six seconds, with no remote service or automatic run. Owner now confirms:
“App opens; screens and themes work” for Remote, Touchpad and Setup/Display Light/Dark.
That is presentation acceptance on0.15, not combined-control acceptance. Pairing
recovery and control smoke remain pending; no N3 or Bluetooth-recovery PASS.
