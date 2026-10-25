# N2 — Nord scroll buttons

Status: **N2 DIAGNOSTIC BEHAVIOR ACCEPTANCE PASS (2026-10-25)**.
Owner approved the N2 spec and one-time fresh pairing. No device was changed
while implementing/building. The subsequent controlled update and planned manual
sequence passed by owner observation for the recorded artifact/setup. H1A/H1B
and historical gates remain unchanged. This diagnostic does not establish
production readiness.

## Artifact and local verification

- Source: `a162384a515fae7999e964dff064ad29bb39b876`; base `7b05e8a47fd6459c1f38d43a46163b28d5e16b32`.
- Package `dev.opentvtouchpad.hidprobe`; versionCode6,0.6-diagnostic.
- APK SHA256: `b0f22fcdadac6c6d9cf1b00bfd8bcd3f8b6b78f465928438de78607c9c75f730`.
- Full Gradle unit suite: **101 tests,0failures/errors/skips**; lint
  **0errors/24warnings**; debug assembly passes.
- Report helper and enrollment migration were developed test-first in disjoint
  subagent tasks; main integrated Android controls and source-linked cleanup.
  New coverage:10 descriptor/report tests,8 scroll-input tests and7 additional
  enrollment/migration tests, alongside76 prior tests. Descriptor tests parse
  the emitted HID items independently of payload fixtures.
- Scroll tests use the real pad gesture helper to verify button-down cancels
  pending motion/tap before release, canceled button touch, keyboard activation,
  throttled clicks, denied eligibility, new-run rate reset and failed submission.
- Source-linked native regression compiles the actual Run.stop and scroll field
  with fake native collaborators; cases include scheduled motion, tap release,
  throwing/rejected scroll and permission loss followed by failed neutral cleanup.
  This is not Android framework execution or receiver-release evidence.
- Initial lint rejected the field-initializer scroll callback's permission flow.
  Its native submission now explicitly catches SecurityException and stops;
  existing admission and outer shared exception guard remain. No lint suppression.
- All descriptor/send/reply/neutral/release paths use MouseReport; no native
  three-byte construction remains. GET_REPORT validates type/id/buffer length.
- Packaged permissions remain exactly CONNECT, ADVERTISE, FOREGROUND_SERVICE and
  FOREGROUND_SERVICE_CONNECTED_DEVICE. No INTERNET permission. Packaged backup
  and device-transfer exclusions, nonexported service and cleartext prohibition
  are unchanged.
- Lint's22 earlier warnings remain:18 text/localization,2 SDK/dependency,
  programmatic View constructor and the pad's indirect performClick. Two new
  ClickableViewAccessibility warnings concern scroll-button touch observation;
  its listener returns false, retaining native Button click handling. Actual
  screen-reader/font-scale/layout behavior remains unverified.
- Default Gradle project inventory excludes the probe; opt-in task inventory
  contains debug assembly and no release assembly. Both preserved0.5 and new0.6
  APK signatures verify and use the same signing certificate.
- Preserved0.5 rollback APK SHA256:
  `e159521439bbd801b1bfebeb4b9b9646838b0a9ea0f40d153fc75937578de666`.
  It remains ignored local storage, not committed or distributed.

## Independent review

Independent Codex review covered the complete change from the recorded base,
including untracked files, documentation, connected session/ownership/gesture
callers and the source-linked cleanup fixture. Code/spec verdict: PASS; no
actionable findings. The reviewer independently ran all101 pure-Java tests.
The local report is `/tmp/nord-n2-implementation-review.md`; it is not a device
or package verification. Main separately verified Gradle/lint/APK boundaries.

Accepted limitations: actual Nord layout/accessibility and host wheel/pan behavior
remain live checks; native send success does not prove visible input. Existing
OS-bond trust assumptions and diagnostic-only lifecycle remain. No extra feature
or speculative hardening was added. Owner subsequently confirmed migration preparation; reviewed0.6 is now installed.

## Hardware acceptance — completed for the recorded setup

Target remains OnePlus Nord AC2001,Android12/API31,AC2001_11_F.24;
XGIMI Horizon XK03H,AndroidTV11,RT.3.24.327 release-keys;
Netflix8.112.1 build6 50665. These are prior observed versions, not a fresh
preflight. No identifiers or pairing codes belong in this report.

| Required observation | Result |
| --- | --- |
| Owner stops probe and removes only test bond on both devices | PASS: owner confirmed both removed; service stopped at preflight |
| In-place0.6 installation/version and old-record rejection | PASS: install/version6 and record preservation verified; owner confirms saved projector unavailable/input disabled |
| Actual portrait/landscape setup, pad, four scroll buttons, Stop/Close | PASS by owner: both orientations work, setup remains scrollable |
| Fresh compared pairing saves version2 before control | PASS by owner response to fresh comparison/Save and connect sequence; disk format not reread |
| Four-direction pointer movement and safe Netflix tap | PASS by owner: connected; movement and tap work |
| Vertical up/down and horizontal left/right away from edges | PASS by owner: all four directions work |
| Scroll produces no pointer jump or unintended title activation | PASS by owner: no unwanted click or pointer jump |
| Stop disables input; physical remote works | PASS by owner: Stop blocks input; physical remote works |
| Explicit saved reconnect without fresh pairing resumes control | PASS by owner: saved reconnect restores movement and scrolling |
| Foreground loss/return and five-minute expiry disable input | PASS by owner: both pass; physical remote still works |

