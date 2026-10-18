# Open TV Touchpad V1 Design Specification

**Status:** Written design awaiting user review  
**Date:** 2026-10-18  
**Product name:** Open TV Touchpad  
**Repository slug:** `open-tv-touchpad`  
**License:** MIT

## 1. Purpose

Open TV Touchpad is a projector-hosted, cloud-free remote for controlling touchscreen-oriented Android TV applications from a phone or laptop on the same trusted local network. The projector serves an installable Progressive Web App (PWA), authenticates each controller independently, and converts relative touchpad input into Android accessibility gestures.

V1 exists to make the sideloaded mobile Netflix application usable on an XGIMI Horizon XK03H without keeping a laptop connected, exposing Android Debug Bridge (ADB), or installing a native client on every controller.

The design must remain useful beyond one application or projector, but V1 is certified only against the hardware and software listed below. Compatibility reports for other devices do not imply certification.

## 2. Success Criteria

V1 succeeds when all of the following are true:

1. An XGIMI Horizon XK03H running Android TV 11 can host the service without ADB in the control path.
2. A Mac, Windows laptop, iPhone, and Android phone can install or use the PWA after a one-time local certificate trust procedure.
3. A paired client can move a cursor, tap, drag, scroll vertically and horizontally, recenter the cursor, and view connection latency.
4. The target Netflix mobile application, package `com.netflix.mediaclient`, accepts those gestures both in browsing screens and during 1080p playback.
5. No unauthenticated local-network client can inject gestures, acquire a controller lease, or enumerate paired devices.
6. Flooding, stale messages, disconnects, and controller takeovers cannot create unbounded work or leave a touch gesture active.
7. The setup and troubleshooting documentation can be followed by a non-developer without exposing ADB beyond the initial APK installation workflow.

## 3. Scope

### 3.1 V1 Features

- Relative pointer movement with a user-adjustable sensitivity preset.
- Single tap.
- Press-and-drag.
- Two-finger vertical and horizontal scrolling.
- Cursor recenter.
- Connection state and round-trip latency indicator.
- Per-device named pairing and immediate revocation.
- One active controller lease with explicit takeover confirmation.
- Guided certificate, accessibility, and pairing setup.
- Basic privacy-safe diagnostics.

### 3.2 V1 Non-Goals

- Keyboard or text entry.
- Voice input.
- Back, Home, media-key, or application-launch controls.
- Screen capture, screen inspection, screen mirroring, or remote viewing.
- Remote access outside the local network.
- Cloud accounts, relay servers, analytics, advertising, or telemetry.
- ADB as a runtime dependency or control channel.
- Automatic device discovery as the only connection method.
- Play Store distribution.

Screen mirroring is a separate future project because it changes the privacy, permission, bandwidth, DRM, and threat model. It is not an ordinary Open TV Touchpad backlog item.

## 4. Supported Platforms

### 4.1 Certified Projector

- XGIMI Horizon XK03H.
- Android TV 11.
- 1920x1080 output.
- Netflix mobile application package `com.netflix.mediaclient`.

### 4.2 Android Compatibility Target

- Minimum Android API level 26, corresponding to Android 8.0.
- V1 compiles against and targets Android API level 36. The target is reviewed before each release rather than silently raised during a release cycle.
- Android TV devices other than the certified projector are community-reported until the full device acceptance suite passes.
- Fire TV is not supported in V1.

### 4.3 Required Client Matrix

- iPhone: current supported iOS Safari, both in-browser and installed PWA modes.
- Android phone: current supported Chrome, both in-browser and installed PWA modes.
- macOS: current supported Safari and Chrome.
- Windows: current supported Edge and Chrome.

Browser support is confirmed by testing, not inferred from nominal standards support.

## 5. System Architecture

### 5.1 Projector Application

The Android application contains these components:

1. **Setup UI** — A D-pad-only Android TV interface that displays accessibility status, service status, connection origins, certificate fingerprint, certificate expiry, pairing controls, paired-device details, revocation controls, controller status, and diagnostics. Every action is reachable without a pointer, and the application provides a TV launcher entry, icon, and banner.
2. **Gateway Service** — A `connectedDevice` foreground service that serves the PWA over HTTPS and exposes same-origin pairing and control WSS endpoints. Its lifecycle is independent from the system-managed accessibility service so a network restart does not disable gesture capability.
3. **Certificate Manager** — Creates and stores the projector leaf key, performs one-time local root creation and leaf signing, deletes the root private key, exposes only the public root certificate during a manually activated enrollment window, and drives full trust reset when renewal is required.
4. **Identity Store** — Stores the leaf certificate, paired client public keys, sanitized device names, revocation state, and last-seen timestamps. Android backup is disabled.
5. **Pairing Service** — Manages short-lived pairing codes, TV-side confirmation, rate limits, and pairing lock state.
6. **Session Arbiter** — Authenticates WebSocket sessions, grants one active controller lease, renews and expires the lease, and requires TV confirmation for a live takeover.
7. **Input Engine** — Validates message schemas, enforces ordering and rate limits, coalesces bounded relative movement, maintains cursor state, and drives the gesture state machine.
8. **Accessibility Runtime** — Owns the cursor accessibility overlay and serializes calls to Android's accessibility gesture APIs.

The Gateway Service and Accessibility Runtime communicate through an in-process interface carrying validated domain commands and lifecycle events. Network payloads never reach Android accessibility APIs directly.

### 5.2 Progressive Web App

The projector serves a same-origin, dependency-bundled PWA containing:

- Guided connection, certificate, and pairing screens.
- Touchpad surface and gesture recognizer.
- Sensitivity presets and scroll-direction setting.
- Connection, lease, and latency status.
- Reconnect and re-pair guidance.
- Basic diagnostics with no application-content or browsing-history collection.

