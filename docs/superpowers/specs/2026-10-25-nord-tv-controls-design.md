# N3: Nord unified TV controls

Status: approved security/platform contract. Owner selected the earlier N3 plan
after considering and declining the separate Google TV companion approach.
Approved 2026-10-25. This makes the N3
boundary in the accepted direct-remote design concrete. The completed0.7 UI and
its owner smoke remain the baseline (`b26c05c`, evidence `b0d0829`).

## Outcome and selected direction

Operate the XGIMI from the Nord: existing Bluetooth mouse/tap/scroll plus Home,
Back, D-pad/OK, volume up/down, play/pause and Netflix in the same native screen.
No Mac, router configuration, CA installation, account or projector app is needed.
The phone and projector must be reachable over the local Wi-Fi for TV keys;
Bluetooth continues carrying mouse input. Network isolation can block TV keys.

The owner selected Nord-direct operation and asked to proceed with these TV
controls. Builder recommendation: narrowly port the already reviewed Mac Remote
v2 protocol behavior into the current Java/native app, using Android TLS/Keystore
and generated protobuf messages. This is not reuse of Python code on Android.

Alternatives considered:
- A complete third-party Android remote: would replace our tested HID/lifecycle
  integration. The inspected candidate advertises reconnect and unverified server
  TLS defaults incompatible with our contract; it is not selected as a dependency.
- Bluetooth keyboard/consumer reports: unverified key mappings, no established
  Netflix shortcut, and another HID descriptor/re-pairing change. Deferred.
- Mac/PWA bridge: reverses the owner's Nord-direct decision. Deferred.

N3 adds INTERNET solely for outbound Remote v2 TLS sockets to an owner-entered
RFC1918 IPv4 address, fixed ports6467(pairing)/6466(control). No listener, HTTP,
ADB runtime, DNS, discovery scan, multicast, cloud, analytics or arbitrary URLs.
An address is a locator, never identity. Editing it while stopped preserves the
TV certificate pin and requires explicit Connect. Thus DHCP changes do not need
a router reservation. Automatic discovery is a later usability increment.

## Trust and durable identity

Bluetooth enrollment/version2, reportID1/five bytes and its existing OS-bond
assumption stay unchanged. Bluetooth identity and TV TLS identity are independent;
the app cannot cryptographically prove both identify the same physical box.
The owner checks the intended projector when pairing and during combined tests.

Remote v2 pairing is an explicit, temporary bootstrap exception to normal pinned
TLS. Only the pairing state/port may accept an otherwise untrusted peer certificate;
it has no TV-control API. Display a masked, six-hex-character field inside this
app and enter only the code shown by the intended projector. No code in chat,
logs, saved-instance state, clipboard integration or persisted preferences.
Disable field state-saving/autofill, clear on submit/cancel/Stop/pause, and avoid
raw exception/protocol logging. No app-controlled screenshots of pairing UI.

Validate the established protocol's code binding against the actual pairing TLS
session's client/server RSA public keys, then require its success response. Capture
that session's exact server DER certificate. Before durable enrollment, establish
a control-port TLS connection that verifies that exact certificate and complete
the remote handshake without sending any input. Only then atomically save the
record and read it back. Failed code, timeout, unexpected certificate or failed
storage never enables commands or stores a new trusted peer.

This is the existing short-code Remote v2 ceremony, not a new PAKE or a claim of
strong resistance to every active enrollment relay. Its checksum is short; do
not equate initial pairing with independently checking a full fingerprint.
Post-enrollment pinning prevents silent certificate replacement. LAN attackers
can still cause denial of service; user-approved pairing to the wrong display
can enroll the wrong TV. The phone/TV operating systems and TV service are trusted.

Runtime uses platform TLS1.2/1.3, native X.509 parsing/validation and an explicit
exact-leaf certificate check during the handshake, before any remote message.
The peer must satisfy validity/algorithm checks as well as the exact pin. No
trust-all runtime manager, hostname-verification bypass as a substitute for a
pin, accepted child certificate, certificate rollover or silent re-enrollment.
The TV's self-signed identity is checked by certificate, not an IP hostname/SAN.
An expired/changed certificate blocks TV controls and explains the need for
explicit Forget TV controls and fresh on-TV pairing. No permissive fallback.

