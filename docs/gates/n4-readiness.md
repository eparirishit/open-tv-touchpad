# N4 private daily-use readiness

Prepared2026-10-27 against source `da568d4`; installed/accepted app remains0.20.
Merged main `12664b4` has the same source tree; PRs #8/#9 are merged.
**Status: NOT READY — lifecycle design is a draft. No runtime changes installed.**

## Selected and proposed behavior

Selected by owner: keep an idle background connection for faster return; input
only while the remote is visible. Proposed defaults for design review: active
visible sessions beyond five minutes,30-minute hidden retention, healthy return
reuses the same established link with a fresh input epoch; explicit Connect after
loss/expiry/Stop. Pairing remains attended/bounded. See the [N4 design](../superpowers/specs/2026-10-27-nord-daily-use-design.md)
and [plan](../superpowers/plans/2026-10-27-nord-daily-use.md).

## Readiness matrix

| Gate | Evidence | Status |
| --- | --- | --- |
|0.20 UI/normal use | Owner reports testing/everything works;176unit/26focused native tests recorded | Accepted baseline only |
| N4 lifecycle contract | Background-idle direction selected; concrete design prepared | Approval pending |
| Bluetooth loss completion | Latest captured trial missing disconnect/unregister acknowledgments; Force stop workaround | BLOCKED |
| TLS full-suite baseline | Close-read assertion fails in full suite, passes standalone; normal result not captured | Investigation required |
| Service-owned runtime | Current Run still belongs to Activity and service is an eligibility timer | Not implemented |
| Hidden-input epoch | Current writer has lifetime/ready/age checks but no independent UI epoch | Not implemented |
| Lock/background HID retention | AOSP foreground-importance support; no Nord endurance proof | Not run |
|30-minute retention/expiry | Proposed only; installed guard/TV deadline is still five minutes | Not run |
| Notification Stop/task removal | Current diagnostic behavior exists; new lifecycle not proven | Not run for N4 |
| Loss/revocation/reboot/process recovery | Historical N3 coverage and gaps; no N4 proof | Not run for N4 |
| Same-signature update/keys |0.20 record bytes preserved in prior update | N4 upgrade proof pending |
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

## Next concrete work after design approval

Task1 of the implementation plan: establish TLS close-read outcome/timing and
review Bluetooth recovery evidence. In parallel, Task2 may extract ownership
while retaining the current five-minute/pause-stop policy. Retained-idle runtime
and daily-use claims remain gated by native visibility and Nord hardware proof.

## Preparation verification

Independent builder review and the complete-change Codex review found no
remaining actionable documentation defects. Review included the three new drafts,
connected lifecycle/transport callers and the four updated project-status files.
The builder check confirmed first-loss idle expiry and cancel-on-hide during
unfinished connection attempts. Local Markdown links and whitespace checks pass.
No product code, enrollment data, device state or installed APK changed. No new
runtime/build/hardware test result is claimed by this documentation-only review.
