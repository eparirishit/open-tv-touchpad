# Open TV Touchpad V1 Product Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the successful M0 and M1 spikes into a secure, robust, cross-device touchpad product and prepare an auditable open-source V1 release.

**Architecture:** Reuse only the M0 gesture path and the M1-selected certificate, TLS, and discovery adapters. The Android TV application owns identity, controller leases, bounded input state, cursor position, and accessibility dispatch; the bundled same-origin PWA owns touch recognition and presents only relative commands. M2A establishes a secure move/tap vertical slice, M2B adds production identity and leases, M2C completes input behavior and recovery, M3 hardens the system, and M4 packages the release.

**Tech Stack:** Kotlin/JVM 17, Android API 26-36, Android AccessibilityService, Android Keystore, M1-selected TLS/X.509/mDNS dependencies, coroutines, JUnit, AndroidX Test, TypeScript, Vite, Vitest, Playwright, WebCrypto, JSON Schema, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`

## Global Constraints

- Implementation starts only when both `docs/gates/m0-report.md` and `docs/gates/m1-report.md` verify as `PASS` and ADRs 0001-0003 are `Accepted`.
- Product/repository name is Open TV Touchpad / `open-tv-touchpad`; Android namespace and application ID are `dev.opentvtouchpad`.
- Android configuration is `minSdk=26`, `compileSdk=36`, `targetSdk=36`; release code is non-debuggable and uses Java/Kotlin 17.
- Certified target is XGIMI Horizon XK03H on Android TV 11 at 1920x1080 with `com.netflix.mediaclient`; required clients are iPhone Safari/Home Screen PWA, Android Chrome/PWA, macOS Safari/Chrome, and Windows Edge/Chrome.
- HTTPS/WSS is mandatory. Manual OS-level root-fingerprint comparison remains the trust anchor; there is no HTTP control, insecure hostname verifier, bearer credential, PAKE claim, cloud relay, or custom TLS/X.509/signature primitive.
- V1 accepts only the closed command set in the specification and relative deltas. It contains no shell, raw text, URL, intent, package name, arbitrary accessibility action, screen read, telemetry, or DRM-related behavior.
- Accessibility uses a non-interactive `TYPE_ACCESSIBILITY_OVERLAY`, `canRetrieveWindowContent=false`, and serialized `dispatchGesture`; it is never enabled through ADB or `WRITE_SECURE_SETTINGS`.
- All sockets, messages, queues, accumulators, timeouts, deltas, rates, and logs are bounded. Movement applies at no more than 30 batches per second.
- Exactly one paired identity owns the 15-second controller lease; five-second renewals maintain it and live takeover requires TV confirmation.
- Product code uses monotonic time for leases, timeouts, cooldowns, and stale input. Wall time is limited to certificate and user-facing date behavior.
- Release builds contain no pre-provisioned identity, test key, debug route, permissive CORS/origin, exported debug component, secret log, backup path, or insecure fallback.
- No release tag, GitHub Release, store submission, or public artifact upload occurs without a separate explicit user request.

## Review Focus

- **Lifecycle cancellation races:** disconnect, revocation, lease loss, takeover, accessibility disablement, network loss, and service shutdown must cancel drag and pending input before a successor session can act; covered by `InputLifecycleRaceTest` and device trials.
- **Protocol/version skew:** stale cached assets or a newer server must block control without replay or downgrade and offer a deterministic reload path; covered by `VersionCompatibilityTest` and browser upgrade tests.
- **Build-flavor leakage:** debug identity and test material must be structurally absent from release variants, not merely disabled; covered by `verify-release.sh` and release source-set tests.
- **Queue starvation:** movement floods must not block lease release, revocation, cancellation, or health traffic; covered by `GatewayFloodTest` and `InputSchedulerTest`.
- **Interrupted certificate/origin state:** a crash during trust reset or LAN-address change must leave either the previous valid identity or an explicit setup-required state, never a partially trusted listener; covered by `ProvisionedIdentityStoreTest` and restart device trials.

---

## M2A — Secure Vertical Slice

### Task 1: Freeze Module Boundaries and Promote the M0 Input Runtime

**Files:**
- Create: `open-tv-touchpad/android/input/build.gradle.kts`
- Move: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/DisplayBounds.kt` to `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/DisplayBounds.kt`
- Move: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureSpec.kt` to `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/GestureSpec.kt`
- Move: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GesturePort.kt` to `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/GesturePort.kt`
- Move: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/input/GestureCoordinator.kt` to `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/GestureCoordinator.kt`
- Move: corresponding M0 input tests into `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/AcceptedInput.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/InputPort.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/ArchitectureBoundaryTest.kt`
- Modify: `open-tv-touchpad/android/app/build.gradle.kts`
- Modify: `open-tv-touchpad/settings.gradle.kts`
- Modify: `open-tv-touchpad/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: passing M0 gesture runtime and accepted M1 modules.
- Produces: independent `:android:protocol`, `:android:gateway`, `:android:input`, and `:android:app` modules; sealed `AcceptedInput` boundary with initial `Move(dx: Int, dy: Int, arrivedAt: Duration)` and `Tap(arrivedAt: Duration)` variants; `InputPort.submit(command: AcceptedInput): InputAdmission`; unchanged M0 `GesturePort` contract.

- [ ] **Step 1: Write the architecture-boundary and M0-regression tests**

Assert `protocol` has no Android dependency, `gateway` cannot import accessibility implementation classes, `input` cannot import gateway/TLS classes, `app` is the only wiring module, and the complete M0 input test suite retains identical behavior after relocation.

- [ ] **Step 2: Run tests before restructuring**

Run: `./gradlew :android:app:testDebugUnitTest :android:input:test`

Expected: FAIL because the input module and boundary test do not exist.

- [ ] **Step 3: Move the existing M0 code without changing behavior**

Add the smallest `InputPort` boundary for later gateway use, pin only ADR-accepted dependency versions, and remove every unselected M1 candidate from the version catalog and dependency graph.

- [ ] **Step 4: Verify all M0/M1 tests and dependency locks**

Run: `./gradlew check && ./gradlew dependencies --write-locks`

Expected: PASS; dependency locks contain only selected stacks and M0 behavior remains green.

- [ ] **Step 5: Commit module boundaries**

```bash
git add android gradle settings.gradle.kts
git commit -m "refactor: establish product module boundaries"
```

### Task 2: Implement the Foreground Gateway Lifecycle and Static Application Shell

