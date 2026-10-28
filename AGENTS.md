# Open TV Touchpad Development Conventions

These instructions apply to the entire repository.

## Approved N4 daily-use lifecycle (2026-10-27)

The owner approved the N4 design and implementation plan with “go ahead”. Follow
`docs/superpowers/specs/2026-10-27-nord-daily-use-design.md` and its plan. This
supersedes the five-minute/pause-stop policy only through the staged, reviewed N4
work: service-owned established links, visible/unlocked input epochs and30-minute
idle retention. R1 first preserves existing behavior. Missing Bluetooth native
completion and Nord retention/endurance evidence remain hard gates; approval
includes no replacement cleanup proof, timeout bypass or automatic reconnection.
Preserve application identity, both enrollments, backup restrictions and protected
transports. Public release and other-device bridges remain outside this approval.

## Requested prototype UI refresh (2026-10-26)

The owner supplied an HTML UI prototype and requested updating the Android app
from it. Follow `docs/superpowers/specs/2026-10-26-nord-prototype-ui-design.md`.
This supersedes the diagnostic0.7 visual arrangement only: native Remote/Touchpad
tabs, grouped Setup sections, light/dark appearance, circular D-pad and fixed
Stop. The owner’s follow-up hides bottom tabs on Setup, removes Close, adds a
square Stop icon and removes D-pad arrow circles. The next owner follow-up enables
header Stop only for a ready connection, makes OK visibly disabled until TV input
is ready, removes tab border highlights and adds theme/Netflix icons. The latest
owner follow-up hides Stop unless connected, puts Settings at the right end of
the header, and centers each theme icon directly above its label. Preserve all Activity admission, privacy, transport and lifecycle
contracts. The0.13 Bluetooth recovery block remains unresolved; UI work does not
certify or bypass it. Review and verify a stopped device before same-signature
installation; both enrollments must remain byte-identical.

## Requested Nord UI details (2026-10-26)

Follow docs/superpowers/specs/2026-10-26-nord-ui-details-design.md for status dots,
quieter diagnostic copy, numbered Bluetooth setup, muted-blue disabled OK, bounded
0.5×–2× touchpad sensitivity and six-cell OTP entry. The owner explicitly requested
VISIBLE OTP characters; this supersedes older masking requirements only for that
field. Keep validation, one authoritative buffer, no saved state/autofill/learning/
logging and clear-on-end/lifecycle. Preserve all transport, trust and admission
contracts. Retain secondary accessibility buttons and four scroll controls.
Wi-Fi/projector acceptance is deferred by the owner until tomorrow.

## Requested Nord simplification (2026-10-27)

Follow docs/superpowers/specs/2026-10-27-nord-ui-simplification-design.md. The owner
wants the redundant Buttons instead mode and visible mouse buttons removed;
this supersedes the old retain-buttons presentation rule. Preserve accessibility
with native named move/click actions on the touchpad through existing Activity
guards. Keep scrolling and sensitivity; place sensitivity below scroll controls.
Use nonoverlapping numbered setup, the exact shorter comparison label, bold tab
text and taller72dp visible OTP cells. Preserve privacy, trust and lifecycle.

## Approved TV trust-anchor compatibility (2026-10-25)

The owner approved removing only the explicit peer trust-anchor self-signature
check and testing the exception. Follow
`docs/superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md`.
Preserve code-bound pairing, TLS key-possession proof, exact DER pinning, native
trust-manager validation, validity/algorithm checks and client self-signature
verification. This is not permission to ignore native validation failures.

## Approved N3 Nord TV controls (2026-10-25)

The owner approved the earlier unified N3 plan after considering a separate
Google TV companion. Follow `docs/superpowers/specs/2026-10-25-nord-tv-controls-design.md`
and its implementation plan. This supersedes the no-network/no-TV-key boundary
only in the opt-in debug Nord diagnostic: reviewed TLS/Keystore fixture gate,
then code-bound Remote v2 pairing and exact pinned control, with existing HID.
N3a permits the specified test-only loopback fixture before N3b. Preserve the
five-minute run, combined Stop, existing HID enrollment and all other gates.
No plaintext runtime ADB, unpinned runtime TLS, background control or product release.

