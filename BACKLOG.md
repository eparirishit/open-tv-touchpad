# Backlog

Backlog items are not approved V1 implementation work. Security and platform
design must be approved before an item moves into an active milestone.

## Blocking Decision

- [x] Select a bounded M0.1 gesture investigation before changing the certified
  target or input backend.
- [x] Review and approve the written M0.1 design.
- [x] Plan, implement, and execute M0.1 after written-design approval.
- [x] Do not rerun M0: verified M0.1 outcome `STOP` did not authorize it.
- [ ] Approve a different certified target, input backend, or M0 contract before
  further implementation.

M1 remains blocked until a complete M0 report verifies as `PASS`.

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
