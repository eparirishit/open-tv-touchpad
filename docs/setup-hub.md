# Browser-only hub setup and gate checks

This development build supports local HTTPS enrollment and the installed remote
interface. **TV integration is locked** until protected projector transport and
browser trust gates pass. It cannot launch Netflix or control the projector.
The separate H0 Mac proof remains unchanged; this is not a replacement for it yet.

## Start the native owner window

Requirements: macOS arm64, Python3.13 with Tk, an awake Mac, and one reserved
RFC1918 IPv4 address for that Mac. Reserve the address through your router's
normal setup or use an already-managed static address. The app does not change
router settings, open ports publicly, discover devices or use `.local`.

From the repository, install the reviewed platform-specific dependencies in a
virtual environment (existing development environments can retain their H0 tools):

```sh
python3.13 -m venv .venv
.venv/bin/python -m pip install --require-hashes -r hub/requirements.lock
.venv/bin/python -m hub.main --address <reserved-Mac-IPv4>
```

Replace the placeholder; do not type angle brackets into your shell. The listener
uses port8443 on exactly that address. The default state location is the private
`~/Library/Application Support/Open TV Touchpad` directory. Other platforms need
reviewed dependency locks; Windows hosting is not implemented.

The window opens stopped. Select **Create identity** once. macOS may request
Keychain access; denying it leaves setup failed closed. The app never installs
certificate trust. Select **Export public root certificate** and choose a new
filename. The displayed full SHA-256 fingerprint belongs to that root.
Select **Start HTTPS** after the Mac's reserved address is ready.

## Trust and enroll a controller

1. Transfer only the exported public certificate using your chosen file-transfer
   method. Never transfer private keys or the hub state directory.
2. In the receiving device's certificate/profile UI, compare all32bytes of its
   SHA-256 fingerprint with the trusted native Mac display. If the UI does not
   show enough information to make that comparison, stop and record the missing
   ceremony; an app page or HTTP fingerprint endpoint is not a substitute.
3. Install/trust that root through the OS's normal controls. On iPhone, profile
   installation and enabling full trust are separate actions. Follow
   [Apple's instructions](https://support.apple.com/en-us/102390). Android UI
   varies by device/version; record the actual steps tested rather than assuming
   every phone exposes identical controls.
4. Open the exact HTTPS origin shown in the Mac window. Browser certificate errors
   must be resolved through correct address, clock, trust and certificate state.
   Do not bypass them. Install the PWA, then open its installed window **before
   enrollment**. Safari and installed iPhone apps may have separate storage.
5. Select **Open enrollment** on the Mac. Enter a short controller label in the
   installed PWA and request enrollment. Compare its six-digit code with the
   native pending row; approve that exact row on the Mac. The code matches a
   request; it is not the certificate trust anchor.
6. Once enrolled, select **Reconnect session**, then **Take control**. Another
   tab/controller must release its lease or the native owner must Stop first.
   The current build reports the TV backend unavailable and disables TV controls.

The certificate is not scoped to this app, port or home network: the same private
IP can exist elsewhere. Keep the exported fingerprint record and remove this
root from each enrolled device when retiring or replacing the hub identity.

## Recovery

- **Wrong/changed address:** stop the hub. Treat address migration as new trust
  and enrollment; do not silently widen the existing CA or add alternate origins.
  Revoke old sessions and remove the old root from every controller before
  retiring old local state/Keychain identity. Migration tooling is not shipped;
  retain state until explicit recovery is planned.
- **Certificate/clock error:** correct the clock/address or trust installation;
  never disable validation. A running hub refreshes its same-address TLS context
  hourly; certificate issuance renews at14days remaining. Keychain failure stops
  the service. Issuer expiry or signing-key loss requires explicit new trust.
- **Keychain denied or identity corrupt/partial:** retry access only after fixing
  the OS permission issue. Do not edit identity metadata or auto-delete the state
  directory: initial failure may have left an orphan Keychain item. Recovery
  needs the stored installation identifier and deliberate old-root retirement.
- **Enrollment expired/limited:** the owner opens a fresh120second window. Only
  four attempts fit a window; cancelled/invalid attempts do not refund its budget.
- **Approved response lost:** the unreachable enrollment still consumes one of
  eight controller slots. Revoke it in the Mac window and request enrollment again.
- **Page reloaded during pairing:** discard that request; the owner can cancel
  its pending row. Pair again from the installed app. Cleared browser storage
  also requires revocation/re-enrollment.
- **Busy/expired session:** release current control or use native Stop. Sessions
  expire absolutely after30days; expiry/revocation closes existing sockets.
- **Offline, hidden or updated app:** input stops; reconnect explicitly. Commands
  are never stored for replay. Stop before reloading a protocol update; close
  other app windows before activating a waiting service worker.
- **Cannot bind8443:** check the chosen address belongs to this Mac and whether
  another process occupies that exact endpoint. The app never kills another server.

## What still needs actual device evidence

No installed iPhone/Android PWA or native Keychain acceptance is certified by the
unit tests. Record device/OS/browser versions, actual fingerprint comparison,
trust/untrust, storage persistence, negative certificate tests, renewal/migration,
Keychain denial and stale-worker behavior in `docs/gates/h1b-report.md`.
Projector-link checks are separate in `docs/gates/h1a-report.md`; no protected
wireless or USB candidate has passed. Hardware input cleanup is a later H2 gate.
