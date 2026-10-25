# Local PWA Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One installed phone/laptop remote combining verified TV controls and Netflix pointer input through an awake Mac.

**Architecture:** The Mac owns device credentials, enrollment approvals and Stop. One asyncio loop owns network authorization and a socket-bound controller lease; one serialized worker owns blocking pointer input. Reuse the H0 adapters after transport and browser gates pass.

**Tech Stack:** Python 3.13/Tk, cryptography 50.0.2, androidtvremote2 0.3.2, scrcpy 5.0.1; candidate aiohttp 3.14.5/keyring 25.7.0; bundled browser JavaScript and standard Node test runner.

**Spec:** [Local PWA and Mac hub design](../specs/2026-10-24-local-pwa-hub-design.md).

Status: browser-only code implemented and reviewed;103Python/36Node tests pass.
H1A and actual-browser H1B gates remain NOT PASSED. Real TV integration waits.
D1 still requires a protected projector link. Owner has no USBdata cable;
spareAndroid Bluetooth HID is a researched candidate, not an accepted implementation.
Task 1 records the accepted contract. Tasks 2–5 can then run without a projector;
Task 6 additionally requires the selected H1A transport policy and H1B PASS.
Do not turn an unresolved protected transport into guessed implementation steps.

## Global Constraints

- Preserve H0 PASS and historical M0 FAIL/M0.1 STOP; no changes to Android runtime.
- Local-only, exact reserved private IPv4:8443, HTTPS/WSS; no HTTP bootstrap, `.local` dependency, accounts, VPN, cloud, remote assets or telemetry.
- Native owner alone approves enrollment, revokes controllers and configures target.
- Exact routes/commands and limits are those in spec sections 6–8; no arbitrary addresses, commands, packages or URLs from browsers.
- No real device backend until H1B PASS and the explicit H1A policy. No silent plaintext fallback; ADB daemon remains loopback-only.
- Candidate dependencies require compatibility, license, advisory and transitive hash-lock review before installation. No custom TLS/crypto.
- Use subagents with disjoint write scopes and fresh task review. Keep small conventional commits, no main push, review whole proposed change before pushing.
- Update progress/capabilities/architecture and gate reports with actual evidence; never substitute local tests for device/browser observations.

## Review Focus

- Shared-cookie tabs and delayed old-socket messages must not affect a newer lease (Task 4).
- Stop during connection setup must dispose late resources without reviving input (Tasks 4, 6).
- Enrollment response loss, reload and key loss must have explicit recovery without secret disclosure (Tasks 2, 3).
- Installed-PWA suspension/update and second-finger arrival during drag must release input (Task 5).
- Host process death can bypass release watchdogs; measure device cleanup before claiming robustness (Task 6).

## Task 1: Accept the contract and preserve H0

**Files:** `AGENTS.md`, `docs/architecture.md`, `docs/progress.md`, `docs/gates/h0-report.md`; create `docs/gates/h1a-report.md`.

**Interfaces:** Produces the accepted transport policy and its evidence, not a new transport abstraction.

- [x] Record the owner's D1 decision (protected transport selected, 2026-10-24). If protected transport is required, investigate supported pairing/physically controlled USB only within authorized device scope; prove peer rejection and no plaintext downgrade before H1A PASS. Record unsupported outcomes honestly. If the owner accepts trusted-LAN beta, record the precise unencrypted-hop exception and its residual risk instead of claiming secure transport PASS.
- [x] Update AGENTS with a scoped accepted hub contract; preserve historical Android constraints for that app. Local-only preference alone is not the legacy-ADB exception.
- [x] Preserve H0 runtime and tests in commit `ee25da5` tied to report hashes; do not migrate until this baseline exists. Confirm existing reviewed source hashes, rerunning tests only if code changed.
- [x] Commit contract/report changes separately as `docs: define accepted local hub contract`.

## Task 2: Certificate identity and native owner setup (H1B, no device)

**Files:** create `hub/__init__.py`, `hub/main.py`, `hub/local_ui.py`, `hub/identity.py`, `tests/hub/test_identity.py`, `hub/requirements.lock`, `docs/adr/001-hub-runtime-and-trust.md`, `docs/gates/h1b-report.md`.

**Interfaces:** `create_identity(address: IPv4Address) -> None`, `load_tls_context(address: IPv4Address) -> ssl.SSLContext`, `export_root(destination: Path) -> str` (returns full SHA-256 fingerprint). Native setup supplies only the configured address; browser cannot invoke identity management.

