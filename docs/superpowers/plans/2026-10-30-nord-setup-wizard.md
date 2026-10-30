# Nord Guided Setup Wizard Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for the
> owned units; parent implements the coupled runtime integration. No new worktree.

**Goal:** Replace repeated manual setup runs with the approved resumable wizard.
**Architecture:** Native SetupScreen renders immutable SetupState snapshots from
RemoteRuntime. Existing native run/cleanup ownership remains authoritative;
separate Bluetooth-enrollment and TV-pairing intents handle the internal handoff.
**Tech Stack:** Existing Java17/API31–36 native views, AtomicFile and JUnit.
**Spec:** ../specs/2026-10-30-nord-setup-wizard-design.md (owner approved “go ahead”).

## Global Constraints

- Same app ID/signer/credential formats, no new dependency/permission/transport.
- Setup never grants remote input. Normal Connect keeps both-channel policy.
- Native cleanup proof, failed latch, pairing codes/TLS and bounded windows remain.
- One visible Start setup; Pair TV prepares its own internal run after cleanup.
- Persist only completed configuration and validated non-authoritative address.
- Keep OTP visible/native and unsaved; preserve the exact comparison label.
- Reviewed stopped in-place update only; preserve both enrollment bytes.
- N4 readiness and public release remain separate. Do not unpair a test device
  without owner preparation; no gate gestures through ADB.

## Review Focus

- Non-prefix completion (TV paired but Bluetooth forgotten) must preserve TV trust.
- A stale permission/discoverability result must not start a newer/hidden run.
- Native service destruction/rebind must finish the real cleanup barrier.
- Re-pair/Reset or draft-write failure must not silently erase unrelated trust.
- Code/keyboard/large-font/rotation must preserve privacy and reachable escape UI.

### Task 1 — configuration model and non-authoritative address

**Files:** Create main SetupState.java, SetupAddressStore.java, SetupAddressFile.java
and focused SetupStateTest.java, SetupAddressStoreTest.java; native file fixture
SetupAddressFileTest.java. All Java paths are under experiments/hid_probe/src/
{main,test,androidTest}/java/dev/opentvtouchpad/hidprobe/.

**Interfaces:**
- SetupState.Page = OVERVIEW, BT_START, BT_WAIT, BT_COMPARE, BT_CLOSING, BT_DONE,
  ADDRESS, TV_START, TV_CONNECTING, TV_CODE, TV_CLOSING, TV_ERROR, DONE, MANAGE,
  CLOSING, RECOVERY.
- SetupState.Action = START, BT_PAIR, BT_CONFIRM, BT_MISMATCH, NEXT, SAVE_ADDRESS,
  PAIR_TV, CONFIRM_TV, RETRY, CHANGE_ADDRESS, BACK, CANCEL, MANAGE, OPEN_REMOTE,
  REPAIR_BT, REPAIR_TV, FORGET_BT, FORGET_TV, APPEARANCE, BATTERY_SETTINGS.
- SetupState.Status = DONE, NEXT, TO_DO.
- SetupState.Snapshot(Page page, boolean bluetoothSaved, boolean addressSaved,
  boolean tvPaired, String address, String message, String diagnostic,
  long remainingMillis, boolean busy, boolean enabled, long generation,
  boolean requestDiscoverable). Helpers complete(), anyComplete(), firstMissing(),
  status(int step) for steps1–3. firstMissing yields BT_START/ADDRESS/TV_START/MANAGE.
- SetupAddressStore(EnrollmentStore.Disk), address(), failed(), save(String).
  Existing TvEnrollment.validAddress is the sole address validator.
- SetupAddressFile(File noBackupDirectory) implements EnrollmentStore.Disk;
  owns only no_backup/setup-address. Format ASCII 1\n + valid address, max17bytes;
  bound reads to18bytes to reject trailing data. Missing file is empty; I/O failure
  is distinct. Verified atomic write/readback. Existing credential files untouched.

- [x] Add failing real-model tests for all8 completion combinations, first missing
  and per-item chips; progress must not authorize input.
- [x] Add store tests: missing/corrupt/oversize/version/non-private/port/leading-zero,
  save/reload, failed write/readback and successful explicit retry. No secrets/logs.
- [x] Implement only these classes, native atomic-file test and run focused tests.
- [x] Parent task-scoped spec/quality review and coherent commit.

### Task 2 — real wizard operations and native presentation

**Files (parent):** RemoteRuntime.java, ProbeActivity.java, RemoteConnectionService.java,
TvRemoteClient.java (deadline observation only), existing actual-method harnesses
and native service tests. **Files (UI worker):** SetupScreen.java, RemoteScreen.java,
RemoteScreenTest.java, DailyUseScreenTest.java, new SetupScreenTest.java.
Parent and UI worker have disjoint write sets; do not run Gradle concurrently.