**Files:**
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/gateway/GatewayService.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/gateway/GatewayRuntime.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/network/NetworkMonitor.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/preferences/GatewayPreferences.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/boot/BootReceiver.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/gateway/GatewayRuntimeTest.kt`
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/gateway/GatewayServiceDeviceTest.kt`
- Modify: `open-tv-touchpad/android/app/src/main/AndroidManifest.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/xml/backup_rules.xml`
- Create: `open-tv-touchpad/android/app/src/main/res/xml/data_extraction_rules.xml`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/web/BundledWebApp.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/web/BundledWebAppTest.kt`
- Modify: `open-tv-touchpad/android/app/build.gradle.kts`

**Interfaces:**
- Consumes: selected TLS/mDNS adapters, provisioned identity, built `web/dist`, accessibility readiness, active private LAN network, and user start/stop preference.
- Produces: `GatewayRuntime.start(reason: StartReason): StartResult`; `GatewayRuntime.stop(reason: StopReason)`; `GatewayRuntime.onNetworkChanged(network: LanNetwork?)`; `NetworkMonitor.events: Flow<NetworkEvent>`; `BundledWebApp.open(path: AssetPath): AssetResponse`; foreground `GatewayService` of type `connectedDevice`.

- [ ] **Step 1: Write failing lifecycle, manifest, and asset-policy tests**

Assert explicit user start, default-disabled boot start, restart on private-LAN address change, immediate stop on LAN/accessibility/certificate invalidity, no indefinite wake lock, exact interface binding, multicast release, a Gradle dependency from Android asset packaging to the production PWA build, immutable hashed assets, no-store bootstrap/version data, and specification permissions/exports/backup exclusions only.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:gateway:test`

Expected: FAIL with missing lifecycle types and manifest declarations.

- [ ] **Step 3: Implement the foreground runtime as one cancellable scope**

Start TLS, mDNS, and notification only after all preconditions pass; stop children in the order input admission, sockets, discovery, listener, notification. Network change rebuilds listeners without toggling accessibility. Boot receiver starts only when the user preference is enabled. Package `web/dist` through a deterministic Gradle copy task into generated app assets; fail the Android build when the web build ID or content hashes do not match `/app-version.json`, and never commit generated assets.

- [ ] **Step 4: Verify lifecycle and release manifest**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintRelease :android:app:assembleRelease`

Expected: PASS with only launcher and system-bound accessibility service exported and all backup/transfer domains excluded.

- [ ] **Step 5: Commit the gateway lifecycle**

```bash
git add android
git commit -m "feat: add foreground local gateway lifecycle"
```

### Task 3: Add a Structurally Debug-Only Authenticated Client

**Files:**
- Create: `open-tv-touchpad/android/app/src/debug/kotlin/dev/opentvtouchpad/debug/DebugIdentityProvider.kt`
- Create: `open-tv-touchpad/android/app/src/debug/assets/debug-client-public-key.spki`
- Create: `open-tv-touchpad/android/app/src/release/kotlin/dev/opentvtouchpad/debug/DebugIdentityProvider.kt`
- Create: `open-tv-touchpad/android/app/src/testDebug/kotlin/dev/opentvtouchpad/debug/DebugIdentityProviderTest.kt`
- Create: `open-tv-touchpad/android/app/src/testRelease/kotlin/dev/opentvtouchpad/debug/ReleaseIdentityAbsenceTest.kt`
- Create: `open-tv-touchpad/web/src/debug/debugIdentity.ts`
- Create: `open-tv-touchpad/web/src/debug/debug-identity.test.ts`
- Modify: `open-tv-touchpad/android/app/build.gradle.kts`
- Modify: `open-tv-touchpad/web/vite.config.ts`

**Interfaces:**
- Consumes: M1 authenticated session protocol and an offline-generated test key pair kept only in debug source/assets.
- Produces: debug `DebugIdentityProvider.load(): PairedProbeIdentity?` returning the fixed identity; release implementation of the same signature returning `null`; `import.meta.env.DEV`-guarded web debug identity module absent from production chunks.

- [ ] **Step 1: Write failing debug-success and release-absence tests**

Assert debug pairing bypass works only for the fixed test key, release compilation contains no SPKI/private-key marker or debug route, production web build contains no test key/name, and release startup cannot select a debug identity.

- [ ] **Step 2: Run both build variants and web tests**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:testReleaseUnitTest :android:app:assembleRelease && corepack pnpm --dir web test && corepack pnpm --dir web build`

Expected: FAIL because providers and leakage checks are absent.

- [ ] **Step 3: Implement source-set-isolated debug identity wiring**

Generate the test pair offline, commit only the debug public key to Android and debug private fixture to web test/debug assets, and make production bundling fail if either marker appears. Do not add a runtime setting, environment override, or remote enrollment route.

- [ ] **Step 4: Verify release absence**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:testReleaseUnitTest :android:app:assembleRelease && corepack pnpm --dir web test && corepack pnpm --dir web build`

Expected: PASS; release APK and `web/dist` marker scans return no test identity material.

- [ ] **Step 5: Commit debug-only identity**

```bash
git add android/app web
git commit -m "test: add isolated M2A client identity"
```

### Task 4: Deliver the Authenticated Move-and-Tap Vertical Slice

**Files:**
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/CommandEnvelope.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/AuthenticatedConnection.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/VerticalSliceInput.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/VerticalSliceInputTest.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/AppGraph.kt`
- Create: `open-tv-touchpad/web/src/session/ControlSocket.ts`
- Create: `open-tv-touchpad/web/src/touchpad/BasicTouchpad.ts`
- Create: `open-tv-touchpad/web/src/touchpad/basic-touchpad.test.ts`
- Create: `open-tv-touchpad/web/tests/m2a-vertical-slice.spec.ts`
- Create: `open-tv-touchpad/docs/gates/m2a-report.md`

**Interfaces:**
- Consumes: debug authenticated session, current server cursor, `InputPort`, one-finger pointer events, and M0 gesture runtime.
- Produces: authenticated `move(dx, dy)` and `tap()` only; `VerticalSliceInput.admit(command: AcceptedInput): InputAdmission`; `ControlSocket.send(command: SliceCommand): Promise<Acknowledgement>`; visible cursor and later `gesture_result` for tap.

- [ ] **Step 1: Write failing ordering, bounds, auth, and browser gesture tests**

Assert unauthenticated/tampered/replayed commands cause zero input; deltas remain within `[-120,120]`; movement is relative, coalesced, clamped, stale-dropped, and applied at no more than 30 Hz; tap flushes earlier accepted movement before dispatch; acknowledgement means admitted, not completed; and pointer cancellation sends no tap.

- [ ] **Step 2: Run unit and browser suites**

Run: `./gradlew :android:protocol:test :android:gateway:test :android:input:test && corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/m2a-vertical-slice.spec.ts`

Expected: FAIL because the vertical slice is not wired.

- [ ] **Step 3: Implement the smallest end-to-end move/tap path**

Keep one hardcoded Standard sensitivity profile and no production lease/pairing state. Input admission occurs only after MAC, schema, sequence, session, and queue checks. The PWA disconnect state consumes pointer events but emits no commands.