- [ ] Write tests for exact-IP SAN/constraints, out-of-scope IP/DNS rejection, 90-day renewal, issuer-expiry refusal, denied/substituted Keychain backend and exclusive0600 temporary-file cleanup on success/error. Fixture certificates are disposable and never installed into system trust by tests.
- [ ] Run `.venv/bin/python -B -W error::ResourceWarning -m unittest discover -s tests/hub -p test_identity.py -v`; require an expected missing implementation failure before code.
- [ ] Review/lock dependencies, then implement spec section5 using cryptography, native macOS keyring backend and standard SSL. Tk main thread and one background loop communicate through bounded queues. Native UI exposes export/fingerprint, enrollment window, revoke and Stop; no device control yet.
- [ ] Rerun identity tests to PASS. Record actual dependency decisions and unresolved browser checks in ADR/report. No silent trust installation.
- [ ] Commit as `feat: add local hub certificate identity`.

## Task 3: Bounded HTTPS enrollment and sessions (H1B, no device)

**Files:** create `hub/sessions.py`, `hub/server.py`, `tests/hub/test_sessions.py`, `tests/hub/test_server.py`; extend `hub/main.py`, `hub/local_ui.py`.

**Interfaces:** `Sessions.open_window() -> None`, `request(label: str) -> tuple[str, str, str]` (pending token, CSRF, comparison code), `approve(pending_id: str) -> None`, `complete(pending_token: str, csrf: str) -> str | None`, `authenticate(token: str) -> str`, `csrf(session_id: str) -> str`, `revoke(session_id: str) -> None`. Tokens remain internal and never enter logs. `create_app(sessions: Sessions, controller: Controller | None) -> aiohttp.web.Application`; absent controller exposes no device action.

- [ ] Write session tests for four pending/120seconds, exact pending approval, concurrent completion once, response-loss orphan/revoke recovery, reload restart, eight controllers, persisted token hashes,30-day expiry and CSRF stability across tabs. Use controlled clocks and ephemeral SQLite.
- [ ] Write server tests for exact routes/Host/Origin, absent/null/foreign Origin, no CORS, no-store responses, cookie attributes, CSRF, traversal,4KiB body/frame rejection, malformed/duplicate JSON keys, unauthenticated upgrade and3second hello deadline.
- [ ] Run the two test files with unittest; verify RED before implementing. Implement spec sections6–7 using aiohttp and SQLite transactions; owner requests enter only through the native message queue.
- [ ] Prove the32 accepted-connection cap includes TLS handshakes,5second handshake/header deadlines,8 authenticated sockets, disabled compression and bounded writes. A request semaphore alone fails this test; if the selected stack cannot enforce limits with maintained/native mechanisms, stop H1B and document the dependency decision rather than inventing a new TLS stack.
- [ ] Run both test files to PASS and review auth/data persistence paths; commit as `feat: add owner-approved controller enrollment`.

## Task 4: Lease and command lifecycle with a no-device recorder

**Files:** create `hub/controller.py`, `tests/hub/test_controller.py`; extend `hub/server.py`, `hub/local_ui.py`.

**Interfaces:** `Controller.take_control(session_id: str, socket_id: str) -> str`, `admit(session_id: str, socket_id: str, message: dict) -> dict`, `stop(reason: str) -> None`, `socket_closed(socket_id: str) -> None`. Async dispatch callback `dispatch(kind: str, fields: dict) -> None` is supplied by composition; test recorder only, no projector fallback. A synchronous stop callback fences underlying input before cleanup is scheduled.

- [ ] Write deterministic tests for socket/session/generation ownership, exact-next sequence, mismatched heartbeat/release/connect, observer close, old socket after new lease, revocation and timed expiry on live sockets.
- [ ] Add delayed dispatch/connect tests: Stop during setup, late completion disposal, old cleanup after successor, queue overflow,200ms slow writes,1second heartbeat/3second expiry,2second idle/15second total drag timeout. Assert no post-fence ordinary sends and cleanup-only exceptions.
- [ ] Run `.venv/bin/python -B -W error::ResourceWarning -m unittest discover -s tests/hub -p test_controller.py -v`; confirm RED.
- [ ] Implement bounded closed-message validation, eight-control queue/one movement slot,30Hz dispatch and60frames/sec burst120, with per-session aggregation. Stop is lease-independent for enrolled controllers; connect/input/heartbeat/release require the exact current owner. Generation fences cover every awaited setup result.
- [ ] Rerun controller and server tests to PASS; commit as `feat: enforce controller lease and stop lifecycle`.

## Task 5: Installed PWA and actual browser trust gate

**Files:** create `web/index.html`, `web/remote.js`, `web/gestures.js`, `web/style.css`, `web/manifest.webmanifest`, `web/sw.js`, `web/icons/`; `tests/web/gestures.test.mjs`, `tests/web/lifecycle.test.mjs`; update static allowlist in `hub/server.py`, `docs/gates/h1b-report.md`.

