# Nord HID reconnect: proposed trust decision

Status: ACCEPTED for the isolated diagnostic implementation. The owner selected
“remembering the verified projector approach” after review of the OS-bond trust
limitation. This authorizes the B diagnostic, not a network bridge or H1A PASS.

## Objective and established behavior

Make reconnection practical for the eventual unified phone/laptop remote without
using plaintext Wi-Fi ADB. The Nord provides native Bluetooth HID mouse input;
the existing Mac TV Remote v2 path provides TV keys. The Mac-to-Nord transport
and companion background lifecycle need separate designs.

Probe 0.2-diagnostic already demonstrates registration, compared Numeric
Comparison pairing, pointer movement, Netflix target activation, Stop, and
control remaining disabled after app foreground loss and adapter OFF/ON.
It deliberately forgets authorization at every stop. Successful reconnect and
held-button failure cleanup have not been demonstrated. See the
[H1A evidence](../../gates/h1a-report.md) for exact artifact and hardware details.

## Security boundary

The Android Bluetooth stack, not an app-owned password, authenticates and
encrypts the actual HID link. Reviewed Android 12 AOSP code requires
authentication and encryption for both HID channels. That is platform-policy
evidence, not verification of the exact Nord firmware or negotiated link strength.
See the [source trace](../../research/2026-10-24-protected-wireless-options.md#android-12-enforcement-trace-and-remaining-verification-2026-10-24).

On API31 the public API exposes neither a bond-key fingerprint for this app to
pin nor the newer encryption-status API. An address selects a peer; its name,
bond state, CONNECTED callback and successful mouse report do not independently
prove encryption or continuity of the originally compared key. Adding a password
between the Mac and Nord cannot change this Bluetooth boundary.

Concrete gap: while the app is stopped, someone removes and replaces the bond
for the saved peer. On restart the app sees the same address and BOND_BONDED.
It cannot reliably distinguish this from the original enrollment. A dynamic
broadcast receiver, encrypted preferences or a local enrollment signature does
not fix that missed history.

## Approaches and recommendation

| Approach | Benefit | Limit |
| --- | --- | --- |
| A. Fresh comparison after every stop/restart | Preserves the current no-retained-trust contract | Repeated manual unpair/pair; unsuitable as normal remote UX. A bounded same-process reconnect experiment can narrow continuity gaps but is not durable enrollment or independent link attestation. |
| B. OS-managed retained bond, explicit Connect | Normal remote UX using native Bluetooth security; no custom crypto | Explicitly trusts OS bond custody and authorized local bond changes while the app is absent; cannot promise app-pinned key continuity. Requires owner acceptance and vendor/platform evidence before H1A can pass. |

Decision: B, with the stated OS-bond trust boundary accepted. A describes the
previous installed diagnostic until the update is verified and installed. If continuity
of the original key despite unobserved local re-pairing is mandatory, B is not
acceptable on this API31 design; retain A and reassess hardware/platform support.
No additional basic Netflix click trials can resolve this API limitation.

## Proposed B contract

1. **Initial enrollment.** Reuse the existing fresh Numeric Comparison ceremony:
   observed pairing, matching numbers checked on BOTH intended devices, successful
   bond, and explicit confirmation in the resumed app. No Just Works, address-only
   enrollment, pairing-digit collection or automatic system-prompt approval.
2. **Stored record.** One selected peer address and enrollment format/version in
   app-private, backup-excluded storage. No keys, pairing codes, active state or
   queued input is stored. Corrupt, missing, restored or unsupported records fail
   closed. The address is a selector, not a credential. Android owns bond keys.
3. **Startup.** Controls remain disabled. Show “Saved projector — disconnected.”
   Require fresh user Connect each time; a saved record never authorizes input by
   itself. Check permission, adapter, actual HID registration and current bond.
   Missing bond invalidates enrollment. No peer substitution or discovery fallback.
4. **Explicit reconnect.** One bounded attempt to the selected peer per diagnostic
   run. Explicit Start begins a run with one fixed five-minute elapsed deadline;
   Connect cannot renew it. Stop ends the run and its service. Another explicit
   Start, including after process recreation, creates a new bounded run with
   retained enrollment but no control authorization. Fence the
   previous input generation, discard pending input, and finish old connection/
   registration teardown before creating a new attempt. Reuse existing probe
   timeouts. Connections without an outstanding explicit attempt for the selected
   peer receive no reports and are disconnected; a public callback does not prove
   which side initiated the physical link. Permit no overlapping attempts. Late
   success after Stop/timeout cannot re-enable input. Ambiguous teardown or callback
   ordering leaves input disabled, rather than recycling the old registration.
5. **Admission.** Require the current registered attempt, selected bonded peer,
   accepted connection callback, resumed Activity and live test service. Submit
   a neutral report before enabling controls; failure stops the attempt. Native
   send acceptance is not acknowledgment of delivery or proof of receiver cleanup.
   Do not label the callback or neutral report a cryptographic verification.
6. **Loss and Stop.** Immediately fence input and clear queued state; request
   neutral/disconnect/unregister where possible. Reconnection never restores a
   drag, replays commands or resumes control automatically. Stop ends control but
   retains enrollment. App foreground loss, adapter loss and process recreation
   also require explicit Start then Connect again under B. Diagnostic input stays foreground
   only; this does not authorize background control.
7. **Revocation.** Any observed pairing/re-pair request or departure from BONDED
   for the enrolled peer invalidates enrollment and stops input. Forget projector
   clears the app record and session; direct the owner to OS settings to remove
   the bond. No hidden bond-removal APIs. Unknown peers never replace the saved
   peer. Receiving no broadcast while stopped is not proof nothing changed.
8. **Accepted threat assumption.** Both OSes and their privileged
   Bluetooth components are trusted to enforce authenticated encrypted HID.
   A person or privileged component changing bonds outside the app can change
   the trust associated with its saved selector. The app does not defend original
   key continuity against that change. Ordinary LAN traffic and a spoofed device
   name/address alone must not bypass the stack's key authentication. No plaintext
   or alternate input backend is allowed when Bluetooth fails.

## Smallest implementation and proof after acceptance

Extend the existing isolated diagnostic before touching a product backend:

- `ProbeSession` owns enrollment eligibility, connection generations and input
  admission; its unit tests cover Stop/loss/late callbacks and invalidation.
- `ProbeActivity` owns public Bluetooth calls, receiver lifecycle, explicit
  Connect/Forget UI and neutral-first admission. A small app-private store owns
  the one enrollment record; manifest/backup rules exclude it from backup and
  device transfer, not from all possible extraction by privileged components.
- `PairingGuardService` retains its current bounded eligibility role; no network
  permission, background input or new service is introduced.
- Update the probe README, gate evidence, progress/capabilities and backlog with
  actual results. Unit/build/lint checks and independent change review precede
  installation. Existing callers outside the probe remain untouched.

Minimum new live proof: enroll once, Stop then explicit Start/Connect; process reopen
then explicit Start/Connect; selected-link loss with no automatic input resumption;
bond removal requiring fresh comparison; late/unknown-peer rejection where
reproducible. Record unsupported scenarios as untested, not PASS. Establish
teardown/callback ordering on the real stack; local generations alone cannot
identify every native callback from an old physical connection.

H1A remains NOT PASSED until its accepted authentication/encryption evidence,
reconnect and fault-cleanup requirements are satisfied. Platform-source evidence
must not be upgraded into exact-vendor verification by this design decision.
Held-button crash cleanup requires a separate bounded fault-test contract; the
current immediate click cannot prove it. H1B browser trust remains independently
pending. No Mac-to-Nord endpoint or product integration is part of this change.

## Design review disposition

Independent security/builder review recommended B only under the explicit OS-bond
trust assumption. Its two corrections are incorporated: a fixed deadline per run
with Stop ending the service, and backup exclusion without claiming data is
generally non-extractable. Admission now explicitly requires an outstanding owner
attempt without claiming knowledge of which side initiated the native connection.
No code or new hardware evidence follows from this review. Owner acceptance was
subsequently received as recorded at the top of this document.