The PWA contains no third-party runtime scripts, fonts, analytics, or CDN resources.

### 5.3 Data Flow

```text
PWA gesture
  -> authenticated WSS session
  -> strict schema and size validation
  -> sequence, lease, and rate-limit checks
  -> bounded input queues and state machines
  -> serialized accessibility gesture dispatcher
  -> Android application
```

The client sends relative movement only. It cannot supply display coordinates, Android package names, URLs, shell text, intents, or arbitrary accessibility actions.

## 6. Network and Service Lifecycle

### 6.1 Local Addressing

- The projector generates one stable hostname in the form `ott-<10-base32-characters>.local`.
- Android 11's `NsdManager` can advertise a DNS-SD service but cannot select the stable hostname required by the certificate. M1 therefore evaluates a small custom mDNS responder that owns the A/AAAA, SRV, TXT, and hostname records and holds `WifiManager.MulticastLock` only while the foreground gateway needs it. The chosen responder dependency and its multicast behavior are recorded before M2.
- The leaf certificate contains both the stable `.local` DNS name and the current non-global LAN IPv4 address as Subject Alternative Names. The gateway accepts either exact HTTPS origin on TCP port `8443`.
- `.local` is the preferred origin. The exact IP origin is the Android Chrome and broken-mDNS fallback.
- A client is paired to one web origin. Moving between hostname and IP creates a different browser-storage origin and requires pairing that origin separately.
- If `.local` fails and DHCP later changes the certified IP address, the documented remedies are to restore the original address with a router DHCP reservation or perform a full trust reset and re-enroll. The server never silently issues a new certificate.
- The gateway binds only to the selected active LAN interface. It does not bind cellular, VPN, loopback-only, or wildcard interfaces when a concrete LAN address is available.
- The service refuses startup on an address it classifies as globally routable. The firewall is not treated as the security boundary.
- During certificate setup the projector performs an RFC 5905 SNTP query and requires its wall clock to be within five minutes of network time. When SNTP is unavailable, setup displays the projector time and requires the user to confirm it is within five minutes of the phone or laptop clock. The leaf certificate uses `notBefore = generation time - 24 hours` and `notAfter = generation time + 729 days`, keeping total validity at 730 days. Runtime leases, timeouts, cooldowns, and stale-input checks use a monotonic clock rather than wall time.

### 6.2 Foreground Service

- The user explicitly starts the Gateway Service after setup.
- The gateway declares foreground service type `connectedDevice` and the corresponding Android 14+ permission. It declares only the network and multicast permissions required by that service type.
- Optional start-after-boot is disabled by default and may be enabled only in the Setup UI.
- The service restarts its listeners after a LAN address change without restarting or toggling the accessibility service.
- No indefinite partial wake lock is held. The foreground service must survive ordinary idle through Android-supported lifecycle mechanisms.
- The service stops accepting commands immediately when accessibility is disabled, the selected LAN disappears, or certificate state is invalid.

### 6.3 Accessibility Service

- The service declares `android.permission.BIND_ACCESSIBILITY_SERVICE` and is exported only as required for the Android system to bind it.
- Its configuration enables gesture dispatch and an accessibility overlay.
- `canRetrieveWindowContent` is `false`.
- It does not request screen-content, notification, usage-access, overlay-app, storage, microphone, contacts, or location permissions.
- The user must enable it manually in Android accessibility settings. The application never uses ADB or `WRITE_SECURE_SETTINGS` to enable it.
- Setup and troubleshooting document the Android 13+ sideload flow: open App Info, choose **Allow restricted settings**, then return to Accessibility and enable the service. OEM wording and availability are verified in the compatibility matrix.

## 7. Security Model

### 7.1 Threats Addressed

The design addresses:

- A hostile device on the same Wi-Fi network.
- A malicious webpage attempting local-network requests.
- Spoofed mDNS advertisements and hostnames.
- Pairing-code guessing and pairing floods.
- Replay of captured application messages.
- A compromised or stolen paired controller.
- Pre-authentication socket exhaustion and slow clients.
- Oversized, malformed, or high-rate protocol messages.
- Stale movement or gesture commands after lag, reconnect, or takeover.
- Accidental exposure through Android component exports, backups, logs, or diagnostics.
- Command modification or injection by a transport relay after a legitimate PWA and client key have been established.

### 7.2 Explicitly Out of Scope

- A rooted or otherwise compromised projector operating system.
- A compromised browser or operating system on a currently paired controller.
- An attacker with physical access who can change projector settings or install software.
- Denial of service against the Wi-Fi network itself.
- A transparent relay that only observes, delays, drops, or forwards correctly signed application messages.
- Malicious PWA code delivered after the user trusts the wrong root certificate. Browser JavaScript cannot defend its own signing-key use when the code origin itself is compromised.

Even in those cases, the closed command protocol contains no shell, URL, arbitrary intent, package launch, or screen-read operation.

A hostile LAN device can deliberately exhaust pairing attempts while a pairing window is open and keep the global 15-minute cooldown active. This accepted griefing risk is bounded to user-initiated pairing windows and cannot affect already paired controllers.

### 7.3 Certificate Enrollment

HTTPS/WSS is mandatory. There is no HTTP control mode and no application-layer-encryption fallback.

The security trust anchor is the user's out-of-band comparison of the root certificate's full SHA-256 fingerprint in the operating-system certificate details against the value shown on the TV. The HTTP enrollment page, `/ca.crt` response, mDNS result, HTTPS bootstrap response, pairing transcript, and PWA display are not independent trust anchors and must never be described as such. If M1 cannot document a practical fingerprint-verification path on any required platform, that platform fails M1.

At first setup or full trust reset:

