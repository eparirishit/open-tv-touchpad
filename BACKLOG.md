# Backlog

Backlog items are not approved V1 implementation work. Security and platform
design must be approved before an item moves into an active milestone.

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
- [ ] Resolve D1: protected projector transport or explicit trusted-LAN beta exception.
- [ ] Accept the new hub contract and record H1A transport evidence/policy.
- [ ] H1B: verify browser trust, native enrollment/revocation and bounded sessions
  without a projector backend.
- [ ] H2: integrate verified adapters after H1B and accepted H1A policy; certify
  browser lifecycle and device cleanup.
- [ ] H3: verify macOS distribution, setup/recovery and release boundaries.
- [ ] Build the phone/laptop PWA only after the applicable new gates pass.
- [ ] Add two-finger scrolling and measured sensitivity tuning in the PWA.
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
