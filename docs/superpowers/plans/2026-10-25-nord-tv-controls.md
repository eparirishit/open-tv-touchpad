# Nord TV Controls Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for bounded workers and independent review. Main owns integration and device operations. Existing owner authorization covers execution; no repeat plan-approval pause.

**Goal:** Unified Nord remote: verified TV keys over TLS plus existing Bluetooth mouse.
**Architecture:** One Activity Run owns both channels and the fixed service deadline.
Native TLS/Keystore and a separate durable TV record authenticate a closed Remote v2
client; presentation only binds those operations. Preserve the existing HID state machine.
**Tech Stack:** Java17/native Views, Android API31+, platform TLS/Keystore, protobuf Java lite.
**Spec:** ../specs/2026-10-25-nord-tv-controls-design.md

## Global Constraints

- Existing linked worktree and `nord-tv-controls` branch; no new checkout or release variant.
- N3a must pass before real TV integration: no TV input in the loopback fixture.
- All protocol/trust/lifetime values in the approved spec are binding; 300s run,
  TLS1.2/1.3, RFC1918 fixed6467/6466 runtime, exact DER pin, no secret logging.
- Source/app artifacts and owner observations are separate. Review before device update.
- No background control, automatic retry, custom crypto/ASN.1/protobuf parser, or ADB input.
- Reuse existing AndroidX/JUnit. Protobuf dependency only after source/license/hash/advisory review.
- Workers have disjoint writes; main serializes Gradle/device/Git integration.

## Review Focus

1. Broken enrollment must block both pairing and combined Connect, not select HID-only (task3).
2. A stopped worker cannot enroll or submit after a newer run starts (task4).
3. Initial Bluetooth system-pairing pause exception must survive, but no TV operation may survive pause (task5).
4. Write timeout must close I/O even while the writer is blocked; stale timeout cannot close newer socket (task4).
5. Native Keystore client cert must actually authenticate with exact-peer TLS; factory success is insufficient (task1).

## Task 1: Native identity and TLS gate

Files: new `experiments/hid_probe/src/main/java/dev/opentvtouchpad/hidprobe/TvIdentity.java`,
`TvTls.java`, and `src/androidTest/java/dev/opentvtouchpad/hidprobe/TvTlsTest.java`.
Main owns manifest/version build config and device runner.

Interfaces: package-private `TvIdentity.create(String alias)` and `load(String alias)`
return `KeyStore.PrivateKeyEntry`; `delete(String alias)`. Create must refuse an
existing alias. Native RSA2048 certificate, signing SHA256/384/512 PKCS1/PSS and the
provider-required raw-RSA signing permissions (no encryption/decryption purpose); no exportable fallback. `TvTls.clientSocket(entry, peer)` returns an unconnected
SSLSocket with a single identity, native trust + exact leaf pin and only TLS1.2/1.3
enabled. Separate `TvTls.pairingSocket(entry)` has bootstrap-only trust and no
control caller. SSLContext construction stays private.
`TvTls.certificate(byte[] der)` uses native CertificateFactory with bounded input.
Workers may refine signatures within the same ownership if documented before integration.

- [x] Write native tests first, observe compile failure against missing helpers.
- [x] Implement identity creation/loading/deletion, no key bytes exported, valid
  self-signature. Use native parser/trust factory and narrowly scoped key manager.
- [x] Loopback mutual-TLS test with ephemeral fixture identities under test-only
  aliases: correct pin passes and verifies client cert; wrong/expired/child peers
  fail; pairing bootstrap and control policy are separate; close unblocks I/O.
- [x] Main adds INTERNET/version8 only to debug diagnostic, builds all existing
  unit/native tests, independent review before installing on stopped Nord.
- [x] Execute reviewed TLS fixture on emulator then Nord. No retained enrollment
  access in test code; main compares enrollment before/after. Delete fixture aliases,
  close sockets, remove only instrumentation APK. Record exact evidence. On native
  incompatibility stop this gate, preserve existing0.7 and report without weakening.

## Task 2: Locked wire format and pairing transcript

Files: `experiments/hid_probe/src/main/proto/{polo,remotemessage}.proto`, generated
Java lite source, `TvWire.java`, `TvPairing.java`, tests; `gradle/libs.versions.toml`,
probe build file, dependency ADR and reproducible generation script.

Interfaces: `TvWire.read(InputStream)` -> byte[] with64KiB/five-byte-prefix bounds;
`write(OutputStream, byte[])`; generated protobuf types own message parsing.
`TvPairing` owns strict request/options/config/secret state transitions, native
SHA256 standard transcript over unsigned RSA fields and code bytes. No sockets.
Command enum owns exact allowed keys and constant Netflix URI.