One RSA2048 client identity resides in AndroidKeyStore; generate its certificate
using native KeyGenParameterSpec, not custom ASN.1 or a new X.509 library. Allow
only TLS/signing operations needed by the proven provider; no exportable PEM
fallback. Prove usable self-signature and mutual TLS on API31 first. Hardware
backing is not assumed or advertised. Key loss/invalidation requires explicit
re-pairing, never a replacement identity silently used with an old enrollment.

A separate bounded AtomicFile in noBackup stores format version, locator,
client-key alias/certificate fingerprint and exact TV certificate (maximum16KiB
record). Never alter the existing Bluetooth enrollment file. Malformed/oversized,
missing-key or mismatched-client records fail closed. The TV store has three
explicit outcomes: ABSENT (missing/verified empty record), VALID, and UNUSABLE
(corrupt/unsupported/oversized/I/O error, missing or invalid client key). Only
ABSENT permits HID-only Connect or fresh Pair TV. UNUSABLE blocks both Connect
and Pair TV until successful explicit Forget TV controls; it must not be treated
as ABSENT or silently fall back to HID-only. Stop/recovery remain available. A
failed Forget remains UNUSABLE for the process. Do not copy the Bluetooth store
null-peer convention or alter its existing behavior. Record persists identity,
not connection/readiness or queued commands. Backup/transfer exclusions remain.
Pairing is unavailable while a TV enrollment exists; the owner must explicitly
forget it before replacing it. There is at most one fixed app-owned key alias.
Before fresh pairing, clean up only an unreferenced app-owned candidate alias;
a cleanup failure blocks pairing rather than leaking unbounded identities.

Forget TV controls first stops both channels, then writes/verifies an empty
record before deleting the app-owned key. A crash between these steps cannot
restore enrollment; leftover key alone never authorizes a peer. A failed write
latches TV controls off and reports removal unconfirmed, retaining the known
limitation that old disk state might remain after restart. Do not claim successful
revocation until the empty record is verified. Forget Bluetooth remains separate.
Neither action edits Google TV's credentials or the projector's other remotes.

## Session ownership and integration

Keep current five-minute foreground-only diagnostic, explicit Start and Connect,
process-wide native-owner fencing and teardown barrier. Start continues to require
Bluetooth enabled and the existing permissions/service/HID registration. A TV-only
mode with Bluetooth off is not added. Do not extend the deadline
for TV pairing, reconnect or setup. Network work must never block the main thread.

First-time sequence: retain/enroll Bluetooth as today, Stop, Start a new run,
open Setup and Pair TV, enter the displayed code, save successful TV enrollment,
and finish that run. Then Start -> Connect explicitly establishes both channels.
Pair TV is allowed only with Bluetooth already enrolled and no HID connection or
connection attempt in that run. Pairing must not cause a hidden input session.
Once a TV operation starts, any Activity pause stops it; returning cannot resume
pairing or restore input. Native keyboard entry within the Activity remains usable.

Before TV enrollment, explicit Connect keeps existing HID-only behavior with a
clear 'TV controls not paired' status. After enrollment, Connect means combined:
verify TV TLS/readiness first, then connect the saved Bluetooth peer, and enable
all remote controls only when both are ready. One attempt per run. Any pairing,
connect or active-channel failure stops the whole run and identifies the failed
channel. No silent fallback to HID-only after a combined failure. To return to
HID-only operation, explicitly Forget TV controls while stopped.

ProbeActivity.Run owns the network connection just as it owns HID. Add a small
TV protocol/transport owner with immutable run identity; no second independently
surviving service. Pure protocol and admission helpers remain testable without
Android. RemoteScreen composes views; it never owns sockets or trusted storage.
Extend NativeRunGate with a TV resource. Do not release its slot until the owned
socket and workers are closed. Cancellation that cannot terminate key generation
also retains ownership; a late result may clean up but may not enroll. A stalled teardown remains disabled and reports
unconfirmed cleanup, as the existing Bluetooth path does.