- [ ] **Step 4: Prove the vertical slice on the certified projector**

Run all automated tests, then record ten minutes of 30 Hz control, tap completion latency, cursor behavior over Netflix, reconnect within five seconds, and zero queue growth in `docs/gates/m2a-report.md`.

Expected: authenticated PWA move/tap works end to end; release scans still find no debug identity.

- [ ] **Step 5: Commit M2A**

```bash
git add android web protocol docs/gates/m2a-report.md
git commit -m "feat: complete secure move and tap slice"
```

## M2B — Production Identity and Controller Leases

### Task 5: Persist Provisioned Identity and Harden Local Storage

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/security/ProvisionedIdentityStore.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/security/ProvisionedIdentity.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/ProvisionedIdentityStoreTest.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/TrustResetCoordinator.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/TrustResetCoordinatorTest.kt`
- Modify: `open-tv-touchpad/android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: M1 certificate provisioner, app-private atomic storage, Android Keystore, current private LAN address, and explicit trust-reset confirmation.
- Produces: `ProvisionedIdentityStore.load(): IdentityLoadResult`; `ProvisionedIdentityStore.commit(identity: ProvisionedIdentity): Unit`; `ProvisionedIdentityStore.clear(): Unit`; `TrustResetCoordinator.reset(): ResetResult`.

- [ ] **Step 1: Write failing atomicity, expiry, address-change, and reset tests**

Assert complete-or-absent state across interrupted writes, no root private key or client secret in storage, invalid/missing leaf alias fails closed, certificate warnings at 60/30 days, changed IP enters explicit setup-required state, trust reset stops gateway/revokes pairings/deletes leaf and public cert state, and backup/transfer extraction remains impossible.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest`

Expected: FAIL with missing store/coordinator.

- [ ] **Step 3: Implement atomic app-private persistence and destructive reset**

Write versioned metadata to a temporary file, fsync where supported, rename atomically, and verify the Keystore alias before reporting readiness. Trust reset requires TV confirmation and cannot preserve pairings because the pinned leaf SPKI changes.

- [ ] **Step 4: Verify restart and interrupted-reset device cases**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest`

Expected: PASS; every interrupted case returns to either the old valid state or explicit setup.

- [ ] **Step 5: Commit identity storage**

```bash
git add android
git commit -m "feat: persist provisioned projector identity"
```

### Task 6: Implement Production Pairing and Paired-Device Storage

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/pairing/PairingService.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/pairing/PairingCode.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/identity/PairedDeviceStore.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/identity/DeviceName.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/pairing/PairingServiceTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/identity/PairedDeviceStoreTest.kt`
- Create: `open-tv-touchpad/web/src/pairing/PairingClient.ts`
- Create: `open-tv-touchpad/web/src/pairing/pairing-client.test.ts`

**Interfaces:**
- Consumes: TV-opened pairing window, secure random source, leaf signer/SPKI, exact origin, client public key/name/nonce/proof, monotonic clock, and TV phrase confirmation.
- Produces: `PairingService.open(): ActivePairing`; `PairingService.submit(request: PairingRequest): PairingChallenge`; `PairingService.confirm(requestId: PairingRequestId, approved: Boolean): PairingResult`; `PairedDeviceStore.put(device: PairedDevice): Unit`; `PairedDeviceStore.revoke(identityId: IdentityId): Boolean`; `PairedDeviceStore.list(): List<PairedDevice>`; `PairedDeviceStore.find(identityId: IdentityId): PairedDevice?`; `DeviceName.parse(value: String): DeviceName`.

- [ ] **Step 1: Write failing happy-path, abuse, normalization, and persistence tests**

Assert one random six-digit code, two-minute one-use expiry, five global failures triggering 15-minute cooldown, at most one active code, cooldown griefing limited to pairing, exact origin/version/nonces/name/client key/leaf SPKI in both signatures, matching four-word phrase, TV confirmation, no storage before confirmation, printable normalized 1-32-code-point names, no logs, and persistence of only public key/fingerprint/name/revocation/last-seen/profile.

- [ ] **Step 2: Run pairing tests**

Run: `./gradlew :android:gateway:test && corepack pnpm --dir web test -- pairing-client.test.ts`

Expected: FAIL because production pairing is absent.

- [ ] **Step 3: Implement the bounded pairing state machine**

Use constant-time code comparison where practical, one global monotonic attempt counter/window, mutually signed canonical transcript, and explicit TV decision. Never display or send a root fingerprint as application proof.

- [ ] **Step 4: Verify concurrent and restart behavior**

Run: `./gradlew :android:gateway:test && corepack pnpm --dir web test -- pairing-client.test.ts`

Expected: PASS; concurrent requests cannot create two identities or bypass cooldown.

- [ ] **Step 5: Commit pairing**

```bash
git add android/gateway web/src/pairing
git commit -m "feat: add production pairing ceremony"
```

### Task 7: Implement Production Session Authentication and Revocation

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/SessionAuthenticator.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/SessionRegistry.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/SequenceGuard.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/session/SessionAuthenticatorTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/session/SessionRegistryTest.kt`
- Create: `open-tv-touchpad/web/src/session/SessionAuthenticator.ts`
- Create: `open-tv-touchpad/web/src/session/session-authenticator.test.ts`
- Modify: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/AuthenticatedConnection.kt`

**Interfaces:**
- Consumes: paired identity, revocation state, client signed ephemeral key/nonce, leaf signer, secure random challenge/session/nonces, exact origin, and protocol version.
- Produces: `SessionAuthenticator.authenticate(exchange: HandshakeExchange): AuthenticatedSession`; `AuthenticatedSession.verify(envelope: MacEnvelope): VerifiedEnvelope`; `AuthenticatedSession.sign(message: ServerMessage): MacEnvelope`; `SessionRegistry.closeIdentity(identityId: IdentityId, reason: CloseReason)`.

- [ ] **Step 1: Write failing conformance, replay, and revocation tests**

Assert every transcript field, fresh ephemeral keys, HKDF context, separate directional keys, exact sequence starting at one, duplicate/lower/gap/cross-session rejection, malformed-envelope close, structurally valid rejection consumes sequence, reconnect resets sequence and keys, revoked identity cannot authenticate, and revocation closes every socket and removes lease within one second.

- [ ] **Step 2: Run server/client conformance suites**

Run: `./gradlew :android:gateway:test :android:protocol:test && corepack pnpm --dir web test -- session-authenticator.test.ts`

Expected: FAIL because production authentication/registry are missing.

- [ ] **Step 3: Replace probe persistence with production session state**

MAC verification must finish before sequence or application state transition. Destroy ephemeral private/shared material after key derivation, keep directional keys connection-local, and put no token in URLs, cookies, browser history, diagnostics, or persistent storage.

- [ ] **Step 4: Verify all replay and revocation cases**

Run: `./gradlew :android:gateway:test :android:protocol:test && corepack pnpm --dir web test -- session-authenticator.test.ts`

Expected: PASS with shared vectors byte-identical.

- [ ] **Step 5: Commit sessions**

```bash
git add android/gateway android/protocol web/src/session
git commit -m "feat: authenticate and revoke controller sessions"
```

### Task 8: Implement Controller Leases and TV-Confirmed Takeover

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/lease/LeaseArbiter.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/lease/TakeoverRequest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/lease/LeaseArbiterTest.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/TakeoverPromptController.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/TakeoverPromptControllerTest.kt`
- Create: `open-tv-touchpad/web/src/session/LeaseClient.ts`
- Create: `open-tv-touchpad/web/src/session/lease-client.test.ts`

