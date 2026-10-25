# Nord retained-bond diagnostic implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development. The owner
> already selected this execution method and accepted the B design.

**Goal:** Remember one compared projector; reconnect only after explicit owner action.
**Architecture:** Extend the existing probe gate and native Bluetooth adapter.
Keep enrollment separate from active-run authorization; use native app-private
no-backup storage, with no custom crypto or new dependency.
**Tech stack:** Java17, Android public API31+, existing Gradle/JUnit4.
**Spec:** `docs/superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md`.

## Global constraints

- Debug-only probe, no INTERNET permission, no background input or product backend.
- One attempt per explicit Start/run; fixed five-minute deadline; Stop ends service.
- Authentication/encryption authority is the native Bluetooth stack. Saved address
  selects its current bond, never independently proves original-key continuity.
- First enrollment requires the existing fresh compared-numbers ceremony. Existing
  unrecorded bonds cannot be adopted. Any observed selected-peer re-pairing or bond
  departure revokes enrollment. No hidden APIs, automatic pairing or bond removal.
- Fail closed on storage, permission, native-call and teardown ambiguity. No replay.
- No hardware input via ADB; install only after review. H1A stays NOT PASSED.

## Review focus

- Saved peer plus unsolicited native connection must never authorize a report.
- Replacement pairing must revoke even after Stop while the screen/process observes it.
- Late old callbacks must not enable or tear down a newer authorized run.
- Failed persistent save/forget must not leave a UI promise that differs from disk.
- Successful CONNECTED alone must not enable input before neutral submission succeeds.

### Task 1: Implement and test the bounded retained-enrollment probe

**Files:** Existing `experiments/hid_probe/src/main/java/dev/opentvtouchpad/hidprobe/`
`ProbeSession.java`, `ProbeActivity.java`, `PairingGuardService.java` if needed;
one small enrollment store in that directory; corresponding JUnit files under
`src/test/java/dev/opentvtouchpad/hidprobe/`; module manifest/XML backup rules,
`build.gradle.kts` and `README.md`.

**Interfaces:** Preserve the existing generation-gated session API where it fits.
Add explicit saved-peer selection/Connect admission and neutral-success state in
the owning gate. Store only a validated single peer and fixed format version using
native atomic file handling in `getNoBackupFilesDir()`; missing/corrupt records
yield no enrollment. Use a pure codec/policy seam only where needed for real tests.
Never serialize test/runtime authorization. Synthetic peers only in fixtures.

- [x] Write failing tests for saved selection without explicit Connect; reconnect
  using a saved peer after Stop/new run; wrong-peer/old-generation callbacks;
  neutral-first admission; re-pair/bond loss invalidation; record corruption,
  unsupported version and failure to save/forget. Preserve useful existing tests.
- [x] Run the narrow unit suite and record the expected red failures.
- [x] Implement the minimal gate/store and native wiring. Fresh comparison saves
  enrollment durably before initiating connect; no save success means no connect.
  Explicit Connect rechecks current bond. Observe invalidation while Activity exists,
  including stopped state; absent-process changes remain the accepted OS boundary.
- [x] Give Start, Connect saved projector, Forget and Stop distinct UI meanings.
  Keep one attempt per run. New Start cannot precede completed old native teardown;
  lack of completion leaves control disabled with a useful recovery message.
  Use immutable run callback ownership, not callbacks reading a mutable new epoch.
  Preserve physical-remote usability and the immediate press/release click only.
- [x] Set versionCode3/versionName0.3-diagnostic; document first enrollment versus
  saved reconnect, invalidation, trust limitation and live tests still pending.
- [x] Run `ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./gradlew --offline
  --console=plain -PhidProbe=true :experiments:hid_probe:testDebugUnitTest
  :experiments:hid_probe:lintDebug :experiments:hid_probe:assembleDebug`.
- [x] Independently review the full module diff and connected callers; fix in-scope
  findings and rerun affected checks. Commit only this coherent diagnostic change.

### Task 2: Record artifact and prepare the live reconnect check

**Files:** `docs/progress.md`, `docs/capabilities.md`, `BACKLOG.md`,
`docs/gates/h1a-report.md`, accepted spec/AGENTS and this plan.

- [x] Record acceptance, exact source commit/APK SHA256, verification and remaining
  unknowns. Inspect merged permissions/backup exclusions and debug-only isolation.
- [x] If the unique verified Nord is online, update only the authorized probe APK
  and launch it; otherwise provide the APK and ask for the minimum reconnect input.
  Do not restart ADB, alter bonds/radios or send input. Never print device addresses.
- [x] Ask for one fresh verified enrollment in the updated app, then Stop → Start
  → Connect saved projector without forgetting the bond. Record only observed
  results; process-reopen and revocation checks follow after this passes.

## Execution record

Plan follows the accepted design; no additional trust decision is introduced.
User execution preference retained: subagent implementation and independent review.

Local verification: 48 unit tests pass; debug build passes; lint reports 0 errors
and 20 diagnostic-localization/SDK-age warnings. Packaged permissions match the
four allowed Bluetooth/service permissions, with no INTERNET. Independent review found no actionable findings. Source `7643aa8` was committed;
version0.3 was installed and its Activity confirmed resumed on the verified Nord.
The user confirmed “Reconnect works; physical remote works” for the first fresh
enrollment/Stop/saved-reconnect trial. The subsequent restart trial (2026-10-25)
initially disconnected, then connected on retry. The user confirmed enabled left/up
buttons move the pointer in that session. Saved enrollment and working reconnect
after restart are established on retry, not first-attempt reliability. The next
Stop → Forget → Start check with the OS bond retained passed by user observation:
no saved projector, Connect and movement disabled. Explicit app revocation and
bond-only input rejection are established; OS bond-change revocation, durable
Forget across process death and fault cleanup remain unverified. Artifact and
first-attempt failure evidence remain in the H1A report; no code fix was inferred.
