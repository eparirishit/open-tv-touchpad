# Open TV Touchpad Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Open TV Touchpad V1 through two hard feasibility gates, three incremental product milestones, security hardening, and an auditable open-source release.

**Architecture:** A Kotlin Android TV application hosts a local HTTPS/WSS gateway and dispatches a closed set of authenticated relative-input commands through an accessibility service. A same-origin TypeScript PWA is the only V1 client. The roadmap stops after M0 or M1 if the certified hardware or trust model fails instead of building around an unproven assumption.

**Tech Stack:** Kotlin, Android API 26-36, Android AccessibilityService, Netty/JDK TLS candidate, maintained X.509 builder candidate, TypeScript, Vite, Vitest, Playwright, JSON Schema, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`

## Global Constraints

- Product and repository name: Open TV Touchpad / `open-tv-touchpad`; Android namespace and application ID: `dev.opentvtouchpad`.
- Certified target: XGIMI Horizon XK03H, Android TV 11, 1920x1080, `com.netflix.mediaclient`.
- Android configuration: `minSdk=26`, `compileSdk=36`, `targetSdk=36`.
- V1 client matrix: iPhone Safari/Home Screen PWA, Android Chrome/PWA, macOS Safari/Chrome, Windows Edge/Chrome.
- No ADB in the runtime control path, no shell/URL/intent/package command, no screen reading, no cloud relay, no telemetry, and no HTTP control fallback.
- Accessibility uses gesture dispatch and a non-interactive accessibility overlay with `canRetrieveWindowContent=false`.
- HTTPS/WSS is mandatory; manual OS-level comparison of the root SHA-256 fingerprint to the TV is the trust anchor.
- All queues, accumulators, messages, sockets, and rates are bounded; movement applies at no more than 30 batches per second.
- One controller lease is active; live takeover requires TV confirmation.
- M0 and M1 are hard stop gates. No product milestone may bypass a failed gate.
- Release code contains no test identity, test key, permissive origin, debug export, insecure hostname verifier, custom TLS/X.509 primitive, or fallback protocol.

## Review Focus

- **Interrupted gate execution:** rerunning a partially completed M0 or M1 must produce the same report without silently carrying stale pass results; covered by report-schema tests in each gate plan.
- **Build-flavor leakage:** debug gate activities, fixed identities, and test certificates must be absent from release artifacts; covered by release-APK manifest and string scans in the product plan.
- **Origin migration:** hostname and IP origins have separate PWA identities and an IP change requires DHCP restoration or trust reset; covered by M1 origin matrix tests and product onboarding tests.
- **Protocol/version skew:** a stale service worker must disable control and reach an explicit reload path; covered by product gateway/PWA integration tests.
- **Lifecycle cancellation:** disconnect, revocation, lease loss, takeover, accessibility disablement, and service shutdown must cancel drag before another gesture; covered by the input-engine state-machine suite.

---

## Plan Set and Order

1. **M0 Hardware Gesture Gate**  
   Plan: `docs/superpowers/plans/2026-10-18-open-tv-touchpad-m0-hardware-gate.md`  
   Deliverable: a disposable D-pad-operated Android test build plus `docs/gates/m0-report.md`.  
   Stop condition: any required Netflix gesture or clean cancellation remains unsupported after one documented investigation.

2. **M1 Trust, Discovery, and PWA Gate**  
   Plan: `docs/superpowers/plans/2026-10-18-open-tv-touchpad-m1-trust-network-gate.md`  
   Prerequisite: signed-off M0 report.  
   Deliverable: evidence-backed ADRs selecting the TLS server, certificate library, and mDNS implementation plus `docs/gates/m1-report.md`.  
   Stop condition: any required platform cannot verify the root fingerprint, use either certified HTTPS origin, persist the required client identity, or sustain the authenticated session protocol.

3. **M2-M4 Product, Hardening, and Release**  
   Plan: `docs/superpowers/plans/2026-10-18-open-tv-touchpad-v1-product.md`  
   Prerequisite: signed-off M0 and M1 reports and accepted M1 ADRs.  
   Deliverable: release-ready open-source repository, signed APK, checksums, SBOM, documentation, and V1 acceptance report.

## Gate Discipline

- [ ] **Step 1: Complete M0 in order and record every observed result**

Run the M0 plan. Do not start M1 until `docs/gates/m0-report.md` has `decision: PASS` and identifies the tested APK commit and projector firmware.

- [ ] **Step 2: Review and commit the M0 gate decision**

Run: `./scripts/verify-gate-report.sh docs/gates/m0-report.md M0`

Expected: `M0 report valid: PASS`

- [ ] **Step 3: Complete M1 in order and freeze its dependency ADRs**

Run the M1 plan. Do not start product work until the report has `decision: PASS` and all three selection ADRs have `status: Accepted`.

- [ ] **Step 4: Review and commit the M1 gate decision**

Run: `./scripts/verify-gate-report.sh docs/gates/m1-report.md M1`

Expected: `M1 report valid: PASS`

- [ ] **Step 5: Execute M2A, M2B, and M2C in order**

Each milestone must end in a working vertical slice. M2A may use only a debug-source-set pre-provisioned identity. M2B removes that path from product flows. M2C completes the input engine and PWA.

- [ ] **Step 6: Execute M3 hardening and the complete acceptance matrix**

No acceptance threshold may be waived by changing the test after failure. Any proposed threshold change returns to written design review.

- [ ] **Step 7: Execute M4 release preparation**

Release only after CI, release-APK scans, SBOM generation, reproducibility comparison, documentation review, and both device/browser acceptance reports pass.

## Roadmap Completion Check

Run:

```bash
./gradlew clean check lintRelease assembleRelease
corepack pnpm --dir web install --frozen-lockfile
corepack pnpm --dir web test
corepack pnpm --dir web build
./scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk
./scripts/verify-gate-report.sh docs/gates/m0-report.md M0
./scripts/verify-gate-report.sh docs/gates/m1-report.md M1
./scripts/verify-acceptance-report.sh docs/release/v1-acceptance.md
```

Expected: every command exits `0`; release verification reports no debug component, test credential, cleartext control route, unintended export, or missing artifact.