**Interfaces:**
- Consumes: authenticated identity/socket, monotonic clock, visibility-aware renewals, TV prompt availability, and synchronous input-cancellation callback.
- Produces: `LeaseArbiter.acquire(requester: SessionRef): LeaseDecision`; `LeaseArbiter.renew(holder: SessionRef): LeaseDecision`; `LeaseArbiter.release(holder: SessionRef): LeaseDecision`; `LeaseArbiter.disconnect(session: SessionRef): LeaseSnapshot`; `LeaseArbiter.revoke(identityId: IdentityId): LeaseSnapshot`; `LeaseArbiter.confirmTakeover(requestId: TakeoverRequestId, approved: Boolean): LeaseDecision`; `LeaseSnapshot(holder, expiresAt, takeover)`; TypeScript `LeaseClient.acquire(): Promise<LeaseStatus>`, `renew(): Promise<LeaseStatus>`, and `release(): Promise<LeaseStatus>`.

- [ ] **Step 1: Write failing lease, race, and takeover tests**

Assert exactly one holder, 15-second duration, five-second visible-client renewal, immediate release/disconnect, expiry, non-holder input rejection, named TV prompt, denial while prompt unavailable, no transfer before approval, old-state cancellation before new admission, simultaneous request ordering, revocation, and control/status separation from pairing.

- [ ] **Step 2: Run lease tests**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest && corepack pnpm --dir web test -- lease-client.test.ts`

Expected: FAIL because the arbiter and prompt do not exist.

- [ ] **Step 3: Implement one lock-protected monotonic lease state machine**

Reserve lease/control commands from the input flood queue, make cancellation a synchronous prerequisite to transfer, and have the PWA stop renewing and accepting touch while hidden or disconnected.

- [ ] **Step 4: Verify race suite**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest && corepack pnpm --dir web test -- lease-client.test.ts`

Expected: PASS; no test observes two holders or input after lease loss.

- [ ] **Step 5: Commit leases**

```bash
git add android web/src/session
git commit -m "feat: add single-controller leases"
```

### Task 9: Remove Product Dependence on Debug Identity

**Files:**
- Modify: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/AppGraph.kt`
- Modify: `open-tv-touchpad/web/src/main.ts`
- Create: `open-tv-touchpad/scripts/verify-release.sh`
- Create: `open-tv-touchpad/scripts/testdata/release-forbidden-patterns.txt`
- Create: `open-tv-touchpad/docs/gates/m2b-report.md`

**Interfaces:**
- Consumes: production pairing, session, lease, release APK, and production web bundle.
- Produces: `verify-release.sh <apk>` and a product flow that has no debug-identity branch outside debug builds/tests.

- [ ] **Step 1: Write the release-verifier fixtures and failing product-flow test**

Scan manifest, resources, DEX strings, native assets, bundled web files, and route list for test keys/names, debug identity, cleartext control, permissive CORS/origin, exported debug components, debuggable flag, backup, and insecure verifier markers. Assert a clean install reaches certificate setup/pairing rather than automatic control.

- [ ] **Step 2: Run release verification before cleanup**

Run: `./gradlew :android:app:assembleRelease && sh scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk`

Expected: FAIL while product wiring still depends on M2A identity.

- [ ] **Step 3: Route all non-test flows through production identity**

Keep debug fixtures solely for debug/test automation, remove debug UI/route selection, and make the release verifier independent of Gradle task success.

- [ ] **Step 4: Verify M2B and record the gate report**

Run: `./gradlew clean check lintRelease assembleRelease && corepack pnpm --dir web test && corepack pnpm --dir web build && sh scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk`

Expected: PASS; `docs/gates/m2b-report.md` records enrollment, production pairing, revocation, lease, and takeover on two real controllers.

- [ ] **Step 5: Commit M2B**

```bash
git add android web scripts docs/gates/m2b-report.md
git commit -m "feat: complete production identity and leases"
```

## M2C — Complete Input Engine and User Experience

### Task 10: Enforce Strict Protocol Decoding, Admission, and Responses

**Files:**
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/StrictJsonDecoder.kt`
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/Command.kt`
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/ServerMessage.kt`
- Create: `open-tv-touchpad/android/protocol/src/test/kotlin/dev/opentvtouchpad/protocol/StrictJsonDecoderTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/admission/CommandAdmission.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/admission/RateLimits.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/admission/CommandAdmissionTest.kt`
- Modify: `open-tv-touchpad/protocol/v1/schema/session.schema.json`

**Interfaces:**
- Consumes: UTF-8 WebSocket text no larger than 4 KiB and authenticated session/lease state.
- Produces: closed sealed `Command` variants; `StrictJsonDecoder.decode(text: String): DecodeResult`; `RateLimits.tryConsume(scope: RateScope, operation: RateOperation, now: Duration): RateDecision`; `CommandAdmission.admit(session: SessionRef, command: Command): AdmissionResult` mapping accepted input commands to `AcceptedInput`; `ack`, `reject`, `gesture_result`, and periodic `session_status` messages.

- [ ] **Step 1: Write failing schema, duplicate-key, sequence, rate, and queue tests**

Cover exact field sets/types, duplicate/unknown/missing fields, non-UTF-8/binary/fragmented oversized data, bounded integers, closed enums/types, sequence consumption rules, ack semantics, no gesture-data echo, 60-capacity/30-per-second high-rate bucket, independent identity/global/lease/ping limits, discrete FIFO 32, control FIFO 8, and reject-newest overflow.

- [ ] **Step 2: Run protocol/gateway tests**

Run: `./gradlew :android:protocol:test :android:gateway:test`

Expected: FAIL with missing strict decoder/admission pipeline.

- [ ] **Step 3: Implement decode then MAC/sequence/lease/rate/state/queue admission**

Unknown, duplicate, malformed, lower, duplicate, or gap cases close the session as specified. Structurally valid rejected commands consume sequence and receive a bounded code response only when safe.

- [ ] **Step 4: Verify protocol conformance**