- [x] Fetch exact v0.3.2 schemas/license and resolve immutable upstream commit;
  lock protobuf-javalite/protoc compatible supported versions, hashes and licenses;
  advisory query. Record actual findings, not assumed clean status.
- [x] Write failing tests for fragmented/malformed/truncated/oversized frames,
  signed/unsigned RSA encoding, malformed/wrong code, unexpected/duplicate pairing
  replies, command enum and fixed URI. Use independent synthetic vectors.
- [x] Implement bounded framing plus generated parser recursion16; no generic
  arbitrary-key/URL command API. One handshake phase accepts one expected reply.
- [x] Verify unit tests and reproducible generation; independent scoped review.

## Task 3: Durable TV enrollment

Files: `TvEnrollment.java`, Android adapter `TvEnrollmentFile.java`, pure storage tests.
Interfaces: record (format1, locator, alias, client-cert fingerprint, exact peer DER),
state ABSENT/VALID/UNUSABLE; `save`, `forget`, `changeLocator` use AtomicFile/readback.
Key presence/fingerprint validation supplied by native identity adapter; no HID file reuse.

- [x] Test <=16KiB parsing, version/schema/address validation, corrupted/missing-key
  records, failed read/write/readback, explicit empty tombstone and process failure latch.
- [x] Implement one fixed app-owned alias and separate noBackup record. Forget
  tombstones/readbacks before key deletion, unknown states cannot become ABSENT.
- [x] Test locator changes preserve peer pin; no candidate adoption or silent key
  replacement; test delayed commit cannot follow revoked enrollment in integration.
- [x] Review before task4 uses storage; no credentials in tests/logs/artifacts.

## Task 4: Bounded remote connection

Files: `TvRemoteClient.java`, pure admission/attempt helper if needed, local TLS tests.
Consumes identity/enrollment/wire. Interface: per-run beginPair(locator), finishPair(code),
connect(record), submit(Command), stop(); callbacks carry immutable owner and attempt.
Expose separate pairing/ready/closed/error status, never raw exceptions or peer data.

- [x] Test cancellation before/after socket creation, keygen, handshake and commit;
  retained exact pin, pairing/control cert mismatch, deadlines and no send before ready.
- [x] Implement one socket, bounded reader/writer/watchdog, one pending user command,
 250ms age/10Hz, bounded protocol replies8, frame admission100/s burst200; platform
  handshake12s/exchange10s/code120s/write2s/idle30s within existing run deadline.
- [x] Test blocked write independent close, stale timeout, Stop clears queue, new
  owner cannot start until worker termination, no post-Stop trust resurrection.
- [x] Review transport and credential mutation paths together; no real TV operations yet.

## Task 5: Unified native screen and lifecycle

Files: ProbeActivity, NativeRunGate, RemoteScreen, PairingGuardService wording/deadline
access only; NativeRunGateTest, NativeStopTest plus StopCleanupHarness.java.template,
RemoteScreenTest and appropriate new combined-state tests.

- [x] Test combined gating at actual submit boundaries, TV-active pause and existing
  initial-Bluetooth-pair exception, service expiry before delayed timer, lost either
  channel stops both, Stop cleanup attempts every resource despite exceptions.
- [x] Wire separate TV enrollment/pairing run; then TLS-first/HID-second Connect.
  Keep HID-only only for ABSENT TV enrollment. Add TV native inputs and masked inline
  code field; state-saving/autofill off; clear on every terminal event.
- [x] Add real D-pad/OK/Home/Back/volume/play-pause/Netflix controls, split channel
  status and accessible labels. DOWN/click cancels pad. Keep fixed Stop/Close.
- [x] Validate portrait/landscape/enlarged text/IME footer and no false enabled controls.
  Run full unit/lint/native suites, inspect packaged permissions/backup/export/release
  exclusion; whole-change independent security review and scoped re-review of fixes.

## Task 6: Install and owner hardware acceptance

Files: probe README, progress/capabilities/BACKLOG, N3 report, approved architecture.
- [x] Commit reviewed implementation; preserve0.7 APK and record source/hash. Verify
  unique authorized stopped Nord; same-signature update preserves both stores/bond.
- [ ] Owner TV-code pairing in app (never chat), saved reconnect/process restart,
  TV buttons/Netflix20s coexistence, pointer/tap/scroll and profile selection.
- [ ] Owner Stop/physical-remote, pause/return/expiry, controlled Wi-Fi/Bluetooth loss.
  Never label local writes or automated fixtures as observed projector actions.
- [ ] Record each pass/failure/NOT RUN; no N3 PASS while required checks remain.

Execution: owner has already chosen subagent development and approved this design;
continue within scope. Native feasibility failure is a real stop gate, not permission
for an unreviewed alternate security architecture.