Stop/Close, qualifying foreground loss, five-minute expiry, HID loss, TV loss, service loss,
Forget and disposal synchronously fence new admission and cancel the pad, then
close network resources and run existing HID neutral/teardown. Workers post only
generation-tagged results; stopped/stale owners cannot change enrollment, enable
controls or connect another peer. Serialize enrollment finalization/Forget so a
late pairing completion cannot resurrect a revoked record. Preserve the existing
no-input pre-confirmation Bluetooth system-UI pause exception only while no TV
operation has begun. Once Bluetooth Connect/confirmation or any TV operation
begins, pause terminates the run and return requires explicit Start. Returning
from initial Bluetooth discoverability/numeric comparison may retain setup
evidence as today, but never enables input while covered. No replay, retry or
background reconnect.
Network dispatch independently rechecks the shared monotonic run deadline and
foreground/cancellation latch immediately before writing; delayed main-thread
timers or sleep/wake must not extend admission. Never funnel an unbounded stream
of peer messages into UI callbacks; coalesce status updates per owned run.
A command already handed to the OS before Stop may have reached the TV; do not
promise to retract it. Successful local write is not TV delivery acknowledgment.

## Bounded protocol and input

Use reviewed schemas from androidtvremote2 v0.3.2 with licenses/source commit and
hashes recorded. Generate Java lite messages with matching protobuf compiler/runtime.
Proposed supported line: protobuf-javalite4.36.2/protoc36.2, checked against current
upstream release/support records. Before adoption, record exact artifacts, hashes,
license and advisory results in a dependency ADR. No hand-written protobuf parser,
TLS implementation, certificate encoder or novel cryptographic construction.

Use only the required handshake, ping, key and app-link protocol behavior; no
voice, IME/text, microphone, clipboard, power or arbitrary launch input. Ignore
bounded informational messages without displaying/persisting their app/device
metadata. Unexpected pairing-state messages fail; no generic command escape hatch.

Builder limits to verify, not claims already measured:
- One TLS socket at a time, replacing pairing with verified control sequentially.
- Maximum64KiB frame, five-byte length prefix and protobuf recursion limit16;
  reject malformed/overlong length, oversized/truncated frame or parser error.
- Connect/handshake12s; each pairing exchange10s; human code window120s bounded
  by the original five-minute run; control idle timeout30s refreshed by valid
  traffic; stalled write2s closes the socket. Stop closes blocked I/O promptly.
- One bounded reader, one serialized writer and a bounded control/watchdog context
  per run. No unbounded executor or main-thread message queue. Read admission
  max100 frames/s with burst200; overflow stops instead of allocating callbacks.
- At most one pending user command, latest age<=250ms checked before writing;
  user submissions at most10/s, excess dropped. Fixed commands only; no key-down
  holds or autorepeat. Protocol replies bounded separately (at most eight); a
  flood/backlog stops instead of starving Stop or retaining stale user commands.
- Socket closure/watchdog and generation cancellation are independent of the
  writer thread, including a blocked write. A stale timeout cannot stop a new run.

Control enum: UP19, DOWN20, LEFT21, RIGHT22, OK23, HOME3, BACK4, VOLUME_UP24,
VOLUME_DOWN25, PLAY_PAUSE85, all RemoteDirection.SHORT. Netflix sends only the
previously working `nflx://www.netflix.com/browse` app link. No other URI accepted.
Protocol support is checked before exposing a control; unsupported required
features block N3 acceptance instead of making dead buttons appear functional.

## UI changes

Retain0.7 touchpad, scroll controls and fixed Stop/Close. Add a compact labeled
TV-controls section with real D-pad/OK, Home/Back, volume, play/pause and Netflix.
Show separate Mouse and TV states plus clear combined readiness. Native controls
retain48dp targets, focus, labels and enlarged-text/landscape scroll reachability.
TV-button DOWN cancels pending pad motion/tap as scroll buttons already do;
keyboard/accessibility activation cancels too. Setup contains the TV locator,
Pair TV/code/confirm, and separate Forget TV controls. Editing is stopped-only.
No enabled-looking placeholders. Setup toggling is local and does not reconnect.

## Delivery gates and acceptance

