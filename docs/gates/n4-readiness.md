# N4 private daily-use readiness

Prepared2026-10-27 against source `da568d4`; previously accepted app0.20 is preserved locally.
Merged main `12664b4` has the same source tree; PRs #8/#9 are merged.
**Status: NOT READY — reviewed0.21 installed with preserved enrollment. Nord lifecycle and endurance acceptance pending.**

## Selected and proposed behavior

Selected by owner: keep an idle background connection for faster return; input
only while the remote is visible. Approved lifecycle defaults: active
visible sessions beyond five minutes,30-minute hidden retention, healthy return
reuses the same established link with a fresh input epoch; explicit Connect after
loss/expiry/Stop. Pairing remains attended/bounded. See the approved [N4 design](../superpowers/specs/2026-10-27-nord-daily-use-design.md)
and [plan](../superpowers/plans/2026-10-27-nord-daily-use.md).

## Readiness matrix

| Gate | Evidence | Status |
| --- | --- | --- |
|0.20 UI/normal use | Owner reports testing/everything works;176unit/26focused native tests recorded | Accepted baseline only |
| N4 lifecycle contract | Owner approved concrete design and plan2026-10-27 | Approved; candidate installed |
| Bluetooth loss completion | Fresh0.20 trace has disconnect/unregister/complete after verified OFF; owner confirms explicit reconnect without Force stop. Earlier missing-callback trial retained | Baseline trial passed; N4 retest required |
| TLS full-suite baseline | EOF(-1) captured in failing runs; test-only correction: isolated1pass; full runner41passes/1skip on emulator | [Evidence](n4-tls-close-read-report.md); integration check pending |
| Service-owned runtime | R1 committed/reviewed; permission follow-up lint0errors. 0.21 installed; device behavior pending | Software verified; N4 hardware pending |
| Hidden-input epoch | Candidate implements current attachment/epoch and per-write checks;206unit tests | Software verified; reviews closed |
| Lock/background HID retention | AOSP foreground-importance support; no Nord endurance proof | Not run |
|30-minute retention/expiry | Candidate lifetime/first-loss/expiry unit proof; previous0.20 was five-minute | Nord not run |
| Notification Stop/task removal | Actual service methods verified; new lifecycle needs Nord proof | Hardware pending |
| Loss/revocation/reboot/process recovery | Historical N3 coverage and gaps; no N4 proof | Not run for N4 |
| Same-signature update/keys |0.21 signer matches0.20; both records byte-identical across update/launch; stable process6s | Update preservation verified; key use pending owner Connect |
| Soak/battery/thermal | No N4 measurements | Not run |
| Private daily-use pilot | Requires preceding gates and exact-artifact owner acceptance | NOT READY |
| Public release/device matrix | Out of private-pilot scope | Deferred |

## Guardrails

- Keep missing native proof visible; no timeout/default-query cleanup bypass.
- No inactive/locked UI control, replay, automatic new connection or trust reset.
- Current debug signing custody and a public release-key strategy are different
  questions. Preserve current update identity; do not uninstall to solve signing.
- Android API documentation/AOSP are feasibility support, not hardware acceptance.
- No new N4 tests, device trials, APK or production build is claimed by preparation.

## Current delivery work

Tasks1/2 are complete in software: test-only TLS correction, current0.20
Bluetooth recovery evidence and reviewed service ownership extraction. Tasks3/4
implement retained-idle lifecycle and visible-input epochs. Review corrections
pass206unit and44native tests (one opt-in skip), including permission granted/
denied and actual focus-loss service destruction/rebinding. Complete and scoped re-reviews close all actionable findings.
Nord update preservation is verified; retained-idle hardware acceptance remains pending.

## Preparation verification

Independent builder review and the complete-change Codex review found no
remaining actionable documentation defects. Review included the three new drafts,
connected lifecycle/transport callers and the four updated project-status files.
The builder check confirmed first-loss idle expiry and cancel-on-hide during
unfinished connection attempts. Local Markdown links and whitespace checks pass.
No product code, enrollment data, device state or installed APK changed. No new
runtime/build/hardware test result is claimed by this documentation-only review.

## Candidate implementation

See the [software report](n4-candidate-report.md) and
[private-use guide](../nord-daily-use.md). Version0.21 is implemented and software-reviewed;206unit tests,44native passes/one opt-in skip, lint0errors/20warnings
and both builds are recorded. Reviewed0.21 is installed from source `6abbc23`; the Activity was not observed
resumed. Owner short-control/Home-return/Stop result is pending. No hardware PASS
is implied. The accepted0.20 APK remains preserved locally; no downgrade is planned.