## Requested Nord remote presentation (2026-10-25)

The owner requested a minimal Google TV-inspired UI and supplied the connected
Nord app as reference. Follow the Nord remote UI design/plan dated2026-10-25.
This changes presentation only in the diagnostic0.7: native Remote/Setup views,
existing touchpad/scroll/pointer controls, no new transport or TV-key commands.
Keep0.6 descriptor/enrollment/trust/lifecycle unchanged; no fresh pairing required.
Build and review before a same-signature update; verify the probe is stopped.

## Approved N2 Nord scrolling (2026-10-25)

The owner approved `docs/superpowers/specs/2026-10-25-nord-scroll-design.md`
including four scroll buttons and one-time fresh Bluetooth pairing. Follow
`docs/superpowers/plans/2026-10-25-nord-scroll.md`. This supersedes N1's three-byte
descriptor/version1 enrollment only for the isolated0.6 diagnostic: use the sole
five-byte report and version2 enrollment; never adopt an old version1 selector.
Do not install until reviewed and owner migration prep is confirmed. OS unpairing
and comparison remain manual. Preserve all lifecycle/trust/release restrictions;
N3 and product integration remain outside scope.

## Approved Nord direct touchpad increment (2026-10-25)

The owner selected operating the Nord itself and instructed implementation of
N1 in `docs/superpowers/specs/2026-10-25-nord-direct-remote-design.md`.
Its plan is `docs/superpowers/plans/2026-10-25-nord-touchpad.md`. This extends
only the isolated HID diagnostic with finger movement/tap and shared input
failure handling. Keep its descriptor, enrollment, permissions and five-minute
foreground-only lifecycle. N2 scrolling, N3 Android TV Remote v2 and N4 product
lifecycle remain follow-ups. Mac/PWA and controller bridges are deferred; no
historical gate becomes PASS and no product backend is authorized by N1.

## Approved Hub Proof (2026-10-24)

The user approved the unified remote proposal and its first local hardware proof.
`docs/superpowers/specs/2026-10-24-unified-remote-hub-proof.md` governs the
isolated `experiments/hub_remote/` spike and supersedes the projector-only,
no-runtime-ADB, and no-Home/Back/app-launch restrictions ONLY for that spike.
M0 FAIL and M0.1 STOP remain historical facts; this is a separate H0 gate, not
an M0 workaround or permission to start M1/product delivery. No LAN/browser
control endpoint is authorized by this proof. Preserve all other boundaries.

## Accepted local PWA hub contract (2026-10-24)

The user approved local-only certificate trust and explicitly selected:
"Require a protected projector link: proceed with browser-only work while
verifying a suitable link; TV integration waits."
`docs/superpowers/specs/2026-10-24-local-pwa-hub-design.md` and its implementation
plan govern `hub/`, `web/` and their tests. H1B browser-only work is authorized;
no real device backend is permitted before H1A protected-link PASS and H1B PASS.
There is no trusted-LAN plaintext ADB product exception. H0 remains an isolated
historical proof; do not migrate its adapters yet.

For this hub only, the accepted constrained-intermediate/native-Mac Keychain
identity, opaque HTTPS sessions, plain JavaScript PWA and native owner approval
supersede the old Android Keystore/ECDH/TypeScript/projector-only requirements.
The trusted native Mac fingerprint display replaces the TV fingerprint display.
M0/M1 restrictions and Android boundaries below continue to govern the historical
Android app. All shared validation, review, secrecy and documentation rules apply.

## Project Contract

### Accepted Nord retained-bond diagnostic (2026-10-24)