1. The projector generates a non-exportable P-256 leaf private key in Android Keystore.
2. It creates a temporary P-256 root CA key in process memory and a self-signed root certificate valid for ten years. The root private key is never written to Android Keystore, storage, logs, diagnostics, or backups.
3. The leaf certificate contains exactly the fixed `.local` hostname and current non-global LAN IPv4 address in Subject Alternative Name, includes server-auth Extended Key Usage, and uses the validity window defined in section 6.1.
4. The temporary root signs exactly that leaf certificate.
5. The application drops all references to the temporary root key immediately after signing and never retains a serialized form. Managed runtimes cannot promise immediate byte-level zeroization, so the guarantee is non-persistence rather than instant memory erasure; M1 verifies that no root key reaches storage, logs, crash reports, backups, or diagnostics. The public root certificate remains available.
6. The TV displays both exact HTTPS origins, the full grouped SHA-256 root-certificate fingerprint, and a human-readable verification phrase derived from that fingerprint.
7. The user manually opens a five-minute enrollment window. During this window only, the projector serves the public root certificate over HTTP on the selected LAN interface at port `8080`.
8. The user opens the certificate details supplied by the operating system, compares the full SHA-256 fingerprint with the TV, and only then enables trust. The verification phrase assists transcription but does not replace the full fingerprint check.

The enrollment HTTP listener:

- Exposes only `GET /ca.crt`.
- Has no redirect, PWA, pairing, control, diagnostic, or API endpoint.
- Returns at most 32 KiB per response.
- Allows at most ten requests per minute globally.
- Closes when the user closes setup, after five minutes, or after the projector leaves the selected LAN.

The root private key is intentionally destroyed, so leaf renewal or an IP Subject Alternative Name change requires a full trust reset and re-enrollment on each client. The Setup UI warns at 60 and 30 days before leaf expiry. The root certificate deliberately omits Name Constraints: an ephemeral in-memory signer plus a single issued leaf is simpler and more portable than claiming inconsistent client enforcement. M1 confirms that the 730-day leaf works under each required user-installed trust store, including Apple's separate 825-day private-root rule.

M1 must select and record the embedded TLS server stack and X.509 construction library. The gate requires proof that the server can complete TLS handshakes using the opaque Android Keystore leaf key, that certificate construction works on API 26 and the certified API 30 device, and that the selected dependencies do not install a global cryptographic provider or require exporting the leaf key. A maintained certificate library is preferred over custom ASN.1/DER code even when it is larger; the small-dependency policy does not justify writing certificate or TLS primitives.

### 7.4 Pairing Identity

- Each browser profile creates a non-extractable P-256 ECDSA signing key using WebCrypto.
- The private key remains in browser storage. The projector stores only the public key, a user-visible device name, a key fingerprint, revocation state, and last-seen time.
- Browser storage eviction may require re-pairing; this is documented and never bypassed with a weaker credential.
- On iPhone, onboarding completes certificate trust first, then instructs the user to install and open the `standalone` Home Screen PWA before key generation or pairing. Safari and the Home Screen PWA have separate storage, and non-installed Safari storage is subject to inactivity eviction. Pairing from Safari is available only as a clearly labeled temporary mode and creates a separate device identity.
- Pairings do not expire automatically. The Setup UI identifies stale devices by last-seen time and lets the user revoke them manually.

### 7.5 Pairing Ceremony

1. The user starts pairing on the TV, which displays a random six-digit code for two minutes.
2. The PWA submits the code, a sanitized device name, its public key, a client nonce, the protocol version, and its current exact origin over HTTPS.
3. The server adds a fresh server nonce and the leaf public-key SPKI, signs the complete canonical transcript with the leaf private key, and returns both.
4. The PWA verifies the server signature, then signs the same transcript with its client key.
5. The TV and PWA independently display the same four-word verification phrase derived from the client public-key fingerprint.
6. The user compares the phrases and explicitly confirms the pairing on the TV.
7. The projector stores the public identity and invalidates the pairing code.

The mutually signed transcript binds the protocol version, exact origin, server nonce, client nonce, sanitized name, client public key, and leaf public-key SPKI. The PWA stores that SPKI with the projector identity; a full trust reset changes it and invalidates all pairings. Signed transcripts use a versioned, length-prefixed binary encoding with fixed field order and distinct `open-tv-touchpad/pairing/v1` and `open-tv-touchpad/session/v1` context labels rather than canonicalized JSON. ECDSA uses P-256 with SHA-256 and a fixed 64-byte `r || s` signature representation. Browser APIs do not expose the peer certificate or a TLS exporter, so the protocol does not claim that a root fingerprint in an application message proves which certificate the browser saw.

V1 deliberately does not add PAKE. A PAKE can protect a low-entropy pairing code and derive an end-to-end secret, but it cannot make modified JavaScript trustworthy when that JavaScript was delivered through a wrongly trusted TLS root. V1 instead makes manual CA fingerprint verification explicit, compares the client-key phrase on both screens, mutually signs the pairing transcript, and authenticates each later session with the paired keys. If M1 shows that practical CA verification is not possible on a required platform, the design stops rather than presenting PAKE as a substitute for trusted code delivery.

Pairing controls:

- Pairing is locked by default after successful onboarding.
- A pairing code may be used once.
- Five failed code attempts across all remote addresses trigger a 15-minute global cooldown.
- At most one code may be active.
- Device names use printable letters, digits, spaces, hyphens, and underscores; leading and trailing spaces are removed; length is 1-32 Unicode code points after normalization.
- Pairing state and codes are never logged.

### 7.6 Session Authentication and Replay Protection

Each WSS connection performs an authenticated ephemeral key exchange:

