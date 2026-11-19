# Nord guided setup candidate evidence

Date:2026-10-30. Version22 /0.22-guided-setup-candidate.
Executable source: `da111fd` (later evidence-only commits do not change the APK).
Decision: SOFTWARE CHECKS AND REVIEW PASS; NORD WIZARD ACCEPTANCE PENDING.
Contract: [approved design](../superpowers/specs/2026-10-30-nord-setup-wizard-design.md)
and [implementation plan](../superpowers/plans/2026-10-30-nord-setup-wizard.md).

## Implemented scope

One native Overview → Bluetooth → address → Pair TV → All set journey, resumable
from completed configuration. Normal saved Connect remains separate. Manage has
confirmed per-item Re-pair/Forget, safe address changes and separate Appearance.
Optional own-app battery-settings navigation changes no setting automatically.

Runtime setup intent cannot promote temporary HID transport to input. Bluetooth
setup ignores saved TV transport only for its setup-only verification run; normal
Connect retains the existing both-channel policy. Real OS-granted visibility and
protocol-code deadlines are capped by the existing five-minute setup ceiling.
Safe closing waits for actual native owner/resource completion and preserves the
failed latch. A pending pairing continuation is ephemeral, generation-bound and
cleared by UI departure, cancellation or attachment loss; planned service cleanup
and eligible rebind may consume it once. No timer supplies native completion.

The new bounded AtomicFile address draft is private, backup-excluded and has no
trust meaning. The existing credential locator takes precedence. Existing app
identity, signer, credential formats, Keystore, TLS/pins, native gate, command/report
sets, backup/permission policy, code editor and dependencies remain unchanged.
TvRemoteClient adds deadline observation only. Visible code stays in one native
unsaved editor and clears on eligibility loss. No real code is captured or logged.

## Software evidence

-218 JVM tests pass, including all eight independent completion combinations,
  bounded draft load/write/readback failures, actual-source runtime handoff,
  discovery generation/expiry, no setup input epoch, service/visibility/Stop,
  stale callbacks and native cleanup proofs.
- API31 native runner59:58passed, one opt-in live-projector diagnostic skipped.
  Includes five isolated AtomicFile fixtures, nine wizard-view tests and existing
  Activity/service/privacy/TLS/accessibility/gesture/layout fixtures. Synthetic
  portrait/landscape, large-font and short-viewport checks do not certify physical
  TalkBack/IME behavior. No projector gate gesture was injected.
- Android lint:0errors/25warnings; debug and instrumentation APK builds pass.
  Warnings remain the experiment's native click-listener heuristics and English
  text/resource lint; no new dependency or runtime permission was added.
- Task-scoped runtime review fixes are resolved and re-reviewed. Complete Codex
  review found the unbound-visit and hidden-preparation issues described below;
  both are addressed in the clean final scoped Codex re-review. No actionable
  review findings remain. Reviews inspected source/evidence; the parent ran tests.

Artifact SHA-256: `aec2b6761d24780f17c20304e65d170c92de99a07b7e67f24de516aed4511461`.
The built APK's signer matches the preserved0.21 candidate. Protected source
comparison againstcc416a3 confirms unchanged credential/crypto/native-gate,
command/report, code editor, lifetime/epoch, manifest and backup-rule files.

The first native run reproduced a shared page-transition crash: Back retained a
parent in the detached old header. Reusing the native reparent helper fixed it;
the unchanged transition/layout suite then passed. Task review found and corrected
setup-surface departure retaining TV pairing, saved Bluetooth retry skipping the
link test, a dropped cleanup continuation and hidden TV revocation failure. Real
runtime-method regressions cover those paths. Full review then found the pending
handoff needed cancellation during a deliberately unbound interval, and hidden
Bluetooth preparation could stall before registration. A revocable UI-visit marker
now cancels without a binder and cannot authorize a later visit; hidden preparation
stops before a discovery request/window begins. Focused RED/GREEN regressions and
full affected suites pass. These are software fixtures, not
hardware pairing/reset acceptance.

## Delivery and remaining owner checks

The reviewed0.22 was installed in place on the confirmed stopped Nord. Prechecks
verified the intended Nord/Android12, version21, no connection service and both
existing enrollment records. The candidate signer matched the actual installed
APK. Both enrollment records and the optional address draft stayed byte-identical
before/after installation and launch. The resumed Activity's process remained
stable for six seconds; no foreground/started connection appeared automatically.

The original ADB service reported a routing error despite a reachable debugging
port. An isolated loopback-only ADB service connected successfully without
restarting other debugging sessions. No device addresses or enrollment contents
are included in evidence. No unpair, reset, data clear or projector input was
injected. Do not uninstall or clear app storage for the pending owner checks.

The owner reported all non-destructive0.22 acceptance checks passing: Review setup
shows Bluetooth, address and TV controls saved; Appearance works; saved Connect,
Home, pointer movement and one scroll step work; Stop reaches “Native teardown
completed.” No re-pairing was needed. This is owner-observed acceptance, not a
new independently captured native trace or fresh-enrollment test.

Full first-time wizard/mismatch/reset trials
require the owner to prepare the intended test bonds explicitly. Test denied
permission/discoverability, foreground loss/code expiry, unreachable peer and
recovery with accurate copy. Never treat a fixture result as a hardware PASS.

N4 lock/return,30-minute retention/expiry, loss/recovery and endurance/battery gates
remain as recorded in [daily-use readiness](n4-readiness.md). No public release
or daily-use readiness is claimed by this candidate.

## 0.23 delivery (2026-11-18)

0.23-design-alignment (versionCode 23) from source commit aa1d0d7 was installed in
place on the Nord (Android 12); APK SHA-256 prefix d17f88410f9b0b76. The design
alignment, collapsible status card, larger D-pad/touchpad and D-pad hold-to-repeat
are included. An earlier 0.23 build from before the last two commits had already
been installed; both share versionCode 23, so compare APK hashes, not versions.

Prechecks: the app showed "Stopped. Enrollment retained. Native teardown
completed." and no foreground connection service was running. The candidate was
explicitly signed with the same local debug key as the installed APK, after a
worktree build had produced a different signer; Android would have rejected that
build as an update. Both enrollment records and the two preference files were
byte-identical (SHA-256) before install, after install and after launch. After
launch no foreground service started and the app showed the saved projector as
disconnected. The default ADB server again reported no route to the reachable
debugging port; a separate loopback ADB server connected. No unpair, reset, data
clear, uninstall or projector input was performed. Hardware checks of the new
behavior are pending the owner's test.

## 0.24 delivery (2026-11-18)

0.24-ok-long-press (versionCode 24) from source commit bd5fc72 was installed in
place on the Nord; APK SHA-256 prefix 90984be2d8f266ef, signed with the same local
debug key as the installed app. Adds OK long press (START_LONG/END_LONG with an
800–1500ms TV-side hold and fail-closed release) on top of 0.23.

Prechecks: the app showed "Stopped. Enrollment retained. Native teardown
completed." with no foreground service. Both enrollment records and both
preference files were byte-identical before install, after install and after
launch (the touchpad preference differs from the 0.23 record only because the
owner changed sensitivity in between). No foreground service started on launch.
No unpair, reset, data clear, uninstall or projector input was performed.
Pending owner hardware checks: long-press OK opens the projector's long-press
action, and the XK03H releases a held OK when the control connection closes.

Owner hardware check (2026-11-18): long-press OK works on the projector with 0.24.
The release-on-disconnect behaviour (switching apps mid-hold) has not been reported.
