# H1B — Browser trust and authorization gate

Status: **NOT PASSED**. Browser-only code is implemented and its scoped reviews are complete;
actual OS trust/Keychain and supported-browser evidence remains incomplete.
No projector backend is composed. H1A remains independently NOT PASSED.

## Local evidence (2026-10-24)

- Pinned dependency metadata/license inspection and OSV querybatch:17 exact
  distributions, no listed advisories. Installed from SHA256-locked wheels.
- Identity fixtures exercise real OpenSSL path/constraint rejection, renewal,
  private temporary-file cleanup and denied/substituted Keychain seams. Native
  Keychain methods are guarded/mocked; no real identity/trust was installed.
- Sessions use real temporary SQLite: native approval, concurrent completion,
  capacity, hash-only persistence, expiry, revocation and lost-response recovery.
- Controller uses a recording/no-device backend for lease, ordering, bounds and
  lifecycle races. No recording-backend success is device evidence.
- Temporary loopback HTTPS tests exercise actual TLS/HTTP/WSS: origin/Host,
  CSRF, cookies, owner approval, lease, unavailable device response, passive
  session expiry, eight sockets,32 accepted connections including TLS handshakes,
  and five-second handshake/incomplete-header expiry.
- A hidden Tk smoke check constructed the owner window and correctly mapped a
  selected pending row to its native approval ID. It did not use Keychain/network.
- Browser logic uses Node fixtures for DOM, gestures, sockets and service worker;
  this is not a browser-rendering, installation or certificate-trust claim.

Current integrated verification: **103 Python tests passed** in15.203seconds
and **36 Node tests passed**. Test fixture coverage includes the review regressions
for PEM export/path validation, motion/button ordering, blocked identity work,
reconnect rate limits, slow WebSocket closure and private lease-status updates.
Runtime hashes were checked against the reports. Sessions, identity, controller, server/native and PWA/status reviews are closed. These counts establish
local code behavior, not browser/Keychain or hardware acceptance.

Code checkpoints: `39292bd` sessions, `92b8335` controller, `61ad936` identity.
Server/native checkpoint: `e7603c6`; PWA follows in the feature-branch history. No PR/push or
release claim is made by this gate report.

## Required actual-device matrix — all pending

| Surface | OS/browser version | Trust/fingerprint | Installation/storage | Lifecycle/update | Result |
|---|---|---|---|---|---|
| iPhone Safari | Not recorded | Pending | Pending | Pending | NOT TESTED |
| Installed iPhone PWA | Not recorded | Pending | Pending | Pending | NOT TESTED |
| Android Chrome | Not recorded | Pending | Pending | Pending | NOT TESTED |
| Installed Android PWA | Not recorded | Pending | Pending | Pending | NOT TESTED |
| Laptop Safari | Not recorded | Pending | Pending | Pending | NOT TESTED |
| Laptop Chrome | Not recorded | Pending | Pending | Pending | NOT TESTED |

For each required client, record actual full-fingerprint comparison using OS
certificate details, valid trust, wrong root/IP/expired/out-of-scope rejection,
same-address renewal, explicit address migration and old-root removal. Install
before enrollment and verify persistence in that installed context. Test native
Keychain denial/lock and identity recovery, expired/revoked existing sessions,
background/offline behavior and stale-worker/version mismatch.

No real CA was added to any device, no public listener was opened, no projector
settings changed, and no hardware acceptance is inferred. OS clock correction
and trusted-root installation require normal owner actions during the gate.
