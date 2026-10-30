# Nord0.21 daily-use candidate — software verification

Date2026-10-27. Status: SOFTWARE CANDIDATE installed; N4 hardware acceptance
pending. Source `6abbc23c53c64d40e6618c562ac3442455e23b8e`. The owner approved the [N4 contract](../superpowers/specs/2026-10-27-nord-daily-use-design.md).
This report does not promote original M0/M1 or H1A/H1B gates or authorize release.

## Implemented behavior

- Service-owned RemoteRuntime, callbacks, enrollment access and native cleanup.
  One process runtime retains pending native owners across service recreation;
  no Activity/View/listener is held by the service/runtime. R1 extraction is
  committed inb1d3256; the discoverability permission guard correction is62d0e6a.
- Normal Remote/Touchpad uses one Connect; Setup retains an attended Start setup
  flow. Current enrolled peers/identity are reused; nothing auto-pairs or resets.
- Established links may idle for30minutes after first eligibility loss. Visible
  use has no five-minute cap. Pairing/setup retains its five-minute ceiling,
  discoverability/code and protocol deadlines. Healthy return grants a fresh
  input epoch on the same connection and requires a fresh touch-down.
- Input requires the current UI attachment, resumed/focused, interactive/unlocked
  Remote/Touchpad and healthy required transports. Input revokes before readiness
  I/O; the first-loss idle clock also starts before it. Setup/lock/focus loss,
  pause/detach and stale attachments cannot authorize user commands. Loss-handling
  returns before native health checks; one coalesced runtime refresh follows UI
  code/gesture cleanup. Native health work cannot delay lease revocation or the
  first-loss idle clock.
- TV commands retain their submitted epoch through queue and each native write.
  Unsent stale work drops. Uncertain/partial/in-flight user frames terminate the
  connection, with explicit reconnect after actual cleanup. Stop/revoke do not
  block UI on socket closure; actual workers/closure still gate completion.
- Idle traffic is existing protocol maintenance only. Stop/loss/expiry/task or
  process death ends the session; no boot start, sticky restart or retry loop.
  Open/Stop are the only notification actions. No wake lock, screen-on policy,
  battery exemption, new transport, dependency or LAN listener was added.
- App label is Open TV, version21/0.21-daily-use-candidate. Application ID, signer,
  Keystore alias, enrollment formats/paths and backup exclusions are preserved.
  The module remains opt-in/debug-only; no public APK release is being produced.

## Evidence

Current-baseline TLS and Bluetooth evidence are separate:
[TLS close-read](n4-tls-close-read-report.md) and [0.20 Bluetooth trial](n3-report.md).
The owner confirmed0.20 explicit reconnect after verified OFF→ON without Force
stop or re-pairing. This is not new candidate hardware evidence or a guarantee
that earlier missing-callback cases are impossible. NativeRunGate's proof and
failed latch remain intact; no replacement cleanup contract was introduced.

Core worker observed behavioral red→green for epochs/lifetime, asynchronous Stop,
stale queued work, partial/native writes, final-flush revocation and actual worker
completion;58 focused JVM tests pass. Parent actual-session/runtime regressions
observed red→green for retained peer state, hidden/stale UI, synchronous revocation
before native readiness, and first-loss idle-clock ordering. Existing real-method
Stop/Bluetooth/TV/service harnesses follow their actual production methods.

The new real-Activity/OS-focus screen test fails against R1 because diagnostic
Start is visible on Remote. It passes on the candidate. The existing large-font/
orientation test was updated to require Start setup in Setup and Connect on
Remote, preserving all control reachability checks.

Integrated verification after review fixes:206unit tests pass; native runner reports45 tests:
44passes and one opt-in projector-diagnostic skip; lint has0errors/20warnings;
debug and androidTest APK builds pass. This includes actual service recreation,
Activity status/privacy/layout/accessibility/OTP, native TLS fixtures and actual
window-focus loss/restoration with no real transport connection.