1. The client identifies its paired key and sends a fresh P-256 ECDH public key and client nonce signed by its long-term P-256 signing key.
2. The server verifies the client identity and revocation state, then sends a random 256-bit challenge, session identifier, protocol version, expiry time, fresh server nonce, and fresh P-256 ECDH public key.
3. The server signs the complete handshake transcript with the leaf private key. The client verifies it against the leaf SPKI pinned during pairing.
4. Both sides derive the ECDH shared secret and use HKDF-SHA-256 with the unsigned canonical transcript—both identities, both ephemeral public keys, both nonces, challenge, session identifier, version, origin, and expiry—as context to derive separate 256-bit client-to-server and server-to-client HMAC keys. The shared secret and ephemeral private keys are discarded after derivation.
5. The server accepts command sequence number `1` only after authentication, then requires each subsequent command sequence number to increase by exactly one on that ordered WebSocket connection.
6. Every command, acknowledgement, status message, and gesture result carries HMAC-SHA-256 over a direction label and its deterministic binary envelope. MAC verification occurs before any state transition or input processing.

Authentication has no bearer token or long-term shared secret in a URL, query string, cookie, local log, browser history, or storage. A reconnect creates new ephemeral keys, directional MAC keys, challenge, and sequence space; commands are never replayed across connections. Queue residence time is measured only after a MAC-validated command arrives at the server. Client wall-clock timestamps are not trusted.

Directional session MACs prevent a pure TLS-terminating relay from changing, injecting, or forging either client or server application messages after a legitimate PWA was paired. They do not reveal or authenticate the TLS certificate seen by the browser, encrypt the already TLS-protected payload, or prevent transparent forwarding, observation, delay, or dropping. M1 benchmarks handshake setup and sustained 30 Hz MAC generation and verification on every required client and the certified projector; failure is an architecture-review stop.

Revoking a device terminates all of its current sockets and lease immediately.

### 7.7 Network and Browser Hardening

- Maximum four unauthenticated sockets and eight authenticated sockets.
- Five-second authentication timeout.
- Maximum WebSocket message size of 4 KiB.
- Strict JSON decoding with duplicate-key rejection, exact field sets, bounded integers, and unknown-message rejection.
- Per-identity and global token-bucket limits for connections, authentication, lease operations, pings, and commands.
- Exact `Host` validation against the configured hostname and port.
- Exact `Origin` validation against one of the leaf certificate's two served HTTPS origins for browser connections.
- TLS 1.2 minimum, with TLS 1.3 preferred where available.
- Same-origin assets only.
- Cross-Origin Resource Sharing is disabled; the server emits no permissive CORS headers.
- Content Security Policy denies all by default and permits only the exact resources required by the bundled PWA. `connect-src` lists both `'self'` and the exact `wss://<current-origin-host>:8443` origin so Safari versions that do not map `'self'` to WSS remain covered.
- `frame-ancestors 'none'`, no cross-origin embedding, no inline script, no `eval`, and no third-party resources.
- Security-sensitive responses use `Cache-Control: no-store`.

### 7.8 Android Application Hardening

