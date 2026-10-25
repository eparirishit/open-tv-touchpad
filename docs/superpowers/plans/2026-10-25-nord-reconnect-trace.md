# Bounded Nord reconnect trace

> Execute with the user's existing subagent implementation/review preference.

**Authorization:** The owner said “continue” after the proposed small privacy-safe
connection trace and single restart reproduction. This is diagnostic instrumentation
of the accepted probe, not a reconnect fix or a change to the trust contract.

**Goal:** Distinguish native registration/connection callback ordering, request
rejection, foreground loss and neutral/input submission failure during the first
saved connection after process restart.

**Evidence:** 0.3 source `7643aa8` passed saved reconnect and actual pointer input
after restart on retry. Its first attempt reported a selected disconnect followed
by completed teardown. Vendor logs yielded no relevant retained markers. Forget
with OS bond retained passed; no app enrollment currently remains. H1A stays NOT
PASSED. Do not infer a specific root cause or repeat unchanged basic HID checks.

## Diagnostic contract

- Keep the state machine, timers, callback ownership, authentication, explicit
  Connect, neutral-first input, descriptor and report bytes unchanged.
- Add a pure Java `ProbeTrace` with a closed event enum. It produces only a fixed
  format version, per-run sequence number, elapsed milliseconds and enum names.
  No free-form event strings or payloads, identifiers, names, addresses, pairing
  values, coordinates, reports, exception text or absolute time.
- Emit at most 64 lines per run including one final truncation marker, through
  one fixed Android log tag `OpenTvHidTrace`. No app-owned trace file, export UI,
  network permission, external dependency, continuous capture or global log clear.
  Android's bounded log buffer may retain diagnostic lines after app closure;
  document this, and treat absent/truncated capture as incomplete evidence.
- The existing run owns the trace. Old callbacks still obey their existing owner
  checks. Trace emission cannot authorize or drive any operation. Keep it small:
  no logger framework, listener registry, new service or state abstraction.
- Cover run start (saved/empty), actual registration result, explicit connect
  request/return, selected versus other peer connection states, rejection before
  explicit admission, neutral submission result, pause, stop, teardown completion,
  service/profile loss and deadlines. Record protocol mode only as fixed enums.
  Optionally record move submission success/failure without direction/count/delta.
  Preserve the operation's existing ordering and never repeat native calls merely
  to log them. Bind event names to actual observations, not inferred explanations.
- Version the diagnostic APK as 0.4-diagnostic/versionCode4. No new production
  capability or protection claim follows. All live outcomes need this new hash.

## One implementation task

Files: `experiments/hid_probe/src/main/java/dev/opentvtouchpad/hidprobe/ProbeTrace.java`
(new), `ProbeActivity.java`, corresponding `ProbeTraceTest.java`, module
`build.gradle.kts`, and README. Main owns progress/gate/backlog records.

- [x] Write/run failing unit tests for fixed-format enum-only output, monotonic
  nonnegative elapsed time, per-instance reset, bounded output/truncation and
  event-order preservation. Capture red evidence before implementation.
- [x] Implement the small trace and observational call sites. Do not alter
  `ProbeSession`, `NativeRunGate`, `EnrollmentStore` or the service lifecycle.
- [x] Run the full probe unit suite, lint and debug build with the existing offline
  Gradle/SDK toolchain. Inspect packaged permissions and debug-only isolation.
- [x] Independently review all changes/new files against this contract and the
  accepted retained-bond spec, especially privacy, output bounds and unchanged
  input/native-call ordering. Fix only demonstrated in-scope findings.
- [x] Commit the reviewed code; record source/APK hash and install only on the
  uniquely manufacturer/API-verified Nord. No automatic pairing/input/radio changes.

## Single live capture

Because Forget passed, one fresh compared enrollment is needed first; never adopt
the remaining OS bond as app enrollment. After that, Stop and completed teardown,
force-stop/reopen, Start then Connect saved projector once. Ask the owner to leave
the failure visible and avoid retrying until the fixed-tag trace is collected.
On success, confirm actual left/up pointer movement (right-edge ambiguity occurred
before). Capture only validated fixed-tag lines from the current probe process;
never clear other apps' logs or save a raw log dump. No automatic test input.

If the failure does not reproduce, record that narrow result and leave the earlier
failure open. If it does, use its trace to select a specific regression/fix; do not
ignore disconnects or add automatic retries to manufacture a PASS.

## Execution checkpoint

The owner confirmed fresh compared enrollment in 0.3 and stopped status with
native teardown complete. The Nord is uniquely verified online as OnePlus/API31.
Implementation has 53 passing unit tests; lint has zero errors and the existing
20 UI-localization/SDK-age warnings; debug assembly passes. Main reran these checks
and verified the packaged version4 and unchanged four Bluetooth/service permissions
(no INTERNET). Independent review found no findings. Source `d0a8c44` was committed
and version4 installed using a data-preserving update; installed version is verified.
Foreground launch was not confirmed, and the bounded crash check found no target
crash. The owner subsequently completed the single manual restart attempt and
confirmed “Connected; left/up moves the pointer”. Current-process/latest-run
capture contains contiguous events1–33 without truncation; explicit connection
completed after1325ms and neutral/movement submissions were accepted. This trial
passed; the earlier disconnect did not reproduce. No root cause or behavior fix
is inferred. The bounded diagnostic task is complete. See H1A for the APK hash,
event timings, preserved first-attempt failure and remaining gate limitations.