The owner selected “remembering the verified projector approach” after the
OS-bond continuity limitation was explained. The accepted
`docs/superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md` supersedes the
probe's fresh-pairing-after-every-stop restriction only for its isolated B
diagnostic. Persist only the selected peer record, not input authorization;
require explicit Start/Connect and trust the OS-managed current bond under the
documented assumption. Do not claim app-pinned original-key continuity. H1A/H1B,
no-network/no-background-input boundaries and all product integration gates remain.

### Isolated Nord HID diagnostic (2026-10-24)

The owner's “sure, continue” authorizes the proposed small phone diagnostic app
for registration, authenticated-pairing feasibility and manual mouse/Stop checks.
`experiments/hid_probe/README.md` bounds this disposable spike with foreground-only
input. Its setup may use a non-exported, non-restarting native foreground service
with a Stop notification and five-minute deadline to survive system pairing UI.
It must not admit comparison confirmation or mouse input while the Activity is
paused; after confirmation, a pause ends the test. This diagnostic lifecycle fix
does not authorize a background-control bridge.
It has a separate application ID, no network permission, no production adapter,
and only an explicitly enabled debug build. It may be installed on the verified
Nord for these checks. Normal Android builds exclude it. H1A/H1B requirements
and the product integration block remain unchanged; no probe result alone is PASS.

- Build the smallest complete implementation of the approved Open TV Touchpad V1 specification and plans.
- Treat `docs/superpowers/specs/` as the product and security contract, `docs/superpowers/plans/` as the implementation order, and accepted files in `docs/adr/` as dependency and architecture decisions.
- M0 and M1 are hard stop gates. Do not start M1 without a verified M0 PASS report, and do not start product work without a verified M1 PASS report and accepted dependency ADRs.
- If implementation evidence contradicts the approved architecture, stop, document the evidence, and request a design decision. Do not silently weaken a gate or security property.
- Keep V1 focused on the projector-hosted PWA architecture. Backlog features remain out of scope until separately designed and approved.

## Security Boundaries

- Never add HTTP control, an insecure hostname verifier, permissive CORS/origin handling, a cloud relay, or an application-layer fallback around failed TLS.
- Manual comparison of the operating system's full root-certificate SHA-256 fingerprint against the TV is the trust anchor. Do not describe mDNS, the enrollment page, the PWA, a pairing phrase, or an application message as an equivalent trust anchor.
- Keep the leaf private key non-exportable in Android Keystore. Keep the temporary root private key in process memory only, use it to sign exactly one leaf, and never persist or log it.
- Use maintained, M1-selected TLS, X.509, and mDNS implementations. Do not write custom TLS, certificate validation, ASN.1/DER certificate construction, elliptic-curve arithmetic, or signature primitives.
- Accept only the closed, versioned command protocol and relative deltas. Never accept shell text, URLs, intents, raw package names, absolute coordinates, arbitrary accessibility actions, screen reads, or unrestricted text input.
- Authenticate sessions with the approved signed ephemeral ECDH handshake and directional HMAC keys. Verify MAC, schema, sequence, session, lease, state, and rate limits before input admission.
- Keep every socket count, message, queue, accumulator, delta, rate, timeout, and retry bounded. Lifecycle events must synchronously cancel pending input and active drag.
- Keep secrets, pairing codes, signatures, raw protocol messages, client IP addresses, and gesture coordinates out of Git, issue bodies, production logs, diagnostics, and review artifacts.
- Never add telemetry or third-party runtime resources to V1.

## Architecture Boundaries

- `android/protocol` contains platform-independent protocol encoding, schemas, and crypto adapters.
- `android/gateway` contains certificate, TLS, discovery, pairing, session, lease, admission, and bounded network state.
- `android/input` contains cursor, movement, tap, drag, scroll, queueing, and gesture state machines.
- `android/app` contains Android lifecycle wiring, the accessibility adapter, foreground service, setup UI, storage adapters, and TV integration.
- `web` contains the bundled same-origin TypeScript PWA, WebCrypto client, touch recognizer, update flow, and responsive UI.
- `protocol` contains versioned schemas, fixtures, word lists, and cross-language conformance vectors.
- Keep dependencies pointing inward through explicit interfaces. The protocol module has no Android dependency; input does not depend on gateway/TLS; app is the composition root.
- Preserve the exact HTTPS/WSS route table. New routes or commands require a specification change and security review before implementation.