- Build configuration is `minSdk=26`, `compileSdk=36`, and `targetSdk=36` for V1.
- The normal permissions are limited to `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `RECEIVE_BOOT_COMPLETED`. Boot receipt performs work only after the user enables start-after-boot. No dangerous runtime permission is required by V1.
- The Gateway Service declares `android:foregroundServiceType="connectedDevice"`; `CHANGE_WIFI_MULTICAST_STATE` satisfies its network-device prerequisite and is used only while advertising the local service.
- All components are `exported=false` except the launcher activity and the system-bound accessibility service.
- The accessibility service is protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- No custom exported service, receiver, provider, or deep link is required in V1.
- Android backup and device-to-device transfer are disabled with `android:allowBackup="false"`, an all-excluding legacy `fullBackupContent` file, and Android 12+ `dataExtractionRules` that exclude every cloud-backup and device-transfer domain.
- The TV activity declares the Leanback launcher category, TV banner, icon, and D-pad focus behavior. No touchscreen feature is required.
- Secrets, pairing codes, signatures, raw messages, client IP addresses, and gesture coordinates are not written to production logs.
- Release builds are non-debuggable and do not trust user-added cleartext network security exceptions.
- Release signing-key custody, backup, rotation consequences, and compromised-key response are documented before M4.

## 8. Controller Lease

- Any authenticated paired device may request control.
- Exactly one identity owns the active controller lease.
- The lease lasts 15 seconds and renews every five seconds while the client remains connected and visible.
- If the holder disconnects, releases control, or fails to renew, the lease ends immediately or at expiry and pending input is discarded.
- A second device requesting a live takeover causes a TV-side prompt naming both devices. Control transfers only after TV confirmation.
- Takeover is denied while the TV cannot display confirmation.
- Transfer cancels the old controller's local input state and active drag before the new controller can issue commands.
- Pairing and controller status remain separate: a paired device does not control the projector until it owns the lease.

## 9. Protocol

### 9.1 HTTPS and WSS Route Table

No other network route is available in V1.

| Method | Route | Authentication | Purpose and cache policy |
|---|---|---|---|
| `GET` | `/` | TLS server identity | Minimal PWA shell; `no-cache`. |
| `GET` | `/manifest.webmanifest` | TLS server identity | Install metadata; `no-cache`. |
| `GET` | `/sw.js` | TLS server identity | Service worker; `no-cache`. |
| `GET` | `/app-version.json` | TLS server identity | Build ID and supported protocol range; `no-store`. |
| `GET` | `/assets/<content-hash>` | TLS server identity | Immutable same-origin PWA asset; one-year immutable cache. |
| `GET` | `/api/v1/bootstrap` | TLS server identity | Informational origin, build, certificate-expiry, and protocol metadata; `no-store`; never treated as the certificate trust anchor. |
| `WSS` | `/api/v1/pair` | Short-lived code, client proof, TV confirmation | Temporary pairing socket subject to the unauthenticated socket cap and two-minute pairing timeout. |
| `WSS` | `/api/v1/control` | Paired client challenge-response | Authenticated controller session. |

The enrollment listener on HTTP port `8080` remains separate and exposes only `GET /ca.crt` during the five-minute TV-initiated window.

### 9.2 Framing

- UTF-8 JSON text messages only.
- Protocol version `1` is negotiated during pairing and authentication.
- Handshake messages are outside the command sequence.
- Every authenticated command has exactly `version`, `type`, `sessionId`, `seq`, a type-specific `payload`, and the directional `mac` defined in section 7.6.
- A command acknowledgement means the message passed MAC, schema, sequence, session, lease, state, and rate checks and was admitted to a bounded input queue or accumulator. It does not mean Android completed the gesture.
- Discrete gestures emit a later `gesture_result` containing the originating sequence number and one of `completed`, `cancelled`, `rejected`, or `timed_out`. Coalesced movement reports stale-drop and cancellation counters in periodic session status rather than emitting one result per delta.
- A rejected command receives a small response containing `seq`, result code, and server processing time when the envelope is safe to acknowledge. Responses do not echo gesture data.
- `ping` acknowledgement round-trip time measures transport plus validation and queue-admission latency. Device tests measure gesture completion latency separately.
- Unknown versions, types, or fields are rejected without side effects.
- A structurally valid command consumes its expected sequence number even when it is rejected for lease, rate, state, or overload reasons. A malformed envelope, duplicate key, unknown field, duplicate sequence, lower sequence, or sequence gap closes the session with a protocol error; the client must reconnect rather than guess which command state the server retained.

### 9.3 Closed V1 Command Set

| Type | Payload | Processing |
|---|---|---|
| `lease_acquire` | Empty | Requests control; may require TV confirmation. |
| `lease_renew` | Empty | Renews the current holder's lease. |
| `lease_release` | Empty | Releases control and clears local input state. |
| `set_profile` | Enum `low`, `standard`, or `high` | Selects the projector-side sensitivity profile for this paired identity and persists it. |
| `move` | Bounded integer `dx`, `dy` | Coalesced in a bounded movement accumulator. |
| `tap` | Empty | Runs after already-accepted movement is applied at the server cursor. |
| `drag_start` | Empty | Starts drag at the current server cursor. |
| `drag_move` | Bounded integer `dx`, `dy` | Updates the active drag through a bounded accumulator. |
| `drag_end` | Empty | Completes the active drag. |
| `scroll` | Bounded integer `dx`, `dy` | Coalesced into bounded serialized swipe pulses. |
| `recenter` | Empty | Centers the server cursor and clears pending movement. |
| `cancel` | Empty | Cancels the sender's current input state. |
| `ping` | Random client nonce | Returns the nonce for latency measurement; rate-limited. |

Clients never provide absolute screen coordinates.

### 9.4 Bounds and Queues

- `dx` and `dy` are signed integers in `[-120, 120]` per message.
- `move`, `drag_move`, and `scroll` share a high-rate token bucket that refills at 30 messages per second and has capacity 60 so ordinary timer jitter does not reject legitimate input. The input engine still applies movement at no more than 30 batches per second.
- Movement is summed only within a 50 ms coalescing window, clamped to `[-300, 300]` per axis, and discarded if it has waited more than 100 ms before application.
- Drag movement uses the same bounds and stale-data rule.
- Scroll deltas are coalesced within 50 ms, clamped to `[-240, 240]` per axis, converted to one serialized swipe pulse, and discarded if older than 150 ms.
- Discrete commands use a FIFO with capacity 32.
- Lease and protocol-control commands use a reserved FIFO with capacity eight and independent rate limits.
- Overflow rejects the newest remote command with `overloaded`; it never grows a queue.
- Socket disconnect, lease loss, accessibility cancellation, and service shutdown are local lifecycle events, not enqueued remote commands. They synchronously clear accumulators and drive drag cancellation.

## 10. Input and Gesture Semantics

### 10.1 Cursor

- The projector owns the authoritative cursor position.
- Relative movement is transformed by the selected sensitivity preset and a conservative bounded acceleration curve.
- V1 provides three sensitivity presets: Low, Standard, and High. Per-axis tuning is deferred.
- Cursor coordinates are clamped to the physical display bounds.
- The overlay is non-focusable and non-touchable and contains no application content.
- The cursor appears when an authenticated lease holder moves it and hides after five seconds of input inactivity or immediately when the lease ends.

### 10.2 Tap

- A tap applies any movement already accepted from the same sequence stream before selecting the current server cursor position.
- It dispatches one bounded-duration accessibility gesture.
- A rejected or timed-out gesture is reported to the client and is not retried automatically.

### 10.3 Drag

- Drag is an explicit state machine: Idle, Starting, Active, Ending, or Cancelling.
- Only one drag may exist.
- `drag_start` begins at the current server cursor.
- `drag_move` extends the same continuing gesture where Android supports continuation.
- `drag_end` explicitly completes the stroke.
- The drag automatically cancels after two seconds without a drag update or after 15 seconds total.
- Disconnect, revocation, lease loss, takeover, accessibility cancellation, or service shutdown forces local cancellation before another gesture begins.
- A client cannot start a tap, scroll, or second drag while a drag is active.

### 10.4 Scroll

- Two-finger PWA motion becomes a one-axis-dominant relative scroll command.
- The projector accumulates short-window deltas and dispatches bounded swipe pulses with at least 40 ms between pulses.
- Vertical and horizontal scroll are supported.
- Natural and inverted scroll direction is a local PWA preference.
- Scroll does not attempt pinch, inertial fling emulation, or multi-touch injection.

### 10.5 Serialization

- Accessibility gestures are dispatched one at a time.
- The next gesture waits for Android's completion or cancellation callback.
- A gesture callback timeout cancels the input state and enters a degraded state until a health check succeeds.
- There is no automatic retry because retrying could duplicate an input.

## 11. Errors and Recovery

The UI distinguishes these states and provides one actionable next step:

- Projector not found.
- Certificate not installed, not trusted, mismatched, expired, or issued for a previous IP address.
- Gateway stopped.
- Accessibility service disabled.
- Pairing locked, code expired, code incorrect, or cooldown active.
- Device revoked or browser key unavailable.
- Another controller active or takeover awaiting TV confirmation.
- Input overloaded or rate-limited.
- Gesture rejected, cancelled, or timed out.
- Network changed or projector hostname unavailable.
- PWA build incompatible with the projector protocol.

Reconnect uses capped exponential backoff with jitter, from 250 ms to 10 seconds. A reconnect authenticates a new session, starts sequence numbers at one, and never replays prior commands. The PWA shows a disconnected state before accepting new touch input.

Every gateway build has an immutable build ID and supported protocol range. The service worker caches only content-hashed assets, checks network-first `/app-version.json`, and never serves a cached bootstrap response. When WSS reports `upgrade_required` or the build ID changes, the PWA disables control, asks the browser to update the service worker, and shows **Reload to update** after the new worker is ready. It never reloads automatically during an active lease or drag. A protocol mismatch cannot fall back to an older command format.

## 12. Milestones and Stop Gates

### M0 — Hardware Gesture Gate

Build the smallest disposable Android test APK necessary to validate the certified projector. Initial installation may use ADB over the local network or USB, but ADB is disabled after installation testing.

Pass conditions:

- User can manually enable the accessibility service.
- Accessibility overlay cursor appears over Netflix browsing UI and playback controls.
- Accessibility-dispatched tap, vertical scroll, horizontal scroll, and drag work in `com.netflix.mediaclient`.
- Forced disconnect and cancellation do not leave a stuck press.
- Playback remains usable at 1080p while the overlay is active.

If any required gesture fails in Netflix after one documented compatibility investigation, implementation stops. Testing another application or changing the product target requires explicit user approval; success elsewhere does not satisfy M0.

### M1 — Local Trust and PWA Gate

Build the minimum certificate, HTTPS/WSS, mDNS, signed-command, and PWA-installation spike.

Pass conditions:

- The user can view and compare the full root fingerprint in the operating-system certificate details on every required client platform.
- The ephemeral in-memory root signs one leaf, and no root private-key material remains after setup.
- Each browser validates the leaf for both the fixed `.local` hostname and the initial private IP address.
- The custom mDNS responder publishes the chosen hostname on Android TV 11, survives gateway restart, releases its multicast lock, and does not conflict with Android NSD or ADB mDNS.
- Android Chrome completes all PWA and WSS flows through `.local`; if not, the IP-SAN fallback and documented DHCP-reservation workflow complete them.
- M1 selects and records the embedded TLS server and certificate-construction library, including why rejected candidates failed. Opaque Android Keystore TLS use is proven on API 26 and the certified device.
- HTTPS, WSS, service worker, PWA installation, and non-installed browser mode work.
- A certificate mismatch produces a hard failure, not an insecure fallback.
- The 730-day leaf, server-auth EKU, Subject Alternative Names, and 24-hour `notBefore` backdating validate on every required platform.
- SNTP and manual-clock fallback behavior are tested.
- Browser key creation and persistence behavior are recorded, including separate Safari and Home Screen PWA storage.
- The authenticated ECDH handshake and sustained 30 Hz bidirectional MAC processing remain within the latency and CPU limits.
- A stale PWA build reaches the explicit **Reload to update** path and cannot control an incompatible protocol.

If any required platform cannot perform practical fingerprint verification or use either certified local HTTPS origin reliably, implementation stops and the architecture is reviewed. V1 does not fall back to HTTP control.

### M2A — Secure Vertical Slice

- Implement the foreground gateway, selected TLS/certificate stack, minimal installable PWA, authenticated ECDH session framing, directional message MACs, and a bounded move/tap path into the M0 accessibility runtime.
- A compile-time debug-only pre-provisioned client identity may replace the pairing ceremony for this milestone. Release builds fail if that capability is enabled or any test key is packaged.
- Demonstrate an end-to-end touchpad before building the full identity and gesture system.

### M2B — Production Identity and Leases

- Implement certificate lifecycle, origin handling, installed-PWA onboarding, identity storage, pairing, revocation, session authentication, command verification, leases, and confirmed takeover.
- Remove the debug identity from all non-test flows and add protocol conformance vectors for pairing and sessions.

### M2C — Complete Input Engine

- Implement persistent sensitivity profiles, acceleration, movement coalescing, tap ordering, drag, two-axis scrolling, recenter, overload behavior, cancellation, status, and recovery.
- Complete the touchpad UI, diagnostics, service-worker update path, and end-to-end happy-path tests.

### M3 — Hardening

- Complete malformed-message, replay, flood, slow-client, reconnect, race, drag-cancellation, and soak testing.
- Complete Android exported-component, backup, logging, and release-build checks.
- Run the full device and browser acceptance matrices.

### M4 — Open-Source Release

- Publish setup, trust-reset, update, revocation, troubleshooting, privacy, compatibility, security-reporting, and signing-key documentation.
- Produce a signed APK, SHA-256 checksums, Software Bill of Materials, source archive, and reproducible-build instructions.
- Publish through GitHub Releases first. F-Droid packaging is a later release task after V1 proves stable.

## 13. Quantitative Acceptance Criteria

All criteria apply to the certified projector and a normal home Wi-Fi network without artificial packet loss unless a test states otherwise.

### 13.1 Input Quality

- Tap target error is at most 10 physical pixels at 1920x1080 across a 5x5 test grid.
- The server accepts and applies pointer updates at 30 Hz without queue growth during continuous movement.
- Vertical and horizontal scrolling remain controllable across ten repeated long-list trials without a single delta producing movement greater than the configured maximum pulse.
- Sensitivity presets remain monotonic: Low moves less than Standard, and Standard moves less than High for the same input trace.
- One hundred forced disconnect, revocation, takeover, and timeout trials produce zero stuck touches.

### 13.2 Latency and Recovery

- Queue-admission acknowledgement round-trip latency is at most 80 ms at p95 and 150 ms at p99 over a 10-minute, 30 Hz test. This metric does not claim Android gesture completion.
- Discrete tap completion from client send to Android completion callback is measured separately and reported for M0 and M3; V1 release requires p95 at or below 150 ms on the certified setup.
- A previously paired client reconnects and becomes ready to request control within five seconds after ordinary phone lock/unlock or Wi-Fi interruption, excluding TV confirmation time.
- A lost controller lease blocks all subsequent input from that session within one command-processing cycle.

### 13.3 Stability and Resource Use

- Two-hour soak during 1080p playback completes without process crash, ANR, gesture deadlock, or service loss.
- Resident memory after the first ten warm-up minutes grows by no more than 15 MiB over the remaining soak.
- Idle app CPU averages no more than 1% of one core over five minutes.
- Continuous 30 Hz pointer control averages no more than 10% of one core over ten minutes during 1080p playback.
- Every queue remains at or below its declared capacity under flood tests.
- No wake lock remains held after the gateway stops.

### 13.4 Security

- An unauthenticated client causes zero accessibility gesture dispatches across malformed-message and flood suites.
- Replayed authentication transcripts and duplicate, lower, skipped, or cross-session command sequence numbers are rejected.
- Changing any MAC-protected command or server-message field, payload byte, direction label, session identifier, or sequence number causes MAC rejection and zero state transition or gesture dispatch.
- Revocation closes all affected sockets and removes their leases within one second.
- Ten thousand generated malformed protocol inputs produce no crash, ANR, unbounded allocation, secret-bearing log entry, or gesture.
- Slow-client tests cannot consume more than the configured socket caps.
- Static manifest checks find no unintended exported component or backup path.

## 14. Test Strategy

### 14.1 Unit Tests

- Canonical transcript encoding and signature verification.
- Certificate metadata and lifecycle decisions.
- Pairing-code generation, expiry, cooldown, and global limits.
- Device-name normalization and validation.
- Session sequence and replay rules.
- Handshake transcript encoding, ECDH/HKDF test vectors, and directional message-MAC verification.
- Lease acquisition, renewal, expiry, release, takeover, and revocation.
- Delta coalescing, clamping, stale-data discard, and queue overflow.
- Cursor transformation and display-bound clamping.
- Drag and scroll state machines, including every cancellation path.
- Protocol schema and acknowledgement mapping.

### 14.2 Integration Tests

- HTTPS/WSS origin, host, and certificate validation.
- Exact route-table enforcement; every unlisted route and method is rejected.
- Custom mDNS hostname publication, collision handling, multicast-lock lifecycle, and IP-origin fallback.
- Authentication and pairing happy paths and failures.
- Concurrent pairing, authentication, lease, and takeover races.
- Fragmented, duplicate-key, oversized, unknown, malformed, and slow WebSocket messages.
- Global and per-identity rate limits.
- A 60-message movement burst followed by a sustained 30-per-second stream remains within bounds without rejecting timer jitter; sustained excess traffic is rate-limited.
- Gateway restart on network change.
- Accessibility unavailable, rejected, cancelled, and timed-out gestures.
- Browser reconnect without command replay.
- Service-worker build mismatch and explicit reload-to-update recovery.

### 14.3 Device Tests

- M0 gesture checklist in Netflix browsing and 1080p playback.
- Display bounds, overlay visibility, and cursor accuracy.
- Reboot, long idle, Wi-Fi loss, IP change, and service restart.
- Repeated enable/disable of accessibility and boot-start preference.
- Memory, CPU, latency, flood, forced-disconnect, and soak criteria.

### 14.4 Browser Tests

Run certificate enrollment, full fingerprint comparison, PWA installation, key persistence, pairing, control, background/foreground, lock/unlock, reconnect, update, and revocation on every required client/browser combination. On iPhone, test Safari and the installed Home Screen PWA as separate storage identities and verify that onboarding pairs from the installed PWA by default.

## 15. Repository Structure

After this written specification and the implementation plan are approved, create a new Git repository named `open-tv-touchpad` with this top-level structure:

```text
android/                 Android TV application
web/                     PWA source
protocol/                Versioned schemas, fixtures, and conformance vectors
docs/                    Architecture, setup, troubleshooting, and threat model
SECURITY.md              Reporting policy and supported versions
BACKLOG.md               Prioritized, versioned future work
LICENSE                   MIT license
README.md                 Scope, status, setup entry points, and safety boundaries
```

Android, web, and protocol code remain independently testable. Generated artifacts are not committed unless required for reproducible builds or package-manager locks.

## 16. Dependency and Release Policy

- Prefer platform APIs and small, auditable dependencies.
- TLS and X.509 are exceptions to dependency minimization: use a maintained implementation selected by M1, never custom TLS, ASN.1, DER, certificate validation, PAKE, or signature primitives.
- Pin build tools, package locks, GitHub Actions, and third-party actions to immutable versions or commit SHAs.
- Verify the Gradle wrapper distribution checksum.
- CI runs unit, integration, protocol-conformance, lint, formatting, dependency, secret, manifest, and release-signing checks.
- A release cannot publish from a debug or unsigned build.
- Release artifacts include a signed APK, checksums, SBOM, source revision, and build instructions.
- Dependency and vulnerability findings are triaged before release; unresolved high or critical findings block release unless proven inapplicable and documented.
- The project does not copy GPL code. Upstream projects are credited only for ideas or code actually reused under compatible terms.

## 17. Documentation Requirements

V1 documentation includes:

- Plain-language installation and one-time certificate trust guides for each required client platform.
- Manual accessibility enablement and how to disable it, including Android 13+ **Allow restricted settings** guidance for sideloaded builds.
- Pairing, controller takeover, revocation, trust reset, and update procedures.
- Explanation that the tool controls touch-oriented applications and does not bypass DRM, account restrictions, geographic restrictions, or application licensing.
- Troubleshooting for certificate fingerprint comparison, certificate expiry, changed IP SAN, `.local` resolution, Android Chrome IP fallback, DHCP reservation, Wi-Fi isolation, accessibility, browser storage eviction, stale PWA updates, and gesture rejection.
- Privacy statement covering data stored on the projector and browser.
- Threat model and security architecture.
- Responsible disclosure process in `SECURITY.md`.
- Compatibility matrix distinguishing certified, tested, and community-reported devices.

## 18. Versioned Backlog

The repository tracks this backlog in `BACKLOG.md`. Items move into a release only after their security and platform behavior are designed and approved.

### V1.1 — Everyday Controls

- Length-capped text entry with explicit focus and no text logging.
- Optional voice-to-text performed on the client, with clear microphone permission and privacy behavior.
- Back and Home through Android accessibility global actions.
- Play/Pause through a separately verified media-control path.
- Privacy-safe diagnostic export with explicit user review before sharing.
- Expanded device and browser compatibility matrix.

### V1.2 — Application Navigation

- Application launcher using dynamically discovered, TV-launchable activities only.
- Explicit allowlist and TV-side confirmation for launchable applications.
- No raw package-name, intent, URL, or shell command from a remote client.

### V1.3 — Optional Hosts and Setup

- Optional Windows or Raspberry Pi relay for users who cannot host the service on the TV.
- Optional setup-time ADB helper for installation and diagnostics only; never a runtime control path and never exposed outside the local trusted network.

### Future Evaluation

- Fire TV support.
- Additional Android TV vendors and versions.
- Advanced sensitivity curves and per-axis tuning.
- Adaptive update rate above 30 Hz after measured device compatibility.
- F-Droid packaging and maintenance process.

## 19. Explicit Design Decisions

1. Projector-hosted PWA is the V1 architecture; laptop-hosted relay is deferred.
2. HTTPS/WSS with user-installed local trust is mandatory; HTTP control is forbidden. Manual comparison of the operating-system certificate fingerprint to the TV is the server trust anchor.
3. The temporary root private key exists only in process memory and is destroyed after one leaf issuance; renewal or IP SAN change is a full re-enrollment.
4. Relative input and a closed command set replace arbitrary ADB, shell, intent, URL, and package commands.
5. Accessibility gesture dispatch is the only V1 input backend.
6. Netflix success on the certified projector is a hard M0 gate.
7. All queues, accumulators, sockets, messages, and rates are bounded.
8. One paired device controls at a time; a live takeover requires TV confirmation.
9. Pairings are manually revoked rather than silently expiring.
10. V1 uses a fixed 30 Hz application rate, a 60-message rate-limit burst, and three projector-side sensitivity presets selected by a closed enum command.
11. Release starts with GitHub Releases; Play Store is out of scope and F-Droid follows later.
12. Screen mirroring remains a separate project.
13. A custom mDNS responder is tested for the stable hostname; an exact private-IP certificate SAN and DHCP-reservation workflow provide the Android Chrome fallback.
14. Pairing and ephemeral session handshakes are signed; every later application message is protected by a directional session MAC. V1 does not claim to detect transparent relays or use PAKE as a substitute for trusted PWA delivery.
15. The embedded TLS server and certificate library are selected by M1 evidence before M2 begins.

## 20. Remaining Risks

- The certified firmware may reject accessibility-dispatched gestures that it accepts through ADB; M0 resolves this before product construction.
- Protected video surfaces may obscure the accessibility overlay during playback; M0 records whether control remains usable.
- Android Keystore TLS integration and certificate-generation behavior vary by server stack and vendor; M1 selects the stack and verifies the exact projector.
- Local root installation, fingerprint visibility, and `.local` behavior vary by browser and operating system; M1 is a hard cross-platform gate with an IP-origin fallback, not an HTTP fallback.
- A user who trusts the wrong root can receive malicious PWA code that uses the browser-held client key. Neither pairing signatures nor PAKE can repair that trust failure inside the compromised JavaScript origin.
- The IP fallback is tied to the certified address. Without a DHCP reservation, an address change requires full trust reset and re-pairing for that origin.
- Browser storage eviction can remove a client key; the recovery is explicit re-pairing.
- Android may stop a long-idle foreground service under vendor power management; the device suite measures and documents the behavior rather than hiding it with an indefinite wake lock.
- Sideloaded APK updates depend on stable release-key custody. A lost key requires uninstall/reinstall and local data loss; M4 documentation must make this explicit.

## 21. Primary Platform References

- [Android `NsdManager` and multicast-lock behavior](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [Android `NsdServiceInfo` capabilities](https://developer.android.com/reference/android/net/nsd/NsdServiceInfo)
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 12 backup and `dataExtractionRules` changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore)
- [Android restricted settings guidance](https://support.google.com/android/answer/12623953)
- [WebKit storage policy](https://webkit.org/blog/14403/updates-to-storage-policy/)
- [WebKit Home Screen storage isolation and seven-day-cap behavior](https://webkit.org/blog/10218/full-third-party-cookie-blocking-and-more/)
- [Apple manual root trust procedure](https://support.apple.com/en-us/102390)
- [Apple TLS certificate requirements, including the 825-day rule](https://support.apple.com/en-my/103769)
- [Apple's later 398-day public-root limit and user-added-root exception](https://support.apple.com/en-us/102028)

