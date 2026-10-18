# Open TV Touchpad M1 Trust and Network Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that every required client can securely enroll, install, persist an identity, discover or reach the projector, and sustain the authenticated local HTTPS/WSS protocol before product development begins.

**Architecture:** Extend the M0 repository with isolated protocol, certificate, TLS, discovery, and PWA spikes. Select one maintained implementation for each security-sensitive dependency through evidence-backed ADRs, expose only the specification's enrollment and application routes, and record all browser/device results in a strict M1 gate report. M1 contains no product input commands beyond an authenticated ping probe.

**Tech Stack:** Kotlin/JVM 17, Android API 26-36, Android Keystore, Java Cryptography Architecture, Netty 4.1 JDK SSL candidate, maintained X.509 builder candidates, maintained mDNS responder candidate, TypeScript, Vite, Vitest, Playwright, WebCrypto, Service Worker APIs.

**Spec:** `docs/superpowers/specs/2026-10-18-open-tv-touchpad-design.md`

## Global Constraints

- M1 starts only after `docs/gates/m0-report.md` verifies as `PASS` for the certified XGIMI Horizon XK03H.
- The security trust anchor is manual comparison of the full SHA-256 root fingerprint in operating-system certificate details against the TV display; no HTTP, mDNS, bootstrap, pairing, or PWA value is described as an independent trust anchor.
- The leaf key is non-exportable P-256 in Android Keystore; the temporary P-256 root key exists only in process memory, signs exactly one leaf, is not persisted, and is released immediately afterward.
- The leaf has exactly the stable `.local` DNS SAN and initial non-global LAN IPv4 SAN, server-auth EKU, `notBefore = generation - 24 hours`, and `notAfter = generation + 729 days`.
- HTTPS/WSS uses TCP `8443`; HTTP enrollment uses TCP `8080` and exposes only `GET /ca.crt` during a user-opened five-minute window.
- TLS 1.2 is the minimum and TLS 1.3 is preferred. There is no insecure hostname verifier, HTTP control fallback, application-layer encryption fallback, or globally installed crypto provider.
- Pairing and session transcripts use fixed-order, versioned, length-prefixed binary encoding; P-256 ECDSA/SHA-256 signatures use fixed 64-byte `r || s`; sessions derive separate 256-bit directional HMAC keys with ECDH P-256 and HKDF-SHA-256.
- The exact hostname is `ott-<10-base32-characters>.local`; `.local` is preferred and the initial private IP origin is the documented fallback.
- Required clients are iPhone Safari/Home Screen PWA, Android Chrome/PWA, macOS Safari/Chrome, and Windows Edge/Chrome.
- The spike must never dispatch an accessibility gesture or include product pairing, leases, movement, drag, scroll, app launch, shell, URL, intent, or screen-reading operations.
- A failed required platform or opaque-key/TLS proof is a hard architecture-review stop, not permission to weaken transport or trust.

## Review Focus

- **Interrupted trust setup:** restarting before root-key release or identity persistence must not reuse partial certificate state; covered by `CertificateIssuerContractTest`, `CertificateProvisionerDeviceTest`, and destructive device trials.
- **Origin migration:** hostname and IP origins must produce separate browser identities and an IP change must require DHCP restoration or full trust reset; covered by the browser origin matrix.
- **mDNS collision or stale multicast state:** name conflict and gateway restart must either publish a deterministic alternate candidate during the spike or fail visibly while always releasing the multicast lock; covered by `MdnsAdvertiserDeviceTest`.
- **Browser storage loss:** a missing non-extractable client key must return to pairing without retaining a usable orphan identity; covered by `IdentityStoreTest` and browser eviction trials.
- **Stale application shell:** a cached PWA with an incompatible protocol must block control and show only **Reload to update**; covered by `UpdateGateTest` and Playwright integration tests.

---

### Task 1: Define Cross-Language Protocol Primitives and Conformance Vectors

