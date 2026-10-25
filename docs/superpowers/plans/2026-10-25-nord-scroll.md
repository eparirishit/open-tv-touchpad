# N2 Nord scrolling implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Add four discrete scroll buttons and safe fresh-pairing migration.
**Architecture:** MouseReport owns the sole five-byte format; EnrollmentStore
rejects old enrollment for the changed descriptor. The current Activity/Run
owns native admission, touch cancellation and per-run scroll throttling.
**Tech Stack:** Existing Java17/Android31+, JUnit/Gradle; no new dependencies.
**Spec:** `docs/superpowers/specs/2026-10-25-nord-scroll-design.md`.

## Global constraints

- Report ID1, five bytes: button bits, X, Y, wheel, pan. Three buttons remain.
- No legacy report fallback, network permission, held drag or two-finger scrolling.
- One scroll step per button activation, at most one per100ms/run; no queue/replay.
- Cancel pad on scroll-button down and admitted click, including throttled clicks.
- Version1 record stays untouched/unselected; version2 is written only after fresh
  verified pairing or explicit Forget. Same19-byte layout, existing failure latch.
- Same app ID, five-minute foreground-only run, native ownership and trust policy.
- Keep preserved0.5 APK. No device update until code review and owner migration prep.

## Review focus

- Stale three-byte neutral/reply/release must not survive the descriptor change.
- Old saved bond cannot bypass fresh comparison under version2.
- Button-down must cancel a pad tap/frame before button-up/click can occur.
- Throttled, disabled and throwing scroll actions cannot queue input or resume it.
- Fixed controls remain reachable on the actual Nord in portrait/landscape.

## Task 1 — Mouse report format (agent)

Files: new `MouseReport.java` and `MouseReportTest.java` in the existing probe
main/test Java packages. No other writes.

Interfaces: package-private `MouseReport`, constants `ID=1`, `SIZE=5`; enum
`Direction { UP, DOWN, LEFT, RIGHT }`; static `byte[] descriptor()`, `neutral()`,
`move(int dx,int dy)`, `clickDown()`, `scroll(Direction)`; static
`boolean acceptsGetReport(int type,int id,int bufferSize)` (input type1 only).

- [x] Write failing report fixtures/bounds/array-isolation and GET_REPORT tests.
- [x] Implement descriptor/encoding. Adapt H0's relative wheel/pan fields with
  report ID1 and existing three buttons; keep attribution and existing license.
- [x] Independently decode descriptor short items in tests to check button/padding
  bit counts, X/Y/wheel/pan widths/pages/usages, report ID and relative flags.
- [x] Standalone javac/JUnit red/green; report outcomes. Main owns Gradle.

## Task 2 — Enrollment version migration (agent)

Files: `EnrollmentStore.java`, `EnrollmentStoreTest.java`, new
`EnrollmentMigrationTest.java`; update existing retained-session fixture only if
an explicit version fixture requires it. Do not change ProbeSession/AtomicFile.

Interface: new `boolean requiresReenrollment()` is true only for a structurally
valid version1 record, never permission to use its selector. Existing peer/save/
forget/failed contracts persist. Decode and save version2, same19-byte format.

- [x] Write failing tests: old valid record has no peer and remains byte-identical;
  migration notice true; invalid/unknown records unselected without false notice.
- [x] Implement minimal load/save migration and preserve AtomicFile readback/failure
  semantics. Successful new save/Forget clears notice; failed storage stays blocked.
- [x] Test ProbeSession cannot connect/confirm/send using only an old saved bond;
  fresh observed comparison/new bond can save version2; restart loads version2.
- [x] Run isolated JUnit and report. No device actions, no other source changes.

## Task 3 — Native integration and scroll controls (main)

Files: `ProbeActivity.java`, new `ScrollInput.java`/`ScrollInputTest.java`,
`NativeStopTest.java`/resource fixture, build.gradle.kts, README, AGENTS.md.

Interface: per-run `ScrollInput(Runnable cancel, BooleanSupplier ready,
Consumer<MouseReport.Direction> send)`, `void touchDown()`,
`void click(MouseReport.Direction,long uptimeMs)`. Main-thread only; no native API
or queue inside helper. Run readiness remains authoritative via supplied callback.

- [x] Test helper using real TouchpadGesture for pad-down/move → button-down →
  frame/up → click ordering. Test canceled button touch, keyboard-style click,
  throttled click, new run, denied eligibility and failed native submission.
- [x] Implement per-run throttle and wire actual button ACTION_DOWN to touchDown,
  returning false. OnClick uses InputSubmission.guard around current-run click.
- [x] Use MouseReport for every descriptor/send/reply/neutral/release; broaden
  existing affected callback native-error catches to include IllegalArgumentException.
  Keep boot/unknown protocol Stop and unexpected-peer rejection.
- [x] Show persistent migration text in setup while old version1 record is present.
  Add labeled scroll buttons outside setup; keep pad, Stop/Close and accessible
  directional alternatives. Responsive rows must not starve setup in landscape.
- [x] Update source-linked Stop fixture to compile MouseReport and assert payload
  length/ID. Bump versionCode6/name0.6-diagnostic; update docs and manual migration.
- [x] Run full probe unit/lint/debug build, package permissions/backup and variant
  exclusions. Review full proposed diff including new files; fix/re-review findings.
- [x] Commit verified implementation and exact source/APK evidence (`a162384`).

## Task 4 — Controlled install and live gate

- [x] Ask owner to Stop and remove only Nord–XGIMI bond on BOTH devices once the
  reviewed APK is ready. Do not install until owner reports ready.
- [x] Verify unique existing authorized Nord ADB target, stopped app, artifact hash;
  install in place without clearing app data or restarting ADB. Verify version6.
- [x] Inspect own-app layout in portrait/landscape without HID injection; involve
  owner if screen is asleep/locked. No ADB gestures count as gate evidence.
- [x] Owner Start → Make visible → fresh matching-code pairing → Save/connect.
  Verify N1 movement/tap, each scroll direction away from edges, no accidental
  click/jump, Stop/background/expiry and explicit saved reconnect. Record partial
  failures honestly; no drag/ADB fallback.
- [x] Update gate report/progress/capabilities/backlog. N2 diagnostic behavior
  acceptance passed on2026-10-25. No N3 implementation or unrelated gate promotion.

## Execution rulings

Owner approved scope and requested subagents previously; proceed without another
plan-approval pause. Reuse existing worktree. Tasks1–2 have disjoint writes and use
isolated javac output; main owns Activity/integration and all Gradle/git actions.
An independent reviewer checks the integrated change before installation.
