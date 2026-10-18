# Open TV Touchpad Development Conventions

These instructions apply to the entire repository.

## Project Contract

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