Verified app APK SHA256: `da6f7bb68cbb16a96769f6f273b70bb9ad284ccf35c894169d11f1e61a0d84a5`.
Verified androidTest APK SHA256: `1a70bfb87ef0bc6c48cd8d823c4dd2f0a752c75f5de44f2ec9bfc0765faedcef`.
The APK signer matches the accepted0.20 artifact. Trust/identity/enrollment/report
sources, permission set and backup attributes match the accepted baseline.
Review/install outcomes are recorded in [N4 readiness](n4-readiness.md). Generated APKs and raw
logs stay local.

## Reviewed Nord update

Installed version21 in place from the exact app artifact above. Its signer
matches the accepted0.20 APK; both enrollment files were compared in memory and
remained byte-identical before update, after update and after launch. The process
stayed stable for six seconds. The Activity was not observed resumed, and no
foreground-run counter was available; no Start/Connect, pairing or input was
injected. This proves the update checks, not successful on-device connection or
background retention. The owner was asked to unlock/open manually and run a short
foreground-control →30-second Home/return →Stop check. On2026-10-29 the owner
reported disconnect after approximately one minute on Nord Home. Installed APK
hash still matches exactly. Owner text and read-only UI inspection agree on
CONTROL_EXCHANGE failure, both channels stopped and native teardown completed.
The process remained present and its connection service was absent; that prior
trace expired. A fresh trial identified Hans freezing the app after30seconds
on Home despite the foreground service. The owner approved enabling background
activity for Open TV only, with auto-launch Off. The unchanged APK then passed
the controlled awake Home-return test and owner pointer/TV/scroll control without
Connect; explicit Stop was corroborated by disconnect/unregister/teardown and
inactive foreground service. See [before/after evidence](n4-home-return-report.md).
No executable source, enrollment, timeout or security behavior changed. Lock,
long retention/expiry and battery/endurance remain open.

## Review corrections

Review reproduced two connected-caller defects: Activity loss callbacks still
fetched native health inline, and ordinary Connect could survive hiding before
HID registration. Loss now revokes and clears input locally, releases the binding
without a snapshot, and rebinds only on resumed focus gain. A started foreground
service retains healthy links independently. Each binding fences late callbacks;
read-only service recreation preserves permission/status diagnostics. The actual
Activity-source regression and native focus test verify binding release and real
service destruction before focus returns. Pending ordinary Connect is marked
before registration and irreversibly cancelled on loss, while explicit bounded
setup keeps its system-dialog exception. Actual-source tests also verify visible
registration polling does not cancel a valid attempt.

The obsolete permission-copy assertion was corrected after reproducing failure
with both permissions granted. The focused granted-permission test and full
denied-permission native suite pass. Native completion still requires the real
service/worker/Bluetooth acknowledgments; no timeout substitutes for proof.
Independent scoped re-review closed the Activity/pending-Connect findings. The
complete Codex re-review then found header Stop checked native health before
revoking queued authority. The actual request-path regression reproduced that
ordering; Stop now validates the attachment and revokes before attendance/health.
Stale attachments still cannot stop the current session. All206unit checks,
lint and APK builds pass after this correction. The final exact-artifact native
runner has44passes/one opt-in skip. The final scoped Codex re-review closes the
Stop finding with no remaining actionable findings. Original findings and their
dispositions remain in the private review record; no unrelated scope was added.

## Hardware and pilot gates still open

Short Home/
lock/return on the Nord; use beyond five minutes; near-deadline retained return
and idle expiry; notification/task removal; current-artifact Bluetooth/Wi-Fi
loss, explicit recovery, reboot/process/bond/pin scenarios; two-hour active soak
and unplugged background battery/thermal observations. Manual observations must
name the exact artifact and outcome. Emulator/fake-native results do not close
these gates. A debugging connection must not substitute for ordinary unplugged
phone conditions during endurance observations.