Run: `./gradlew :android:protocol:test :android:gateway:test`

Expected: PASS against all committed schema fixtures.

- [ ] **Step 5: Commit strict admission**

```bash
git add android/protocol android/gateway protocol
git commit -m "feat: enforce closed command admission"
```

### Task 11: Implement Cursor Profiles, Movement Scheduling, and Tap Ordering

**Files:**
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/SensitivityProfile.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/MovementAccumulator.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/InputScheduler.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/CursorState.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/MovementAccumulatorTest.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/InputSchedulerTest.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/TapOrderingTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/identity/ProfileStore.kt`

**Interfaces:**
- Consumes: admitted `move`, `tap`, `set_profile`, current display bounds, paired identity, monotonic arrival time, and M0 `GestureCoordinator`.
- Produces: `InputScheduler.admit(command: AcceptedInput): InputAdmission`; `InputScheduler.cancel(reason: CancelReason): CancellationResult`; `SensitivityProfile` enum `LOW`, `STANDARD`, `HIGH`; `ProfileStore.get(identityId: IdentityId): SensitivityProfile`; `ProfileStore.set(identityId: IdentityId, profile: SensitivityProfile): Unit`; authoritative `CursorState`.

- [ ] **Step 1: Write failing coalescing, staleness, profile, cursor, and ordering tests**

Assert 50 ms window, sum clamp `[-300,300]`, 100 ms stale discard, maximum 30 apply batches/second, bounded conservative acceleration, Low < Standard < High for identical traces, display clamp after bounds changes, five-second cursor hide, immediate hide on lease end, and tap after all earlier accepted movement with no retry after rejection/timeout.

- [ ] **Step 2: Run input tests**

Run: `./gradlew :android:input:test :android:gateway:test`

Expected: FAIL because the production scheduler is absent.

- [ ] **Step 3: Implement one monotonic serialized scheduler**

Keep cursor state projector-side, store only the enum per identity, expose counters rather than per-move results, and preserve reserved control/cancel processing under movement flood.

- [ ] **Step 4: Verify deterministic trace tests**

Run: `./gradlew :android:input:test :android:gateway:test`

Expected: PASS for fixed input traces and fake monotonic time.

- [ ] **Step 5: Commit movement/tap engine**

```bash
git add android/input android/gateway
git commit -m "feat: add bounded cursor and tap engine"
```

### Task 12: Implement Drag and Every Cancellation Path

**Files:**
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/DragStateMachine.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/InputLifecycle.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/DragStateMachineTest.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/InputLifecycleRaceTest.kt`
- Modify: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/accessibility/AndroidGesturePort.kt`

**Interfaces:**
- Consumes: `drag_start`, coalesced `drag_move`, `drag_end`, explicit `cancel`, continuing-gesture support, and lifecycle events.
- Produces: states `Idle`, `Starting`, `Active`, `Ending`, `Cancelling`; `DragStateMachine.handle(event: DragEvent): DragEffect`; `InputLifecycle.cancelNow(reason: CancelReason): CancellationResult`.

- [ ] **Step 1: Write failing state, timeout, conflict, and race tests**

Assert one drag only, current cursor start, continuing stroke, two-second update timeout, 15-second total limit, no tap/scroll/second drag during drag, ordered end, and synchronous cancellation on disconnect/revocation/lease loss/takeover/accessibility cancellation/service shutdown/network loss before any successor gesture.

- [ ] **Step 2: Run drag/race tests**

Run: `./gradlew :android:input:test`

Expected: FAIL because the drag state machine is absent.

- [ ] **Step 3: Implement explicit effects over the single-flight gesture coordinator**

Do not retry an Android-rejected/cancelled/timed-out continuation. Timeout enters degraded input state until a health check succeeds.

- [ ] **Step 4: Verify randomized event-order tests**

Run: `./gradlew :android:input:test`

Expected: PASS with zero terminal state other than `Idle` after cancellation.

- [ ] **Step 5: Commit drag**

```bash
git add android/input android/app
git commit -m "feat: add cancellable drag state machine"
```

### Task 13: Implement Two-Axis Scroll and Recenter

**Files:**
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/ScrollAccumulator.kt`
- Create: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/ScrollDispatcher.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/ScrollAccumulatorTest.kt`
- Create: `open-tv-touchpad/android/input/src/test/kotlin/dev/opentvtouchpad/input/ScrollDispatcherTest.kt`
- Modify: `open-tv-touchpad/android/input/src/main/kotlin/dev/opentvtouchpad/input/InputScheduler.kt`

**Interfaces:**
- Consumes: admitted `scroll(dx,dy)`, `recenter`, monotonic arrival time, display bounds, and serialized gesture coordinator.
- Produces: `ScrollAccumulator.add(delta, arrivedAt): ScrollBatch?`; `ScrollDispatcher.dispatch(batch): GestureResult`; recenter effect centering cursor and clearing pending movement.

- [ ] **Step 1: Write failing scroll/recenter tests**

Assert 50 ms coalescing, clamp `[-240,240]`, 150 ms stale discard, one-axis-dominant pulse, vertical/horizontal support, at least 40 ms between pulses, serialized dispatch, overload rejection, cancellation, and recenter clearing pending movement without injecting a tap or scroll.

- [ ] **Step 2: Run scroll tests**

Run: `./gradlew :android:input:test`

Expected: FAIL because scroll/recenter are absent.

- [ ] **Step 3: Implement bounded swipe pulses**

Use display-relative conservative maximum paths, no inertial fling, pinch, or multi-touch injection, and no automatic retry.

- [ ] **Step 4: Verify deterministic and ten-trial trace tests**

Run: `./gradlew :android:input:test`

Expected: PASS with every pulse within configured maximum.

- [ ] **Step 5: Commit scroll/recenter**

```bash
git add android/input
git commit -m "feat: add bounded two-axis scrolling"
```

### Task 14: Build the Complete Touchpad Interaction UI

**Files:**
- Create: `open-tv-touchpad/web/src/touchpad/GestureRecognizer.ts`
- Create: `open-tv-touchpad/web/src/touchpad/TouchpadController.ts`
- Create: `open-tv-touchpad/web/src/touchpad/TouchpadView.ts`
- Create: `open-tv-touchpad/web/src/settings/LocalPreferences.ts`
- Create: `open-tv-touchpad/web/src/touchpad/gesture-recognizer.test.ts`
- Create: `open-tv-touchpad/web/src/touchpad/touchpad-controller.test.ts`
- Create: `open-tv-touchpad/web/tests/touchpad-interactions.spec.ts`
- Modify: `open-tv-touchpad/web/src/main.ts`
- Modify: `open-tv-touchpad/web/src/styles.css`

**Interfaces:**
- Consumes: pointer/touch events, authenticated lease state, local scroll-direction preference, sensitivity enum, and `ControlSocket`.
- Produces: relative `move`, `tap`, `drag_start/move/end`, one-axis-dominant `scroll`, `recenter`, `cancel`, and `set_profile`; accessible connection/control status and controls.

- [ ] **Step 1: Write failing gesture-recognition and accessibility tests**

Assert one-finger move/tap/long-press drag, two-finger dominant-axis scroll, pointer cancel, multi-touch transition cancellation, no command while disconnected/hidden/not holder/update-blocked, natural/inverted local scroll, profile enum only, keyboard-focusable controls, screen-reader labels, reduced-motion behavior, and no browser zoom/scroll leakage inside the touch surface.

- [ ] **Step 2: Run unit and Playwright tests**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/touchpad-interactions.spec.ts`