Use `experiments/hid_probe/README.md` for migration and manual sequence. If either
axis fails after one reposition away from an edge, stop and record partial/fail;
no drag, boot-mode or ADB fallback. Keep the earlier unexplained0.3 first-attempt
reconnect failure deferred. This increment does not establish exact Bluetooth
key continuity/encryption, native crash cleanup or daily-use reliability.

### Controlled installation (2026-10-25)

Owner reported “Both test bonds removed; ready for update” after the requested
Stop/manual removal on both devices. Preflight found exactly one authorized
Nord matching OnePlus AC2001/API31 with versionCode5 and no active probe service.
Reviewed APK hash matched. In-place `install -r` succeeded; installed versionCode6
was verified. The private enrollment file was readable before/after and identical
in memory; it contained the legacy version1 record. No record contents, identifiers
or pairing digits were printed or persisted. This verifies data preservation,
not live input rejection or fresh enrollment.

Launch returned Status:ok; phone is awake, but full window inspection did not
show the probe focused. The narrower windows query omitted the focus field and
was not sufficient to establish foreground state. No screen was captured, no
control input injected, and no Bluetooth/ADB restart performed. Owner was asked
to open the probe and verify portrait/landscape reachability plus disabled input
before Start/pairing. Live layout, migration UI and scrolling remain pending.

### Layout and pre-pairing check (2026-10-25)

Owner reported “Both orientations work; input is disabled” in response to the
explicit portrait/landscape check of pad, four Scroll buttons, Stop/Close,
scrollable setup and unavailable old saved projector. Record this as owner-
observed reachability and disabled pre-pairing controls on0.6, not a screen-reader,
font-scale or full accessibility audit. No screenshot or injected input was used.
Fresh Numeric Comparison pairing and N1 movement/tap regression were requested
next; their result and both scrolling axes remain pending.

### Fresh enrollment and movement/tap regression (2026-10-25)

Owner reported “Connected; movement and tap work” after the requested fresh
Numeric Comparison pairing, Save and connect, four-direction pointer movement
and safe Netflix profile/title tap sequence. This is owner-observed behavior on
the installed0.6 artifact. The tested save-before-connect code writes version2;
the private record was not reread during this live step. No pairing digits or
identifiers were requested or logged. Both scroll axes and no unwanted click/jump
were requested next, with one reposition away from an edge permitted before
recording an unhandled direction. Stop/reconnect/background/expiry remain pending.

### Scroll compatibility (2026-10-25)

Owner reported “All four directions work; no unwanted click or pointer jump”
after the requested Netflix vertical up/down and horizontal left/right sequence.
This establishes both wheel/pan axes and no observed unintended activation or
pointer jump for this0.6 trial. It is owner-observed behavior, not measured latency,
accessibility certification or general app/device compatibility. No code changed
and no fallback input was used. Stop/physical-remote use and explicit saved
reconnect were requested next; foreground-loss and five-minute expiry remain.

### Stop and saved reconnect (2026-10-25)

Owner reported “Stop blocks input; physical remote works; saved reconnect restores
movement and scrolling” after the requested Stop, disabled pad/Scroll checks,
native teardown, and explicit Start/Connect saved projector sequence without
removing the bond or pairing again. This establishes the requested0.6 Stop and
saved-reconnect behavior for this trial. It does not resolve the earlier0.3
intermittent first-attempt failure or establish repeated reconnect reliability.
Foreground loss/return and five-minute expiry were requested as the final N2
checks. N2 acceptance remains pending until those observations arrive.

### Final lifecycle checks and acceptance (2026-10-25)

Owner reported “Both pass; physical remote still works” for leaving the connected
app/returning with pad and Scroll controls still disabled, then a fresh explicit
Start/Connect followed by five-minute expiry disabling both input surfaces and
preserving physical-remote use. This completes the planned N2 manual acceptance
sequence for source `a162384` and the recorded0.6 APK. No code changed during the
live sequence; source/build inputs and preserved APK hash were checked unchanged
at closure, so the101-test/build/review evidence remains applicable without a
redundant rerun. Documentation diff checks passed.

N2 diagnostic behavior acceptance is PASS, based on code checks plus the owner's
observations, not an automated recording or measured timing audit. N3 TV controls
and N4 daily-use lifecycle remain separately scoped. Exact Bluetooth encryption/
bond continuity, crash cleanup, broader accessibility and repeated reconnect
reliability remain unverified; earlier0.3 reconnect failure remains unresolved.