## Development Workflow

- Name branches without a `codex/` prefix.
- Commits, feature-branch pushes, and pull request creation do not require separate approval. Obtain the user's explicit approval specifically before pushing directly to `main`.
- Make small commits. Every commit subject uses `type: short-commit-msg`, for example `feat: add PR snapshots` or `fix: fence stale refreshes`.
- Follow the approved milestone order and the task-level test-first steps. Start with the narrowest failing test, implement the minimum passing change, then run affected broader checks.
- Do not bypass failed hardware, browser, certificate, performance, or security acceptance criteria. Do not modify a threshold after observing a failure without returning to design review.
- Use ADB only for approved installation and diagnostics. It is never part of the runtime control path and must not inject gate gestures.
- Do not commit generated APKs, web bundles, signing material, local certificates, device identifiers, test recordings containing secrets, or machine-specific configuration.
- Pin build tools, dependencies, package locks, Gradle wrapper checksums, and CI actions. Remove rejected spike dependencies after each ADR is accepted.

## Review Before Push

- Before pushing changes, run the Codex code review feature on the complete proposed change, including untracked files and connected callers, using the acceptance criteria and approved architecture as the review contract.
- Each actionable finding must identify a violated criterion or a concrete security/data-loss defect and a reproducible scenario. Broad hardening, speculative edge cases, refactors, and new capabilities are not automatic scope additions.
- Fix all actionable in-scope findings, rerun affected verification, and repeat review after code fixes.
- Record false positives, architectural tradeoffs, and out-of-scope findings with reasons instead of silently implementing them.
- If repeat reviews uncover unrelated work or fail to converge, stop expanding implementation, report the remaining findings, and ask for a scope decision.
- Never defer a known authorization bypass, trust-boundary violation, secret exposure, stuck-input defect, or data-loss defect merely to finish a review.
- Batch related fixes by acceptance criterion into small commits, not one commit merely because another review ran.
- Preserve existing and published history. Explain the disposition of prior commits rather than silently reverting, rewriting, or discarding them.

## Verification Expectations

- Keep Kotlin and TypeScript protocol outputs byte-for-byte consistent through committed conformance vectors.
- Test trust boundaries, malformed inputs, duplicate keys, replay, sequence gaps, rate limits, queue overflow, slow clients, socket caps, lifecycle races, and every drag-cancellation path.
- Run release-variant manifest, backup, export, logging, test-identity, route, origin, and secret scans before release work.
- Hardware claims require evidence from the certified XGIMI Horizon XK03H. Browser claims require the complete approved client matrix.
- Never claim a gate, milestone, test, review, or device check passed unless its command or manual evidence was actually completed and recorded.

## Documentation and Progress

- Update `docs/progress.md` when milestone status, gate evidence, blockers, or verification results change.
- Update `docs/capabilities.md` when a user-visible capability becomes available, changes behavior, or is removed.
- Update the applicable architecture document, ADR, threat model, protocol fixture, and setup/troubleshooting guide when implementation or live observations change a flow.
- Keep `BACKLOG.md` versioned. Do not move backlog work into V1 implementation without approved security and platform design.
- Keep reports tied to an exact source commit, artifact hash, projector firmware, application version, OS/browser versions, and observed result.

## Scope Discipline

- Fix root causes within the owning module; do not add cross-layer shortcuts.
- Avoid unrelated refactors, speculative configurability, and abstractions without a current caller.
- Do not add product capabilities while performing gate spikes. Spike code must remain isolated, explicitly temporary, and absent from release artifacts.
- When instructions conflict, follow direct user instructions first, then this file, then the approved specification, implementation plans, and local conventions. Surface security-relevant conflicts rather than guessing.