Expected: FAIL because the complete recognizer/view are absent.

- [ ] **Step 3: Implement one pointer-event state machine**

Use relative deltas only, request animation frame for client coalescing without exceeding server limits, and emit `cancel` on ambiguous transitions. Keep phone/laptop layouts responsive without separate protocol behavior.

- [ ] **Step 4: Verify browsers and accessibility checks**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web build && corepack pnpm --dir web exec playwright test tests/touchpad-interactions.spec.ts`

Expected: PASS in configured Chromium/WebKit projects; real-device matrix remains required at M3.

- [ ] **Step 5: Commit touchpad UI**

```bash
git add web
git commit -m "feat: add cross-device touchpad interactions"
```

### Task 15: Implement Reconnect, Update, and Actionable Error Recovery

**Files:**
- Create: `open-tv-touchpad/web/src/session/ReconnectController.ts`
- Create: `open-tv-touchpad/web/src/status/ProblemState.ts`
- Create: `open-tv-touchpad/web/src/status/StatusView.ts`
- Create: `open-tv-touchpad/web/src/session/reconnect-controller.test.ts`
- Create: `open-tv-touchpad/web/src/status/problem-state.test.ts`
- Create: `open-tv-touchpad/web/tests/update-and-recovery.spec.ts`
- Modify: `open-tv-touchpad/web/src/update/UpdateGate.ts`
- Modify: `open-tv-touchpad/web/src/sw.ts`

**Interfaces:**
- Consumes: network/socket/auth/lease/gesture/version events, page visibility, Service Worker lifecycle, and immutable build/protocol metadata.
- Produces: `ReconnectController.nextDelay(attempt: Int): Duration`; `ReconnectController.reset()`; closed `ProblemState` mapping each specification error to exactly one next action; update-ready/reload flow.

- [ ] **Step 1: Write failing backoff, no-replay, error-copy, and update tests**

Assert capped exponential backoff with jitter from 250 ms to 10 s, disconnected-before-input, fresh session/sequence/no replay, five-second ordinary recovery target, every listed error state and one action, no automatic reload during active lease/drag, network-first version check, no cached bootstrap, update-required control block, and explicit **Reload to update** only after new worker readiness.

- [ ] **Step 2: Run recovery tests**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/update-and-recovery.spec.ts`

Expected: FAIL because recovery controller/status mapping are absent.

- [ ] **Step 3: Implement deterministic state transitions and user actions**

Map errors without exposing secrets or raw server data. Certificate/origin failures never retry through HTTP or an insecure verifier.

- [ ] **Step 4: Verify offline/online, lock/unlock, and upgrade flows**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web build && corepack pnpm --dir web exec playwright test tests/update-and-recovery.spec.ts`

Expected: PASS.

- [ ] **Step 5: Commit recovery UI**

```bash
git add web
git commit -m "feat: add safe reconnect and update recovery"
```

### Task 16: Complete the TV Setup, Device Management, and Diagnostics UI

**Files:**
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/SetupActivity.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/SetupViewModel.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/PairedDevicesViewModel.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/DiagnosticsViewModel.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/SetupViewModelTest.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/PairedDevicesViewModelTest.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/DiagnosticsViewModelTest.kt`
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/setup/SetupDpadNavigationTest.kt`
- Create: `open-tv-touchpad/android/app/src/main/res/layout/activity_setup.xml`
- Modify: `open-tv-touchpad/android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: certificate/clock/network/accessibility/gateway/pairing/device/lease/input status and explicit D-pad actions.
- Produces: complete onboarding, exact origins/fingerprint/phrase, accessibility guidance, start/stop/boot preference, pairing/cooldown, paired-device list with last seen/profile, revoke, takeover prompt, trust reset, expiry warning, and privacy-safe status diagnostics.

- [ ] **Step 1: Write failing state-model and D-pad traversal tests**

Assert every required action/status is reachable without touch, deterministic focus after dialogs/errors, no secret/code/signature/raw message/IP/coordinate in production logs or diagnostics, Android 13+ restricted-settings guidance, explicit destructive confirmations, and safe behavior when gateway/accessibility/network changes mid-screen.