**Interfaces:** `createGestures(send, clock)` returns handlers for pointer down/move/up/cancel and reset; `send(kind, fields)` passes only closed commands into the remote connection manager. The manager owns socket sequence/lease and never sends controls offline. Plain JS modules, no build framework.

- [ ] Write Node tests for tap vs300ms hold/6CSSpixel motion, fractional delta accumulation and±256 clamps, mouse hover/drag, second finger release-before-scroll and suppressed trailing tap, cancellation and sensitivity. Add lifecycle tests for hidden/offline state, missed heartbeat, busy lease and protocol mismatch.
- [ ] Run `node --test tests/web/*.test.mjs`; require RED. Implement responsive keyboard-accessible remote, explicit scroll buttons, visible state, enrollment and Take control/Stop. API and commands are never cached or background-synced.
- [ ] Test static-only service-worker cache, update refusal during drag, stop/reload on version mismatch and disabled offline controls. Run Node tests and affected server tests to PASS.
- [ ] With owner participation, verify certificate transfer/full fingerprint comparison and trust installation on actual iPhone and Android. Install before enrollment; test Safari/Chrome browser and installed app storage separately, plus laptop Safari/Chrome. Record exact versions and positive/negative certificate, renewal, migration/removal, expiry/revoke and stale-worker results. Gate stays open if any required ceremony cannot be demonstrated.
- [ ] Mark H1B PASS only with complete evidence and security review; commit as `feat: add installed local remote interface`.

## Task 6: Integrate the proven device adapters (H2)

**Files:** migrate `experiments/hub_remote/pointer.py`, `tv_remote.py` and their tests to `hub/device/` and `tests/hub/`; update experiment imports/runbook, `hub/main.py`, `hub/controller.py`, `hub/identity.py`; create `docs/gates/h2-report.md`.

**Interfaces:** Preserve `start(serial, hardware=True)`, `HardwarePointer.move/press/release/tap/scroll/request_stop/close` and `TVRemote.start_pairing/finish_pairing/reconnect/key/launch_netflix/disconnect/close`. Extend identity persistence at the adapter's existing credential owner; never relax enrolled-peer validation. Composition maps closed commands to these methods.

- [ ] Require H1B PASS and accepted H1A policy first. For protected transport, obtain a concrete reviewed adapter plan from passing evidence; this plan does not invent one. For an approved beta exception, reuse only the exact H0 configured legacy connection policy.
- [ ] Add failing integration tests for persisted Remote v2 peer binding, Keychain refusal, bounded worker queue, both-channel stop, late connection completion and initial neutral-button report. Update all H0 callers/imports when moving code; retain diagnostic SDK backend outside product selection and preserve license attribution.
- [ ] Migrate once; keep transport ownership/cleanup in existing adapters. Implement explicit reconnect with new lease, no replay, no target discovery/substitution, no automatic ADB restart. Product cannot import experiments.
- [ ] Run all Python hub/H0 tests plus Node tests to PASS. Review the complete migration, not only new lines.
- [ ] Repeat H0 functional checks via each required browser. Test active-drag Stop, browser suspension, process kill, host sleep, Wi-Fi loss, restart and physical remote. Record observed release latency and any residual held state; persistent held input blocks H2. Do not substitute desktop tests for phone behavior.
- [ ] Update capability/status/architecture evidence and commit as `feat: connect local PWA to verified TV controls`.

## Task 7: Distribution and documented recovery (H3)

**Files:** `README.md`, `BACKLOG.md`, `docs/progress.md`, `docs/capabilities.md`, `docs/architecture.md`; create `docs/setup-hub.md`, `docs/gates/h3-report.md` and packaging ADR after selecting the smallest verified macOS distribution method.

- [ ] Verify clean-machine installation/start/quit, private file permissions, dependency/server hashes, local-only binding and uninstall/root-retirement instructions. Signing/notarization needs actual credentials and evidence; do not claim an unsigned development launch is distribution PASS.
- [ ] Document certificate/clock/address migration, Keychain denial, session loss/revoke, ADB recovery, Stop, host sleep and tested limits. State the actual D1 policy prominently. Windows hosting and added TV features stay in backlog.
- [ ] Run secret/release-boundary scans and relevant suites once; execute required whole-change Codex review including untracked files and callers. Fix only concrete in-scope defects, rerun affected tests, record other dispositions.
- [ ] Commit related changes in small batches and create a reviewed feature-branch PR using existing authorization; attach the PR to the task. Do not push directly to main.

## Self-review

Spec coverage:1–4 → Tasks1/6/7;5 → Task2;6 → Task3;7 → Tasks3/4;
8 → Task5;9–10 → all gate reports. Five review-focus cases have tests in their
owning tasks. No product implementation or gate PASS is asserted by this plan.
