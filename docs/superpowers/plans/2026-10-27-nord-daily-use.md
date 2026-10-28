# Nord Daily-use Readiness Implementation Plan

> **For agentic workers:** After design approval, use subagent-driven-development
> for independent units; parent integrates service ownership and gated hardware proof.

**Goal:** Deliver the selected retained-idle, visible-input Nord private pilot
without compromising teardown proof or saved trust.
**Architecture:** One foreground-service-owned RemoteRuntime; Activity is a
snapshot/controller with an epoch-scoped visibility lease. Protocol and gesture
bounds remain separate from session duration.
**Tech Stack:** Existing Java/API31+/Android SDK36, current libraries only.
**Spec:** ../specs/2026-10-27-nord-daily-use-design.md (APPROVED2026-10-27).

## Global constraints

The owner approved implementation2026-10-27. Keep installed0.20 unchanged until
a reviewed same-signature update is ready; R1 preserves five-minute/pause-stop. Preserve app ID/signer/keys/
enrollment/backup policy; no uninstall, auto-pair, boot-start, cloud/bridge or new
commands. Never clear a cleanup barrier merely on timeout. No hidden user input.

## Review focus

- Hidden/stale Activity commands must fail even when both transports stay ready.
- Pause racing an in-flight TV write cannot leave a replayable/partial user frame.
- Screen-off/OEM suspension must not refresh lifetime or falsely show input ready.
- Missing native acknowledgments cannot turn into a successful restart by timeout.
- Upgrade/rotation/process death cannot lose trust or automatically regain input.

### Task 1 — close prerequisite evidence (R0)

Files: TvTlsTest.java; BluetoothCleanupTest.java; NativeStopTest.java;
ProbeActivity.Run cleanup only if evidence identifies an in-scope ordering fix;
docs/gates/n3-report.md and new N4 readiness report.

- [x] Capture close-read integer/exception, close/completion timing and server
  outcome on the same artifact/provider, isolated and full-suite ordering.
- [x] Reproduce any assertion defect before a separate test-only correction;
  retain pending100ms and closed2s bounds. No TLS runtime change without evidence.
- [x] Capture one contiguous owner-operated Bluetooth loss/recovery trace when
  available; establish actual native completion, stale-owner fencing and explicit
  reconnect. Do not repeat UI smoke or erase prior failure evidence.
- [ ] If callbacks remain unavailable, stop at the gate and review a concrete
  recovery-proof decision. No alternative evidence is pre-approved by this plan.

### Task 2 — move ownership without changing policy (R1)

Create: RemoteRuntime.java. Replace internal PairingGuardService.java component
with RemoteConnectionService.java; update AndroidManifest.xml, ProbeActivity.java,
ProbeSession.java composition and actual-method test harness callers.

Runtime consumes existing EnrollmentStore/TvEnrollment/NativeRunGate,
TvRemoteClient and bounded input adapters. Service alone owns callbacks/timers.
Activity binds to immutable snapshots and closed Connect/setup/Stop/input requests;
no runtime callback retains Activity/View references.

- [x] Write failing actual-method tests for rotation/detach, one active owner,
  resource completion, late callbacks and service/TV-worker teardown.
- [x] Extract owner/callbacks, atomically update manifest and notification targets;
  retain current five-minute/pause-stop behavior and foreground-only pairing.
- [x] Run176+ existing unit and relevant native suites, inspect exports/permissions/
  backup/Keystore aliases and review before any stopped-device update.

### Task 3 — explicit policy kernel and UI-input epoch (R2)

Create: InputVisibilityGate.java and SessionLifetime.java plus focused tests.
Modify: RemoteRuntime, ProbeSession, TvRemoteClient, ProbeActivity and
TouchpadView/ScrollInput binding as needed. Do not duplicate gesture recognizers.

InputVisibilityGate consumes attachment/generation, resumed/focused/interactive/
unlocked/surface-visible state. It grants a monotonically new epoch only for the
current eligible UI. Runtime owns grant/revoke; requests carry that epoch.
SessionLifetime consumes monotonic time: attended setup300000ms ceiling;
control visible duration has no five-minute cap; hidden retention1800000ms from
first eligibility loss. Only a healthy eligible return before expiry ends idle;
maintenance/repeated hide events cannot extend its deadline. Hiding during a
control connection attempt cancels it; only already ready links can be retained.

- [x] Red tests: revoked/stale epoch at submission and immediately before user
  writes; queued command cancellation; mid-frame uncertainty closes the session;
  click release; new-down requirement; lock/focus loss and Setup never authorize.
- [x] Separate transport readiness from input lease; revoke without blocking on
  I/O, clear unsent input and keep only protocol maintenance while idle.
- [x] Preserve all phase/liveness/rate/queue/write bounds from the spec. Do not
  extend dead-peer deadlines or replay commands when idle retention renews.
- [x] Test first-loss deadline, repeated hide/maintenance without renewal, hiding
  during connection, expiry after sleep, race with return, task removal, notification
  Stop, permission/bond/pin loss, service death and explicit reconnect requirements.

### Task 4 — UI binding and foreground service lifecycle

Files: ProbeActivity, RemoteScreen, RemoteConnectionService, AndroidManifest.xml,
existing native UI/privacy/accessibility tests.

- [x] Normal saved remote uses Connect/Stop; setup remains explicit and bounded.
  Replace diagnostic session copy only once the new behavior is implemented.
- [x] Start connectedDevice service from visible user action; non-exported,
  START_NOT_STICKY, notification Open/Stop, no notification input controls.
- [x] Bind/recreate without changing live ownership. Healthy return grants a new
  input epoch only on a visible/unlocked Remote/Touchpad surface. Failure/expiry
  never launches a new connection automatically; UI gives an explicit action.
- [ ] Native real-Activity tests for home/lock/focus/Setup/rotation, accessibility
  while hidden, OTP clear/no saved state and reachable notification/Stop paths.

### Task 5 — controlled Nord proof (R3)

- [ ] Build/review, record exact source/APK/provider/firmware and verify stopped
  same-signature installation with current enrollment bytes and key usability.
- [ ] Prove short idle home/lock retention first; no hidden actions can reach TV.
  Return healthy without re-pairing; a new gesture works and no old input replays.
- [ ] Prove30-minute idle/expiry via monotonic observations, visible use beyond
  five minutes, task swipe Stop and notification Stop while locked.
- [ ] Test Bluetooth/Wi-Fi loss independently, explicit recovery without Force
  stop, projector reboot, phone process death, bond/TV enrollment revocation,
  changed locator retaining exact pin and physical-remote usability.
- [ ] Gate failures keep readiness BLOCKED; do not silently select a different
  lifecycle, waive evidence or alter a threshold after seeing failure.

### Task 6 — private pilot packaging and acceptance (R4)

- [ ] Same module/app ID/signature and retained keys/stores for the first pilot;
  verify versioned upgrade from0.20 and rollback/recovery compatibility before
  relying on any downgrade. Preserve a known-good APK; never assume downgrade
  safely restores storage. No key export or unreviewed signing-key rotation.
- [ ] Debug/release-distribution boundary remains explicit. Document private
  installation, update, permissions, network isolation and truthful recovery UI.
- [ ] Complete checklist, two-hour active soak, repeated lock/return, idle battery/
  thermal observation and crash/reboot/revocation checks; owner accepts exact APK.
- [ ] Independent/code review of complete change and callers, focused verification
  after fixes, merge dependent PRs only with explicit merge authorization.
- [ ] Public release signing/store/module promotion and other phone models require
  separate scope after private readiness; no extra framework/provider added now.