**Interfaces:**
- RemoteRuntime.Snapshot appends SetupState.Snapshot wizard.
- Runtime/binder setupAction(long token, SetupState.Action action, String value).
- Runtime/binder takeDiscoverabilityRequest(long token, long generation) returns
  boolean; discoveryResult(long token,long generation,int grantedSeconds).
- TvRemoteClient.codeDeadlineMillis() returns0 unless current code entry is live,
  otherwise min(phaseUntil, deadline.getAsLong()); no deadline mutation.
- RemoteScreen exposes final SetupScreen setupView; setupView has root, primary,
  secondary, back, address, code and management controls; setActionListener(
  BiConsumer<SetupState.Action,String>), render(SetupState.Snapshot), pauseControls(),
  clearCode(), applyPalette(boolean dark). Keep Remote/Touchpad and display entry.
- SetupScreen action values are typed; APPEARANCE/BATTERY_SETTINGS/OPEN_REMOTE are
  Activity/View navigation, all native configuration mutation goes through binder.

- [x] Parent writes failing actual-runtime tests for setup intent/phase handoff,
  registration→TV pairing, no setup input epoch, stale callbacks/results and real
  cleanup-gated navigation. Keep current visibility/Stop regressions meaningful.
- [x] UI worker writes native view/flow/privacy/layout expectations before replacing
  old setup tabs. Observe expected red where feasible against old UI.
- [x] Runtime owns ephemeral wizard page, cleanup target and per-run setup intent.
  Reuse start/register/pair/confirm/forget helpers. Bluetooth enrollment runs use
  BT-only session mode even if TV is saved, without mutating its store. Only this
  setup intent bypasses TV connection for temporary enrollment; normal Connect
  remains unchanged. Callback neutral/completion starts safe closing, never input.
- [x] Bluetooth Start pairing requests permission if needed, waits registration,
  asks Android visibility once per generation. Start ring only from granted
  result; cap120s/earlier setup expiry. Clear deferred requests on cancellation/
  invalid departure; keep existing bounded system-dialog exception.
- [x] BT closing waits actual NativeRunGate availability including Service.onDestroy;
  next native run starts only on explicit Pair TV, not on screen rebind/timer.
- [x] Pair TV single action starts/registers the existing TV-only pairing run, then
  consumes its generation-bound pending pair request while attended. Show real
  code deadline, clear code on loss, preserve failure stage/retry/changed locator.
- [x] Draft address write gates address Done; credential locator wins over draft.
  Re-pair/Forget are confirmed explicit actions; safely stop/clear only affected
  trust. Draft-save failure cannot prevent explicit credential revocation.
- [x] UI worker matches supplied prototype’s overview, stepper, states, timelines,
  sheets and summaries. Reuse PairingCodeField; no hidden web input, fake number,
  canned countdown/completion, prefilled sample IP or false readiness checks.
- [x] Native view tests cover portrait/landscape/font/IME/TalkBack actions and all
  step error/closing/recovery states. Parent actual tests cover lifecycle admission.
- [x] Combined unit/native/lint/build check, task-scoped independent review; fix
  actionable criteria violations and re-review affected code before commit.

### Task 3 — evidence, complete review and safe delivery

**Files:** build.gradle.kts version22/0.22-guided-setup-candidate; progress,
capabilities, architecture, backlog, experiment README and new gate report.

- [x] Confirm same signer, protected trust/store/command/report sources, permission
  and backup rules; no new runtime I/O endpoint or dependency. Update docs to actual
  implemented flow and exact verified artifact; keep hardware claims pending.
- [x] Run full affected module unit/native suites, lint and both APK builds. Use
  existing emulator only for native fixtures; no injected physical gate gestures.
- [x] Complete independent whole-change and required Codex review relative to
  cc416a3 plus all untracked files/callers, using approved spec; scoped fix review.
- [x] Commit/push feature branch and create/attach draft PR; main not changed.
  PR11 is merged; its merge commit has the same tree as cc416a3. Target main and
  preserve published history.
- [x] If authorized Nord endpoint is unavailable, request only the current main
  Wireless debugging IP/port. No install before review and stopped proof. Verify
  exact APK, same signer and byte-identical enrollments across install/launch.
- [x] Owner test of non-destructive Review setup/Appearance/saved Connect/Home/
  pointer/scroll/Stop, including teardown-completed status, without re-pairing.
- [ ] Fresh wizard pairing/mismatch/reset hardware trial only after the owner
  explicitly prepares the test bonds. Do not claim wizard/N4 hardware PASS from
  software fixtures or the non-destructive acceptance check.