- [ ] **Step 2: Run setup tests**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest`

Expected: FAIL because product setup screens are incomplete.

- [ ] **Step 3: Replace gate-only activity with the product setup flow**

Retain a debug-only M0 diagnostics entry if useful, but expose no gate/test identity in release. Render status from immutable view state and keep destructive operations in coordinators.

- [ ] **Step 4: Verify TV UI and manifest**

Run: `./gradlew :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest :android:app:lintRelease`

Expected: PASS; launcher entry, banner, icon, and focus behavior work with the projector remote.

- [ ] **Step 5: Commit setup UI**

```bash
git add android/app
git commit -m "feat: complete TV setup and device management"
```

### Task 17: Complete M2 End-to-End Acceptance

**Files:**
- Create: `open-tv-touchpad/android/app/src/androidTest/kotlin/dev/opentvtouchpad/e2e/ProductLifecycleDeviceTest.kt`
- Create: `open-tv-touchpad/web/tests/product-lifecycle.spec.ts`
- Create: `open-tv-touchpad/docs/gates/m2c-report.md`
- Create: `open-tv-touchpad/scripts/verify-m2-report.sh`

**Interfaces:**
- Consumes: full M2 product and two real controllers.
- Produces: reproducible M2C report proving trust through complete input/recovery behavior.

- [ ] **Step 1: Write the report verifier and automated happy/failure flows**

Require evidence for setup, enrollment, install, pairing, session, lease, takeover, all closed commands, profiles, stale drop, overload, accessibility disable/re-enable, Wi-Fi interruption, lock/unlock, revocation, update, trust reset, Netflix browsing, and 1080p playback.

- [ ] **Step 2: Run the automated product suite**

Run: `./gradlew check :android:app:connectedDebugAndroidTest && corepack pnpm --dir web test && corepack pnpm --dir web build && corepack pnpm --dir web exec playwright test`

Expected: PASS.

- [ ] **Step 3: Run the certified-device and two-controller checklist**

Use at least one iPhone and one Android or laptop controller. Record command admission latency, tap completion, profile monotonicity, scroll trials, takeover cancellation, reconnect, and release APK scan.

- [ ] **Step 4: Verify and commit M2C**

Run: `sh scripts/verify-m2-report.sh docs/gates/m2c-report.md && sh scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk`

Expected: PASS.

```bash
git add android web docs/gates/m2c-report.md scripts
git commit -m "test: complete M2 product acceptance"
```

## M3 — Security, Reliability, and Performance Hardening

### Task 18: Add Adversarial Protocol, Flood, and Race Coverage

**Files:**
- Create: `open-tv-touchpad/android/protocol/src/test/kotlin/dev/opentvtouchpad/protocol/GeneratedMalformedInputTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/GatewayFloodTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/SlowClientTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/RelayMutationTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/ConcurrentLifecycleTest.kt`
- Create: `open-tv-touchpad/web/src/protocol/protocol-fuzz.test.ts`
- Create: `open-tv-touchpad/scripts/run-security-suite.sh`

**Interfaces:**
- Consumes: deterministic seeded generators and complete protocol/gateway/input state machines.
- Produces: repeatable 10,000-case malformed suite plus replay, flood, slow-client, mutation, and lifecycle-race evidence.

- [ ] **Step 1: Add failing generators and security assertions**

Generate duplicate/unknown/missing fields, invalid UTF-8/JSON/numbers/enums/MACs/signatures/keys/sequences/lengths, 4 KiB boundaries, fragmented/slow handshakes, socket-cap pressure, 60-message jitter burst followed by 30 Hz, sustained excess, relay mutation of every protected field, and concurrent revoke/disconnect/takeover/shutdown. Assert zero unauthenticated gestures, bounded memory/queues/sockets, no crash/ANR/secret log, and control-command progress under flood.

- [ ] **Step 2: Run the security suite and capture failures**

Run: `sh scripts/run-security-suite.sh`

Expected: initial failures identify any unhandled generated or concurrent cases.

- [ ] **Step 3: Fix only root causes in shared decoders/state machines**

Do not add input-specific special cases in transport handlers. Preserve exact protocol-close versus bounded-rejection behavior.

- [ ] **Step 4: Re-run with fixed seeds and one randomized seed**

Run: `sh scripts/run-security-suite.sh`

Expected: PASS for 10,000 malformed inputs, floods, slow clients, mutations, and race schedules.

- [ ] **Step 5: Commit hardening tests/fixes**

```bash
git add android web scripts
git commit -m "test: harden protocol and lifecycle boundaries"
```

### Task 19: Run Device Performance, Soak, and Full Browser Matrices

**Files:**
- Create: `open-tv-touchpad/docs/testing/device-matrix.md`
- Create: `open-tv-touchpad/docs/testing/browser-matrix.md`
- Create: `open-tv-touchpad/docs/testing/performance-report.md`
- Create: `open-tv-touchpad/docs/gates/m3-report.md`
- Create: `open-tv-touchpad/scripts/verify-m3-report.sh`

**Interfaces:**
- Consumes: release-candidate APK/PWA, certified projector, required client matrix, local Wi-Fi, and Android/browser profiling tools.
- Produces: complete M3 PASS/FAIL evidence tied to source revision and artifact hashes.

- [ ] **Step 1: Define the report verifier from every quantitative criterion**

Require tap error grid, 30 Hz queue behavior, ten vertical/horizontal scroll trials, profile monotonicity, 100 forced-cancellation trials, p95/p99 ack latency, tap completion p95, reconnect, lease-loss block, two-hour soak, memory growth, idle/active CPU, queue maxima, wake-lock release, malformed/flood results, revocation under one second, manifest/backup/log scans, and all required browser flows.

- [ ] **Step 2: Build and hash one clean release candidate**

Run: `./gradlew clean check lintRelease assembleRelease && corepack pnpm --dir web install --frozen-lockfile && corepack pnpm --dir web test && corepack pnpm --dir web build && sh scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk`

Expected: PASS and immutable APK/web artifact hashes recorded before trials.

- [ ] **Step 3: Execute certified-projector performance and reliability trials**

Run 1080p playback, two-hour soak, latency, CPU/memory, 100 disconnect/revoke/takeover/timeout cancellations, accessibility cycles, reboot/idle/Wi-Fi/IP/service changes, flood, and wake-lock checks. Do not relax thresholds after measuring.

- [ ] **Step 4: Execute every browser/client flow**

Repeat enrollment fingerprint comparison, install, key persistence, pairing, control, background/foreground, lock/unlock, reconnect, update, revocation, hostname, and IP fallback on every required client/browser. Treat iPhone Safari and installed Home Screen PWA as separate identities.

- [ ] **Step 5: Verify and commit M3 evidence**

Run: `sh scripts/verify-m3-report.sh docs/gates/m3-report.md`

Expected: PASS or a valid FAIL that blocks release.

```bash
git add docs/testing docs/gates/m3-report.md scripts/verify-m3-report.sh
git commit -m "test: record V1 hardening acceptance"
```

## M4 — Open-Source Release Preparation

### Task 20: Add CI, Dependency Controls, SBOM, and Reproducible Release Verification

**Files:**
- Create: `open-tv-touchpad/.github/workflows/ci.yml`
- Create: `open-tv-touchpad/.github/workflows/release-check.yml`
- Create: `open-tv-touchpad/.github/dependabot.yml`
- Create: `open-tv-touchpad/scripts/build-release.sh`
- Create: `open-tv-touchpad/scripts/generate-sbom.sh`
- Create: `open-tv-touchpad/scripts/verify-reproducible-build.sh`
- Create: `open-tv-touchpad/scripts/verify-acceptance-report.sh`
- Create: `open-tv-touchpad/docs/release/building.md`
- Create: `open-tv-touchpad/docs/release/signing.md`
- Modify: `open-tv-touchpad/gradle/wrapper/gradle-wrapper.properties`
- Modify: `open-tv-touchpad/gradle/libs.versions.toml`
- Modify: `open-tv-touchpad/web/package.json`

**Interfaces:**
- Consumes: pinned toolchains/actions/dependencies, externally supplied release signing environment, clean source revision, and gate reports.
- Produces: CI checks, signed APK when authorized credentials are present, unsigned local verification artifact otherwise, SHA-256 checksums, CycloneDX SBOMs for Android/web, source archive, and reproducibility comparison.

- [ ] **Step 1: Write failing CI/release script self-tests**

Assert immutable action SHAs, Gradle wrapper checksum, dependency locks, frozen PNPM lock, no unreviewed high/critical vulnerability, no debug/unsigned publish, required gate verification, artifact revision metadata, SBOM presence, checksums, and clean-tree reproducibility inputs.

- [ ] **Step 2: Run release scripts locally before implementation**

Run: `sh scripts/build-release.sh --verify-only`

Expected: FAIL because CI/release scripts and required artifacts are incomplete.

- [ ] **Step 3: Implement verification-first release automation**

Keep signing secrets out of repository/logs, document custody/backup/rotation/compromise response, and separate build/verify from publish. Do not create a release or upload anything.

- [ ] **Step 4: Verify two clean builds and SBOMs**

Run: `sh scripts/build-release.sh --verify-only && sh scripts/verify-reproducible-build.sh && sh scripts/generate-sbom.sh`

Expected: PASS; any unavoidable APK signature/timestamp variance is isolated and documented, while unsigned payload/content hashes match.

- [ ] **Step 5: Commit release engineering**

```bash
git add .github scripts docs/release gradle web/package.json
git commit -m "build: add auditable release pipeline"
```

### Task 21: Publish Complete User, Security, and Backlog Documentation

**Files:**
- Modify: `open-tv-touchpad/README.md`
- Create: `open-tv-touchpad/SECURITY.md`
- Create: `open-tv-touchpad/BACKLOG.md`
- Create: `open-tv-touchpad/CONTRIBUTING.md`
- Create: `open-tv-touchpad/docs/setup/android-tv.md`
- Create: `open-tv-touchpad/docs/setup/ios.md`
- Create: `open-tv-touchpad/docs/setup/android.md`
- Create: `open-tv-touchpad/docs/setup/macos.md`
- Create: `open-tv-touchpad/docs/setup/windows.md`
- Create: `open-tv-touchpad/docs/usage/pairing-and-control.md`
- Create: `open-tv-touchpad/docs/usage/revocation-and-takeover.md`
- Create: `open-tv-touchpad/docs/usage/trust-reset-and-update.md`
- Create: `open-tv-touchpad/docs/troubleshooting.md`
- Create: `open-tv-touchpad/docs/privacy.md`
- Create: `open-tv-touchpad/docs/threat-model.md`
- Create: `open-tv-touchpad/docs/compatibility.md`
- Create: `open-tv-touchpad/scripts/verify-docs.sh`

**Interfaces:**
- Consumes: final UI text, exact routes/origins, certificate lifecycle, compatibility evidence, threat model, and versioned backlog from the specification.
- Produces: complete public setup/support/security documentation and exact V1.1/V1.2/V1.3/Future backlog.

- [ ] **Step 1: Write the documentation-link and required-topic verifier**

Require per-platform trust/fingerprint/install steps, accessibility enable/disable and restricted-settings guidance, pairing/takeover/revocation/reset/update, certificate expiry/IP SAN/`.local`/DHCP/Wi-Fi isolation/storage eviction/stale PWA/gesture troubleshooting, privacy/data locations, no DRM/licensing bypass statement, threat boundaries including transparent relay and pairing griefing, responsible disclosure, certified/tested/community tiers, and every backlog item exactly once.

- [ ] **Step 2: Run docs verification before authoring**

Run: `sh scripts/verify-docs.sh`

Expected: FAIL listing missing documents/topics/links.

- [ ] **Step 3: Write concise task-oriented documentation**

Use the exact UI labels and tested OS flows from M1/M3. Do not describe the enrollment page, phrase, mDNS, or PWA as an independent trust anchor. Keep Windows/Raspberry Pi relay, ADB helper, text, voice, global actions, media control, launcher, diagnostics export, broader platforms, advanced sensitivity, faster rates, and F-Droid in `BACKLOG.md`, not V1 instructions.

- [ ] **Step 4: Verify documentation and internal links**

Run: `sh scripts/verify-docs.sh`

Expected: PASS with no placeholder, broken relative link, or undocumented required recovery path.

- [ ] **Step 5: Commit public documentation**

```bash
git add README.md SECURITY.md BACKLOG.md CONTRIBUTING.md docs scripts/verify-docs.sh
git commit -m "docs: complete V1 setup and security guides"
```

### Task 22: Freeze the V1 Acceptance Report and Release Candidate

**Files:**
- Create: `open-tv-touchpad/docs/release/v1-acceptance.md`
- Create: `open-tv-touchpad/docs/release/v1-artifacts.txt`
- Modify: `open-tv-touchpad/CHANGELOG.md`

**Interfaces:**
- Consumes: M0, M1, M2C, and M3 PASS reports; accepted ADRs; CI result; release candidate; SBOM; checksums; docs verification.
- Produces: one auditable V1 acceptance report and locally prepared release-candidate artifact set; no publication side effect.

- [ ] **Step 1: Write the failing final acceptance manifest**

Reference exact source revision, Android/web build IDs, APK/source/SBOM hashes, toolchain versions, all gate decisions, selected dependency ADRs, full test commands, known limitations, and zero unresolved high/critical findings. Verify every referenced file/hash exists and matches.

- [ ] **Step 2: Run the complete clean verification**

Run:

```bash
./gradlew clean check lintRelease assembleRelease
corepack pnpm --dir web install --frozen-lockfile
corepack pnpm --dir web test
corepack pnpm --dir web build
corepack pnpm --dir web exec playwright test
sh scripts/run-security-suite.sh
sh scripts/verify-release.sh android/app/build/outputs/apk/release/app-release.apk
sh scripts/verify-gate-report.sh docs/gates/m0-report.md M0
sh scripts/verify-gate-report.sh docs/gates/m1-report.md M1
sh scripts/verify-m2-report.sh docs/gates/m2c-report.md
sh scripts/verify-m3-report.sh docs/gates/m3-report.md
sh scripts/verify-docs.sh
sh scripts/generate-sbom.sh
sh scripts/verify-reproducible-build.sh
sh scripts/verify-acceptance-report.sh docs/release/v1-acceptance.md
```

Expected: PASS. Any failure blocks release preparation.

- [ ] **Step 3: Perform a fresh-install final smoke test**

On the certified projector and one iPhone plus one Android/laptop controller, perform trust enrollment, install, pairing, control, takeover, revocation, update check, service stop, accessibility disable, and trust reset using only public documentation.

- [ ] **Step 4: Finalize the acceptance report and artifact inventory**

Record outcomes without secrets, pairing codes, client IPs, gesture coordinates, or signing material. Keep publication instructions separate and require explicit user authorization.

- [ ] **Step 5: Commit the release candidate record**

```bash
git add CHANGELOG.md docs/release
git commit -m "docs: freeze V1 release candidate evidence"
```

## V1 Completion Check

Run the exact command block in Task 22 Step 2 from a clean checkout. Completion requires every command to exit `0`, a successful fresh-install smoke test, and no unresolved gate failure. Preparing the release candidate does not authorize creating a Git tag, GitHub Release, app-store submission, or public upload.
