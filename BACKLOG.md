# Backlog

As of 2026-10-24, D1 is resolved: the owner requires protected projector transport
and authorized browser-only H1B implementation. H1A remains NOT PASSED; no
plaintext product exception is authorized. The accepted design supersedes the
earlier proposed-status descriptions below for this hub work only.

Backlog items are not approved V1 implementation work. Security and platform
design must be approved before an item moves into an active milestone.

## Current direction: Nord handheld remote (2026-10-25)

- [x] Owner selected using the Nord directly, not a controller bridge.
- [x] Review/approve the [N1 touchpad design](docs/superpowers/specs/2026-10-25-nord-direct-remote-design.md).
- [x] Implement N1 finger movement/tap with unchanged HID trust/lifecycle:
  `fdb4905`,76 tests, lint0errors/22warnings and debug build pass.
- [x] Complete N1 scoped review and in-place install: version5 verified,
  enrollment bytes preserved, no actionable review findings left.
- [x] Verify N1 functional movement and Netflix target tap: owner reported both work.
- [x] Verify N1 swipe-return/multitouch cancellation and Stop/physical-remote use.
- [x] Complete N1 long-press rejection, foreground loss/return and expiry checks:
  owner confirmed all pass. [N1 diagnostic behavior acceptance](docs/gates/n1-report.md)
  is complete for0.5; no historical gate or product-readiness claim follows.
- [x] Review/approve the [N2 scroll-button design](docs/superpowers/specs/2026-10-25-nord-scroll-design.md),
  including fresh pairing after the descriptor/enrollment-version migration.
- [x] N2: implement/review four scroll buttons and fresh-pairing migration;
  101 tests pass, lint0errors/24warnings, debug build and independent review pass.
- [x] N2: controlled migration/install, portrait/landscape layout, both scroll
  axes and N1 movement/tap/lifecycle regressions on hardware. Owner confirmed
  all planned checks; [N2 diagnostic behavior acceptance](docs/gates/n2-report.md)
  passes for0.6. Broader reliability/product gates are unchanged.
- [x] Minimal Google TV-inspired native UI: charcoal Remote/Setup pages, fixed
  Stop/Close, existing controls; unit/native tests and independent review pass.
- [x] Version0.7 owner smoke: saved connect, movement/tap/scroll, page switching
  without stray input, Stop and portrait/landscape. [UI report](docs/gates/nord-ui-report.md).
- [ ] N3: approve the [concrete TV-controls design](docs/superpowers/specs/2026-10-25-nord-tv-controls-design.md).
  Source/lifecycle assessment complete; native TLS/Keystore platform proof,
  integration and combined hardware acceptance remain pending. Existing Mac
  Python implementation is reference, not Android runtime code.
- [ ] N4: review daily-use lifecycle and packaging after combined functionality.
- [ ] Before broader distribution, verify screen-reader/font-scale/layout behavior;
  current native alternative controls have not received a complete device audit.

Mac/PWA trust setup and browser matrix, phone/laptop bridge and Windows hosting
are deferred by this priority change. Retain their code and gate evidence; no
pending gate becomes PASS and no production transport exception is implied.

## Blocking Decision

- [x] Select a bounded M0.1 gesture investigation before changing the certified
  target or input backend.
- [x] Review and approve the written M0.1 design.
- [x] Plan, implement, and execute M0.1 after written-design approval.
- [x] Do not rerun M0: verified M0.1 outcome `STOP` did not authorize it.
- [x] Approve the separate H0 hub proof with Android TV Remote v2 and scrcpy.

M1 remains blocked until a complete M0 report verifies as `PASS`.

## Approved H0 proof and proposed hub product

- [x] Complete H0 scoped local Mac functional acceptance: corrected Netflix shortcut
  live retest passed. Preserve the earlier contradicted PASS and logged failure history.
- [x] Confirm profile activation with UHID by user observation: "Yes, it works".
- [x] Resolve visible pointer alignment without video; preserve physical remote use.
- [x] Confirm two-axis scrolling, release, Stop, reconnect and normal playback.
- [x] Draft/review local-only hub design and seven-stage implementation plan.
- [x] Select controller onboarding: per-device certificate trust, no VPN/account.
- [x] Resolve D1: protected projector transport or explicit trusted-LAN beta exception.
- [x] Accept the new hub contract and record H1A policy: protected link required.
- [ ] Prove H1A protected wireless transport; USB is currently unavailable to owner.
- [x] Isolated Nord Bluetooth HID compatibility probe: actual registration,
  matching-number pairing, pointer movement, Netflix click and manual Stop with
  physical-remote usability passed by user observation on `93da0d0`. This is not H1A PASS.