1. **N3a platform/protocol gate:** dependency/license/advisory lock; pure framing,
   pairing transcript, code validation and fixed-command tests. After independent
   review, a same-signature update to the stopped Nord may add INTERNET to this
   opt-in debug diagnostic for an androidTest-only loopback TLS fixture before
   N3b. Test code alone creates one loopback listener on an ephemeral port and
   connects locally; runtime TV address validation remains RFC1918/fixed ports.
   No LAN listener, TV discovery/connection/input or live pairing occurs in this
   fixture. Generate independent fixture identities under a bounded test-only
   Keystore alias prefix; never read/write the TV or Bluetooth enrollment stores,
   use their keys, or export a native key. Close fixture sockets, delete its test
   aliases and uninstall the test APK afterward. Verify enrollment is unchanged
   around installation/testing and preserve0.7. Production APK contains no fixture
   server, test trust manager or fixture credential path.
   The fixture proves native Keystore client auth, exact-pin handshake rejection,
   expired/wrong/child certificates and pairing/control mismatch. Test malformed,
   truncated and oversized peer data, deadlines, cancel/blocked I/O, no secret
   logs, corrupted/key-missing storage and failed Forget, including that UNUSABLE
   cannot select HID-only or Pair TV. Validate API31 Keystore
   behavior on the Nord without input. If native TLS/key support fails, stop and
   return to design, never export the private key as a workaround.
2. **N3b integration:** retain INTERNET only inside the opt-in debug diagnostic;
   bind real buttons, combined admission and lifetime. Unit and instrumented
   checks cover Stop/Close/pause/expiry, stale callbacks, storage races, pending
   input cancellation, separate pairing steps and no authorization after return.
   Preserve the existing covered initial-Bluetooth-pairing regression and add
   its TV-operation counterpart, which must stop on pause.
   Re-run existing101 unit tests and relevant native layout tests. Independent
   code/security review before installation. Preserve0.7 source/APK and enrollment.
3. **N3c owner hardware acceptance:** new TV-code pairing in our app, then saved
   reconnect and process restart without re-pairing; preserve HID bond. Verify
   Home/D-pad/OK, Back, volume-/+, play/pause on owner-chosen content, Netflix launch
   with both channels still usable after20s, mouse click/scroll and profile target.
   Check Stop and physical remote, background/return, expiry, deliberate Wi-Fi loss
   and Bluetooth loss each stopping both without silent reconnection. Prove changed
   locator with same certificate works in fixtures; hardware address change is
   NOT RUN unless observed. Record source/APK/firmware and each actual result.

No daily-use lifecycle, cross-device bridge, release distribution, H1A/H1B PASS
or unrelated product gate follows from N3. Automated proofs and owner observations
are separately recorded. N4 remains the later lifecycle/reliability contract.

## Sources inspected

- Local reviewed `experiments/hub_remote/tv_remote.py` and TLS fixture tests;
  local `ProbeActivity`, `NativeRunGate`, UI and accepted direct-remote design.
- https://github.com/tronikos/androidtvremote2/tree/v0.3.2
- https://raw.githubusercontent.com/tronikos/androidtvremote2/v0.3.2/src/androidtvremote2/pairing.py
- https://raw.githubusercontent.com/tronikos/androidtvremote2/v0.3.2/src/androidtvremote2/remote.py
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
- https://protobuf.dev/support/version-support/
- https://github.com/protocolbuffers/protobuf/releases
- https://github.com/theharisshah/androidremote (alternative only, no code adopted)

Dependency/provider suitability is a gated implementation result, not inferred
from documentation. No N3 platform or hardware result is implied by design approval.

## Independent design review disposition

A read-only Codex subagent traced the existing Activity/session/service/native
owner and reviewed this contract. Three concrete gaps were corrected: explicit
ABSENT/VALID/UNUSABLE TV enrollment states to prevent silent HID-only fallback;
a reviewed N3a-only instrumented loopback fixture/install/permission path; and
preservation of the no-input initial Bluetooth system-pairing pause exception.
Scoped re-review confirms all three resolved and the design ready for owner
approval, subject to the N3a platform gate. No unresolved actionable design finding
was reported. This is design review only, not TLS, implementation or hardware PASS.