**Files:**
- Create: `open-tv-touchpad/protocol/v1/README.md`
- Create: `open-tv-touchpad/protocol/v1/schema/bootstrap.schema.json`
- Create: `open-tv-touchpad/protocol/v1/schema/pairing.schema.json`
- Create: `open-tv-touchpad/protocol/v1/schema/session.schema.json`
- Create: `open-tv-touchpad/protocol/v1/vectors/pairing-transcript.json`
- Create: `open-tv-touchpad/protocol/v1/vectors/session-transcript.json`
- Create: `open-tv-touchpad/protocol/v1/vectors/session-mac.json`
- Create: `open-tv-touchpad/protocol/v1/wordlists/bip39-english.txt`
- Create: `open-tv-touchpad/android/protocol/build.gradle.kts`
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/CanonicalWriter.kt`
- Create: `open-tv-touchpad/android/protocol/src/main/kotlin/dev/opentvtouchpad/protocol/ProtocolCrypto.kt`
- Create: `open-tv-touchpad/android/protocol/src/test/kotlin/dev/opentvtouchpad/protocol/ProtocolVectorTest.kt`
- Create: `open-tv-touchpad/web/package.json`
- Create: `open-tv-touchpad/web/pnpm-lock.yaml`
- Create: `open-tv-touchpad/web/tsconfig.json`
- Create: `open-tv-touchpad/web/vite.config.ts`
- Create: `open-tv-touchpad/web/src/protocol/canonical.ts`
- Create: `open-tv-touchpad/web/src/protocol/crypto.ts`
- Create: `open-tv-touchpad/web/src/protocol/protocol-vectors.test.ts`
- Modify: `open-tv-touchpad/settings.gradle.kts`

**Interfaces:**
- Consumes: Protocol version `1`, pairing/session field order from the approved specification, P-256 keys, and UTF-8 strings.
- Produces: `CanonicalWriter.field(bytes: ByteArray): CanonicalWriter`; `CanonicalWriter.finish(): ByteArray`; `ProtocolCrypto.ecdsaDerToRaw(signature: ByteArray): ByteArray`; `ProtocolCrypto.ecdsaRawToDer(signature: ByteArray): ByteArray`; `ProtocolCrypto.hkdfSha256(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray`; `ProtocolCrypto.verificationPhrase(fingerprint: ByteArray): List<String>`; TypeScript equivalents `encodeFields`, `signRaw`, `verifyRaw`, `deriveDirectionalKeys`, and `verificationPhrase`.

- [ ] **Step 1: Write shared golden vectors and failing Kotlin/TypeScript tests**

Pin context labels `open-tv-touchpad/pairing/v1` and `open-tv-touchpad/session/v1`, fixed field order, four-byte unsigned big-endian field lengths, raw uncompressed P-256 public points, 64-byte `r || s` signatures, full 32-byte HMAC-SHA-256 tags, and distinct `client-to-server` and `server-to-client` labels. The pairing order is context, version, exact origin, server nonce, client nonce, normalized device name, client signing SPKI, and leaf SPKI. The full session order is context, version, exact origin, client signing-key fingerprint, leaf SPKI, client ephemeral public key, server ephemeral public key, client nonce, server nonce, 256-bit challenge, session ID, and expiry. Derive each 32-byte directional key separately with `salt = SHA-256(unsigned full session transcript)` and UTF-8 HKDF info `open-tv-touchpad/session/v1/client-to-server` or `open-tv-touchpad/session/v1/server-to-client`. Encode every MAC input as direction label followed by fixed-order length-prefixed envelope fields and type-specific payload fields, never canonicalized JSON. Derive four verification words by treating the first 44 bits of the already-computed SHA-256 certificate/client-SPKI fingerprint as four consecutive 11-bit indexes into the pinned BIP-39 English word list; record its upstream revision, MIT license, and SHA-256 checksum. The words assist comparison and never replace the full certificate fingerprint check. Tests must reject overlong lengths, missing fields, reordered fields, malformed DER integers, non-64-byte raw signatures, modified context labels, and cross-direction MAC use.

- [ ] **Step 2: Run both vector suites and confirm they fail**

Run: `./gradlew :android:protocol:test && corepack pnpm --dir web test -- protocol-vectors.test.ts`

Expected: FAIL because the protocol implementations do not exist.

- [ ] **Step 3: Implement only the canonical encoder and standard-API crypto adapters**

Use JCA/WebCrypto for ECDSA, ECDH, SHA-256, and HMAC. Implement only DER/raw signature representation conversion and RFC 5869 HKDF composition where a platform API is unavailable; do not implement elliptic-curve arithmetic, ASN.1 certificate construction, or signature primitives.

- [ ] **Step 4: Verify byte-for-byte cross-language agreement**

Run: `./gradlew :android:protocol:test && corepack pnpm --dir web test -- protocol-vectors.test.ts`

Expected: PASS with every Kotlin output equal to the committed TypeScript/shared vector.

- [ ] **Step 5: Commit protocol primitives**

```bash
git add protocol android/protocol web
git commit -m "test: define protocol v1 crypto vectors"
```

### Task 2: Select and Prove Certificate Construction

**Files:**
- Create: `open-tv-touchpad/android/gateway/build.gradle.kts`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/security/CertificateIssuer.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/security/CertificateProvisioner.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/CertificateIssuerContractTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/androidTest/kotlin/dev/opentvtouchpad/security/CertificateProvisionerDeviceTest.kt`
- Create: `open-tv-touchpad/docs/adr/0001-certificate-construction.md`
- Modify: `open-tv-touchpad/settings.gradle.kts`
- Modify: `open-tv-touchpad/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: stable hostname, selected private IPv4 address, current wall time, and Android Keystore alias.
- Produces: `CertificateIssuer.issue(hostname: String, ipv4: Inet4Address, leafKey: KeyPair, now: Instant): IssuedIdentity`; `IssuedIdentity(rootCertificate: X509Certificate, leafCertificate: X509Certificate, chain: List<X509Certificate>)`; `CertificateProvisioner.provision(request: ProvisionRequest): ProvisionResult`; accepted ADR naming one pinned library and rejected candidates with measured reasons.

- [ ] **Step 1: Write the certificate contract tests before selecting a library**

Assert a self-signed P-256 CA root valid for ten years with CA/key-cert-sign usage and no Name Constraints, one P-256 leaf with digital-signature usage, exactly two SANs, server-auth EKU, CA=false, `now - 24h` to `now + 729d`, valid root/leaf signatures, a two-certificate chain, and successful use of an Android Keystore leaf key whose private encoding is `null`. Assert no second leaf can be issued after the provisioner reaches `Complete`, partial state is deleted on restart, and root private-key bytes never enter the persistence adapter.

- [ ] **Step 2: Run JVM and API 26 device tests against the empty contract**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: FAIL because no candidate is implemented.

- [ ] **Step 3: Evaluate maintained candidates behind `CertificateIssuer`**

Evaluate a pinned `okhttp-tls` `HeldCertificate` adapter first. If it cannot express the required CA, SAN, EKU, validity, or opaque external key without serialization, evaluate Bouncy Castle lightweight certificate-builder APIs without installing or reordering a global provider. Reject any candidate that exports the leaf key, installs a global provider, requires custom DER, fails API 26, or conflicts with the platform provider. Record versions, licenses, dependency sizes, tests, and rejection evidence in ADR 0001.

- [ ] **Step 4: Implement the smallest passing provisioner and prove non-persistence**

Keep the root `KeyPair` local to one provisioning call, persist only the public root certificate, leaf chain, leaf alias, origins, validity metadata, and state marker after every certificate assertion passes, then drop all root-key references. Search app-private files, logs, backup staging, and diagnostic output for the root private scalar and assert no match.

- [ ] **Step 5: Verify API 26 and certified-projector behavior**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: PASS on an API 26 emulator and the Android 11 projector; the device report records opaque-key use and absence of root-key persistence.

- [ ] **Step 6: Accept the ADR and commit**

```bash
git add android/gateway docs/adr/0001-certificate-construction.md gradle/libs.versions.toml settings.gradle.kts
git commit -m "spike: select certificate construction stack"
```

### Task 3: Select and Prove the Embedded TLS Server

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/gateway/TlsServer.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/gateway/LanBinding.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/gateway/RouteTable.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/gateway/RouteTableTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/androidTest/kotlin/dev/opentvtouchpad/gateway/TlsServerDeviceTest.kt`
- Create: `open-tv-touchpad/docs/adr/0002-embedded-tls-server.md`
- Modify: `open-tv-touchpad/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: opaque Android Keystore leaf alias, issued chain, selected concrete LAN address, allowed origins, and `RouteTable`.
- Produces: `TlsServer.start(config: TlsServerConfig): BoundServer`; `BoundServer.close()`; `LanBinding.select(networks: List<NetworkCandidate>): BindingDecision`; `RouteTable.resolve(method: String, path: String): RouteDecision`; accepted ADR naming one server stack.

- [ ] **Step 1: Write failing binding, TLS, and route-table tests**

Assert rejection of global, cellular, VPN, wildcard, and absent LAN addresses; exact binding to the selected private Wi-Fi address on `8443`; TLS 1.2 minimum; TLS 1.3 when client/server support it; successful handshake with the opaque leaf key; hard failure for hostname, IP, chain, or expiry mismatch; exact `Host` and `Origin`; and rejection of every method/path outside the eight specified application routes.

- [ ] **Step 2: Run the focused tests**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: FAIL because the server adapter is missing.

- [ ] **Step 3: Evaluate server candidates and record evidence**

Evaluate pinned Netty 4.1 with JDK SSL and a preconfigured `SSLContext` first. Evaluate Ktor CIO or Ktor Netty only if it can consume the Android Keystore key without extraction and has equal lifecycle control. Reject OpenSSL/tcnative, wildcard-only binding, hidden user-agent telemetry, key export, unsupported API 26 behavior, or stacks that cannot enforce message/handshake limits. Record version, license, transitive dependencies, cold-start size, handshake result, and reason for each decision in ADR 0002.

- [ ] **Step 4: Implement the selected adapter and strict route shell**

The shell may return fixed test responses and an echo-free authenticated probe placeholder. It must enforce a five-second handshake timeout, four unauthenticated and eight authenticated socket caps, 4 KiB message cap, exact hosts/origins, no CORS headers, no wildcard listener, and deterministic cleanup on stop or network loss.

- [ ] **Step 5: Verify on API 26 and the certified projector**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: PASS, with a device test proving the leaf private key remains non-exportable before and after TLS handshakes.

- [ ] **Step 6: Accept the ADR and commit**

```bash
git add android/gateway docs/adr/0002-embedded-tls-server.md gradle/libs.versions.toml
git commit -m "spike: select embedded TLS server"
```

### Task 4: Select and Prove Stable Local Discovery

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/discovery/StableHostname.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/discovery/MdnsAdvertiser.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/discovery/MulticastLease.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/discovery/StableHostnameTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/androidTest/kotlin/dev/opentvtouchpad/discovery/MdnsAdvertiserDeviceTest.kt`
- Create: `open-tv-touchpad/docs/adr/0003-local-discovery.md`
- Modify: `open-tv-touchpad/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: persisted random hostname seed, selected LAN address, HTTPS port `8443`, and Android `WifiManager.MulticastLock` adapter.
- Produces: `StableHostname.fromSeed(seed: ByteArray): String`; `MdnsAdvertiser.start(hostname: String, address: Inet4Address, port: Int): Advertisement`; `Advertisement.close()`; `MulticastLease.acquire(): Closeable`; accepted ADR naming one responder implementation.

- [ ] **Step 1: Write hostname, lifecycle, and collision tests**

Assert lowercase `ott-` plus exactly ten RFC 4648 base32 characters and `.local`, deterministic restart, A/SRV/TXT records for `_ottouch._tcp.local`, advertised port `8443`, selected IPv4 only, multicast lock held only while active, lock release after startup failure/stop/network loss, restart after LAN change, visible collision failure, and no collision with Android NSD or ADB mDNS service names.

- [ ] **Step 2: Run tests before adding a dependency**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: FAIL because no responder exists.

- [ ] **Step 3: Evaluate a maintained custom responder behind `MdnsAdvertiser`**

Evaluate a pinned JmDNS release first because the app must control host/address records rather than only a DNS-SD service name. Reject it if Android 11 multicast behavior, collision handling, shutdown, maintenance status, license, or dependency footprint is unacceptable. Do not write a DNS packet parser/responder from scratch; if no maintained candidate passes, stop M1 for architecture review. Record evidence in ADR 0003.

- [ ] **Step 4: Implement the selected adapter and IP fallback metadata**

Keep Android NSD disabled for this service, release all responder threads/sockets and the multicast lock on every exit, and report the exact private-IP HTTPS origin as fallback. Do not silently change hostname or issue another certificate after collision.

- [ ] **Step 5: Verify discovery from every client OS and commit**

Run: `./gradlew :android:gateway:test :android:gateway:connectedDebugAndroidTest`

Expected: PASS on the projector, with macOS, iOS, Android, and Windows observations recorded in ADR 0003; Android Chrome failure is acceptable only when the IP-SAN fallback succeeds and the DHCP-reservation workflow is documented.

```bash
git add android/gateway docs/adr/0003-local-discovery.md gradle/libs.versions.toml
git commit -m "spike: select stable local discovery"
```

### Task 5: Implement Clock Validation and the Root Enrollment Window

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/security/ClockVerifier.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/enrollment/EnrollmentServer.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/enrollment/EnrollmentWindow.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/security/ClockVerifierTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/enrollment/EnrollmentServerTest.kt`
- Create: `open-tv-touchpad/android/app/src/main/kotlin/dev/opentvtouchpad/setup/TrustSetupViewModel.kt`
- Create: `open-tv-touchpad/android/app/src/test/kotlin/dev/opentvtouchpad/setup/TrustSetupViewModelTest.kt`

**Interfaces:**
- Consumes: SNTP client adapter, wall clock, monotonic clock, selected LAN address, public root certificate bytes, and user actions from the D-pad setup screen.
- Produces: `ClockVerifier.verify(): ClockVerification`; `ClockVerification.Verified(offset: Duration)` or `ManualConfirmationRequired(displayedTime: Instant)`; `EnrollmentWindow.open(duration: Duration = 5.minutes): WindowHandle`; `EnrollmentServer.handle(request: HttpRequest): HttpResponse`; full root fingerprint and deterministic verification phrase for TV display.

- [ ] **Step 1: Write failing clock, route, limit, and UI-state tests**

Assert SNTP acceptance only within five minutes, manual confirmation when SNTP is unavailable, no automatic acceptance of a bad clock, monotonic five-minute closure, selected-interface binding on `8080`, only `GET /ca.crt`, response at most 32 KiB, ten requests per rolling minute globally, no redirects, no fingerprint endpoint, close on setup exit/network loss, and TV display of both HTTPS origins plus the full grouped SHA-256 fingerprint.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest`

Expected: FAIL with missing enrollment and clock types.

- [ ] **Step 3: Implement bounded enrollment and D-pad setup state**

Use RFC 5905-compatible SNTP through a narrow adapter, wall time only for certificate validity, monotonic time for windows and rate limits, fixed HTTP response headers, `application/pkix-cert`, `Cache-Control: no-store`, and no logging of client IPs or certificate setup secrets.

- [ ] **Step 4: Verify tests and projector setup UI**

Run: `./gradlew :android:gateway:test :android:app:testDebugUnitTest :android:app:connectedDebugAndroidTest`

Expected: PASS; setup remains fully D-pad operable and enrollment disappears after every close condition.

- [ ] **Step 5: Commit enrollment**

```bash
git add android/gateway android/app
git commit -m "feat: add bounded certificate enrollment"
```

### Task 6: Build the Installable PWA Shell and Durable Client Identity

**Files:**
- Create: `open-tv-touchpad/web/index.html`
- Create: `open-tv-touchpad/web/public/manifest.webmanifest`
- Create: `open-tv-touchpad/web/src/main.ts`
- Create: `open-tv-touchpad/web/src/styles.css`
- Create: `open-tv-touchpad/web/src/onboarding/OnboardingState.ts`
- Create: `open-tv-touchpad/web/src/identity/IdentityStore.ts`
- Create: `open-tv-touchpad/web/src/update/UpdateGate.ts`
- Create: `open-tv-touchpad/web/src/sw.ts`
- Create: `open-tv-touchpad/web/src/onboarding/onboarding-state.test.ts`
- Create: `open-tv-touchpad/web/src/identity/identity-store.test.ts`
- Create: `open-tv-touchpad/web/src/update/update-gate.test.ts`
- Create: `open-tv-touchpad/web/tests/install-and-storage.spec.ts`
- Modify: `open-tv-touchpad/web/vite.config.ts`

**Interfaces:**
- Consumes: exact current HTTPS origin, `/api/v1/bootstrap`, `/app-version.json`, WebCrypto, IndexedDB, and display-mode detection.
- Produces: `OnboardingState.next(event: OnboardingEvent): OnboardingState`; `IdentityStore.create(name: string): Promise<ClientIdentity>`; `IdentityStore.load(): Promise<ClientIdentity | null>`; `IdentityStore.clear(): Promise<void>`; `UpdateGate.evaluate(clientBuild: BuildInfo, serverBuild: BuildInfo): UpdateDecision`; standalone installable PWA shell.

- [ ] **Step 1: Write failing onboarding, storage, and update tests**

Assert certificate trust precedes install; iPhone default flow blocks key creation until opened in standalone mode; Safari temporary mode requires explicit acknowledgment and gets a separate identity; keys are P-256 ECDSA, non-extractable, stored as `CryptoKey` in IndexedDB, and never serialized; missing key returns to pairing; hostname/IP origins do not share storage; protocol mismatch blocks all socket/control actions and shows only **Reload to update**.

- [ ] **Step 2: Run unit and browser tests**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/install-and-storage.spec.ts`

Expected: FAIL because the PWA and identity state machine are missing.

- [ ] **Step 3: Implement the minimal installable shell and service worker**

Bundle all assets locally, hash immutable assets, serve the manifest and service worker un-hashed with no-cache semantics, avoid inline script/eval/third-party resources, preserve visible focus and touch accessibility, and keep the shell limited to onboarding, status, identity creation, and update recovery.

- [ ] **Step 4: Verify automated tests and real-browser storage behavior**

Run: `corepack pnpm --dir web test && corepack pnpm --dir web build && corepack pnpm --dir web exec playwright test tests/install-and-storage.spec.ts`

Expected: PASS; manual evidence separately records Safari vs Home Screen storage, inactivity/eviction behavior, Android PWA persistence, macOS profiles, and Windows profiles.

- [ ] **Step 5: Commit the PWA shell**

```bash
git add web
git commit -m "feat: add installable identity PWA spike"
```

### Task 7: Prove Pairing, Authenticated Sessions, and Strict HTTPS/WSS Policy

**Files:**
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/pairing/PairingProbe.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/SessionProbe.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/session/SocketLimits.kt`
- Create: `open-tv-touchpad/android/gateway/src/main/kotlin/dev/opentvtouchpad/web/StaticAssetPolicy.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/pairing/PairingProbeTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/session/SessionProbeTest.kt`
- Create: `open-tv-touchpad/android/gateway/src/test/kotlin/dev/opentvtouchpad/web/StaticAssetPolicyTest.kt`
- Create: `open-tv-touchpad/web/src/pairing/PairingProbe.ts`
- Create: `open-tv-touchpad/web/src/session/SessionProbe.ts`
- Create: `open-tv-touchpad/web/src/pairing/pairing-probe.test.ts`
- Create: `open-tv-touchpad/web/src/session/session-probe.test.ts`
- Create: `open-tv-touchpad/web/tests/authenticated-probe.spec.ts`

**Interfaces:**
- Consumes: protocol vectors, leaf signer/SPKI, non-extractable client signing key, exact origin, TLS route shell, secure random source, and monotonic clock.
- Produces: `PairingProbe.begin(request: PairingStart): PairingChallenge`; `PairingProbe.confirm(request: ClientProof): PairedProbeIdentity`; `SessionProbe.authenticate(socket: ProbeSocket): AuthenticatedProbeSession`; `AuthenticatedProbeSession.ping(nonce: ByteArray): ProbeAck`; TypeScript pairing/session clients with matching names and behavior.

- [ ] **Step 1: Write failing transcript, relay-mutation, policy, and load tests**

Assert mutually signed pairing transcript fields, client-key phrase match, leaf-SPKI pin, authenticated ephemeral ECDH handshake, fresh challenge/nonces/session ID/expiry, separate directional keys, first sequence `1`, exact increments, full-message HMAC before state transition, reconnect key/sequence reset, 30 Hz bidirectional MAC for ten minutes, and rejection of changed field/payload/direction/session/sequence. Also assert exact route/method table, exact Host/Origin, no CORS, CSP `default-src 'none'`, explicit same-origin WSS in `connect-src`, `frame-ancestors 'none'`, security-response `no-store`, four/eight socket caps, five-second auth timeout, and 4 KiB message cap.

- [ ] **Step 2: Run Kotlin, TypeScript, and browser tests**

Run: `./gradlew :android:gateway:test && corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/authenticated-probe.spec.ts`

Expected: FAIL because pairing/session probes are missing.

- [ ] **Step 3: Implement the minimum closed probe protocol**

Support only pairing proof, authenticated handshake, server/client status, and random-nonce ping. Pairing is user-started and TV-confirmed but deliberately omits production persistence, lockout, revocation, lease, and control commands; those belong to M2B. MAC verification precedes sequence/state updates, all ephemeral secrets are discarded after derivation, and neither side sends a root fingerprint as proof of TLS identity.

- [ ] **Step 4: Verify mutation resistance and sustained-rate budgets**

Run: `./gradlew :android:gateway:test && corepack pnpm --dir web test && corepack pnpm --dir web exec playwright test tests/authenticated-probe.spec.ts`

Expected: PASS; changing any protected byte causes zero probe-state transition. A transparent relay that only forwards/delays/drops correct messages remains explicitly out of scope and is recorded as such.

- [ ] **Step 5: Commit the authenticated probe**

```bash
git add android/gateway web
git commit -m "spike: prove authenticated local PWA session"
```

### Task 8: Execute the Required Client Matrix and Freeze the M1 Decision

**Files:**
- Create: `open-tv-touchpad/docs/gates/m1-report.md`
- Create: `open-tv-touchpad/docs/gates/m1-browser-matrix.md`
- Create: `open-tv-touchpad/docs/setup/certificate-enrollment.md`
- Create: `open-tv-touchpad/docs/setup/ip-fallback.md`
- Create: `open-tv-touchpad/scripts/testdata/m1-pass.md`
- Create: `open-tv-touchpad/scripts/testdata/m1-missing-platform.md`
- Modify: `open-tv-touchpad/scripts/verify-gate-report.sh`

**Interfaces:**
- Consumes: accepted ADRs 0001-0003, test APK SHA-256, source commit, projector firmware, browser/OS versions, both certified origins, and all M1 evidence.
- Produces: `verify-gate-report.sh <report> M1`, complete platform matrix, operator setup guides, and a single `Decision: PASS` or `Decision: FAIL`.

- [ ] **Step 1: Extend the report verifier with failing M1 fixtures**

Require fields for commit/APK/projector/firmware, all three accepted ADRs, root-key non-persistence, API 26 opaque-key TLS, projector opaque-key TLS, fingerprint-verification result for every required platform, hostname and IP TLS results, mDNS restart/lock/conflict results, 730-day/backdate/EKU/SAN validation, SNTP/manual-clock results, installed and non-installed PWA modes, Safari/Home Screen separation, Android Chrome `.local` or documented IP fallback, 30 Hz MAC benchmark, stale-PWA recovery, certificate-mismatch hard failure, and final decision. Reject stale timestamps, duplicate fields, placeholders, missing evidence links, or PASS with any required result not `PASS`.

- [ ] **Step 2: Verify the negative fixture fails**

Run: `sh scripts/verify-gate-report.sh scripts/testdata/m1-missing-platform.md M1`

Expected: FAIL naming the missing platform evidence.

- [ ] **Step 3: Run certificate enrollment and fingerprint comparison on every required client**

For iPhone, Android, macOS, and Windows, install the public root, open the operating-system certificate details, compare the complete SHA-256 fingerprint to the TV, enable trust only after the match, and record screenshots or redacted notes that contain no private key or pairing code. Validate the 730-day leaf, EKU, backdate, hostname SAN, IP SAN, hard certificate mismatch, and trust removal/reset path.

- [ ] **Step 4: Run origin, install, identity, discovery, and update trials**

Exercise Safari/Home Screen separately, Android Chrome/PWA, macOS Safari/Chrome, Windows Edge/Chrome, `.local`, initial private IP, gateway restart, multicast-lock release, name conflict, Wi-Fi loss, DHCP reservation recovery, browser storage clearing, non-installed temporary mode, and one stale service-worker build. Record exact versions and observed outcomes.

- [ ] **Step 5: Run authenticated-session performance trials**

Run a ten-minute 30 Hz bidirectional MAC/ping trial per required browser and on the projector; record handshake time, p95/p99 acknowledgement latency, CPU, reconnect behavior, and any dropped/invalid messages. Confirm no product gesture can be dispatched by the spike.

- [ ] **Step 6: Complete and verify the gate report**

Run: `./scripts/verify-gate-report.sh docs/gates/m1-report.md M1`

Expected for continuation: `M1 report valid: PASS`. If any required client cannot practically verify the fingerprint, use either certified origin, persist the required key, reject mismatches, or sustain the authenticated probe, set `Decision: FAIL`, document the evidence, and stop before M2.

- [ ] **Step 7: Freeze the gate evidence**

```bash
git add docs/gates docs/setup scripts
git commit -m "docs: record M1 trust and network gate"
```

## M1 Completion Check

Run:

```bash
./gradlew :android:protocol:test :android:gateway:test :android:app:testDebugUnitTest
./gradlew :android:gateway:connectedDebugAndroidTest :android:app:connectedDebugAndroidTest
corepack pnpm --dir web install --frozen-lockfile
corepack pnpm --dir web test
corepack pnpm --dir web build
corepack pnpm --dir web exec playwright test
./scripts/verify-gate-report.sh docs/gates/m0-report.md M0
./scripts/verify-gate-report.sh docs/gates/m1-report.md M1
```

Expected: every automated check exits `0`, all three ADRs are `Accepted`, M0 remains `PASS`, and the M1 report prints `M1 report valid: PASS`. Product implementation remains prohibited after any other result.