- [x] Probe negative lifecycle checks: retained bond alone leaves controls disabled
  on restart; leaving a confirmed session and returning does not resume input and
  preserves physical-remote use. TV-side reconnect was unavailable, not tested.
- [x] Controlled Nord adapter OFF/ON trial: verified radio restoration did not
  re-enable probe input; physical remote remained usable. This does not certify
  successful reconnect, peer-only link loss or held-input crash cleanup.
- [ ] Verify Bluetooth encryption/enrolled-peer authentication and reconnect,
  link-loss/crash cleanup; current probe does not certify these properties.
- [x] Resolve the [Nord reconnect trust decision](docs/superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md):
  owner accepted retained OS-managed bond with explicit Connect, including the
  API31 original-key continuity limitation after missed bond changes.
- [x] Implement/review/install the isolated remembered-projector diagnostic:
  0.3 (`7643aa8`), 48 unit tests, debug build, lint with no errors; no actionable
  independent review findings. Installation is not a reconnect PASS.
- [x] Verify 0.3 explicit saved-projector reconnect without another pairing
  ceremony: user confirmed reconnect and physical-remote operation.
- [x] Verify 0.3 saved enrollment and working input after process restart on retry:
  user confirmed connected state then actual left/up pointer movement.
- [ ] Diagnose 0.3 first-attempt restart disconnect; retry success does not erase
  the unexplained initial failure. No speculative reconnect bypass is approved.
  Investigation deferred at the owner's request on 2026-10-25; this is not a
  fix, user-error finding or gate waiver. Nord direct control is now the priority.
- [x] Add/review/install the authorized bounded 0.4 event trace (`d0a8c44`):
  53 tests, build/lint and packaged-permission checks pass; no review findings.
  No connection behavior change or automatic retry is included.
- [x] Capture one 0.4 saved restart connection attempt: user confirmed connection
  and left/up movement; contiguous trace shows connection after1325ms and accepted
  neutral/movement reports. Earlier disconnect did not reproduce; keep it open.
- [x] Verify 0.3 explicit Forget and bond-only input rejection: after Stop →
  Forget → Start with OS bond retained, the user confirmed no saved projector
  and disabled Connect/movement.
- [ ] Verify 0.3 OS bond-removal/re-pair revocation and durable Forget after process
  death; the explicit Forget check does not certify these separate paths.
- [ ] Separately design/review the authenticated Mac-to-Nord transport and native
  companion lifecycle before any production bridge implementation. No legacy
  plaintext ADB fallback or background-input authorization follows from the probe.
- [x] Implement/review browser-only H1B code: native identity, enrollment,
  sessions, bounded HTTPS/WSS, control lease and installed PWA;103Python/36Node tests.
- [ ] H1B: verify actual Keychain and phone/laptop certificate trust, installed-PWA
  persistence and browser lifecycle matrix; no gate PASS yet.
- [ ] H2: integrate verified adapters after H1B and accepted H1A policy; certify
  browser lifecycle and device cleanup.
- [ ] H3: verify macOS distribution, setup/recovery and release boundaries.
- [x] Build the browser-only phone/laptop PWA; real device integration remains gated.
- [x] Add two-finger scrolling and a bounded client sensitivity control.
- [ ] Measure/tune touchpad sensitivity on the protected hardware path.
- [ ] Later milestone: package a Windows hub and move hosting to the spare laptop.
- [ ] Evaluate reliable app detection before automatic TV/touchpad mode switching.
- [ ] Certify additional Android TVs per capability; investigate XGIMI-specific
  power/wake, focus and keystone separately.

H0 PASS does not turn M0/M0.1 into PASS or automatically unblock old M1.
The proposed [design](docs/superpowers/specs/2026-10-24-local-pwa-hub-design.md)
and [plan](docs/superpowers/plans/2026-10-24-local-pwa-hub.md) are ready for the
remaining transport-policy decision; no product network service is implemented.
Abrupt hardware link/power loss and active-drag interruption remain untested production
reliability limitations; local STOP tests passed but do not certify those hardware cases.

## Blocked Approved Milestones

- [ ] M1 — Local trust, HTTPS/WSS, discovery, browser persistence, and PWA gate.
- [ ] M2A — Secure vertical slice.
- [ ] M2B — Production identity and leases.
- [ ] M2C — Complete input engine.
- [ ] M3 — Hardening and reliability.
- [ ] M4 — Release engineering and documentation.
- [ ] M5 — Open-source release.

## Deferred Product Improvements

- [ ] Tune touchpad sensitivity from certified-device measurements.
- [ ] Validate installed-PWA behavior on iPhone and Android after M1 is
  unblocked.
- [ ] Evaluate a separately designed Windows-hosted server mode after V1.
