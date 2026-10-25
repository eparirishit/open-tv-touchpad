# N3 unified Nord remote — TLS blocker fixed, owner pairing pending

Contract: [approved N3 design](../superpowers/specs/2026-10-25-nord-tv-controls-design.md).
The prior blocker was an explicit self-signature rejection of the certificate
presented at the owner-confirmed projector address. See the
[approved compatibility decision](../superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md).
The approved narrow fix is installed and verified on the Nord; owner pairing is pending.
Source branch `nord-tv-controls`, implementation `8763f5a` (transport `6dfd599`).
N3 owner acceptance is **PENDING**. Reviewed version0.9 was
installed with both enrollment stores unchanged and no automatic remote run.
The owner's first Pair TV attempt failed; no successful TV enrollment is verified.

## Approved compatibility fix — installed, native and live TLS verified

The owner approved the narrow exception. Version0.11 removes only the explicit
peer self-signature verification; native control validation, exact DER pinning,
validity/algorithm policy and client self-signature checks remain. A public
androidTest-only fixture uses independent signer/server RSA2048 keys with
identical names, creating a native-parsable SHA256 certificate that fails
self-verification without modifying DER or using custom certificate code.

RED: the positive native test failed at the same explicit INVALID PEER SIGNATURE
check. GREEN: all15 native TLS tests pass on the task API31 emulator, including
pairing and exact-pinned handshakes with this anchor, actual handshake rejection
for absent/wrong private keys, and exact-pin rejection of different certificate
bytes with the same public key. Existing expired/wrong-pin, PSS-policy, parser,
key-use and close tests pass. Child rejection remains a native policy test, not
a live child handshake. Weak RSA/disallowed OID checks are unchanged source
checks; this increment does not add independent negatives for those.

All166 unit tests pass with no failures/errors/skips; lint remains0 errors and
37 warnings; app and instrumentation APKs assemble. The app APK contains none
of the four public test-key/certificate assets or instrumentation classes.
Independent scoped review found no production or Nord-runner blocker.
Installed version11 app SHA256:
`22d18042b732ef1e129b13a140835b2467e900911fa8887bea0fba9dad8e630a`.
The stopped Nord was updated with the same signing identity and both enrollment
stores unchanged; no run started automatically. All15 native loopback TLS tests
also pass on the physical AC2001/API31. A separate reviewed TLS-only diagnostic
using the existing app identity now reports COMPLETE against the real projector,
where0.10 failed. It sent no pairing/control frames and persisted no enrollment.
Both temporary instrumentation installations were removed, records remain
unchanged, and the probe is stopped. Owner code pairing and the remaining
combined hardware acceptance checks are still pending; TLS success alone is not
server enrollment or N3 PASS. The0.10 rollback APK is preserved locally.

The one-use emulator runner was removed after preserving the explicit15-test
result; its review-noted future-reuse shortcomings do not apply to the separately
reviewed physical-device runners. No additional production scope was added.

## Implemented flow

The native screen retains Bluetooth touchpad/tap/scroll and adds the closed TV
D-pad/OK, Home/Back, volume, play/pause and Netflix commands. TV keys use native
Remote v2 TLS with an opaque AndroidKeyStore client identity and exact peer pin.
The TV record is separate from the Bluetooth enrollment. No Mac, discovery scan,
router change, CA installation or runtime ADB input is involved.

First TV enrollment is a separate run with no input. Only after code-bound pairing,
a pinned control handshake and verified main-thread persistence does the app
report pairing complete and stop. Subsequent explicit Connect verifies TLS before
connecting HID; all input requires both channels. UNUSABLE TV storage blocks Pair
and Connect. No silent HID-only fallback follows a failed combined connection.

Stop, qualifying pause, expiry and either channel failure fence input and close
both channels. The process-wide native-owner barrier includes the TV operation
and writer workers, including late native key generation. The existing initial
Bluetooth-system-UI pause exception survives only before any TV operation.

## Evidence so far

- [Native foundation](n3-platform-report.md):126 unit tests,14 emulator tests,
 12 native Nord TLS tests, independent source review and preserved enrollment.
- Final combined build:164 unit tests pass,0 failures/errors/skips; both APKs
  assemble. Lint0 errors/37 warnings (diagnostic UI/native view/target warnings
  and the two explicitly reviewed trust-manager warnings; no suppressions).
- Main lifecycle/UI source review found a deferred-Forget recreation defect;
  fixed by retaining explicit retry when another Activity owns teardown.
  Actual-method harness is RED without the guard and PASS with it. It also checks
  delayed enrollment/connection callbacks after Stop/pause/expiry/new owner, and
  native cleanup continues after an injected TV-stop exception.
- Transport review found failed-write completion could wake readiness before the
  failure latch. Regression reproduced false connected/enrolled callbacks. The
  correction fences failure before completion; four write/flush
  stress cases totaling256 attempts pass and scoped review closes the finding.
- Stop cleanup now attempts HID unregistration even when native state lookup,
  disconnect or enumeration throws. The actual-method regression is RED before
  correction and PASS afterward, including TV-stop and neutral-report failures.
  Native teardown remains unconfirmed on failure; no owner slot is force-released.
- API31 emulator:17/17 native tests pass (12TLS, five UI), including portrait,
  landscape/enlarged text, D-pad geometry, code storage/autofill exclusions, IME
  inset geometry and setup gesture cancellation. Instrumentation APK removed.
- Final independent whole-change review reports no remaining actionable findings.
  This is source/test review, not proof of live-TV interoperability.
- APK DEX has no test server/identity fixture. The only five permissions are
  INTERNET, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, FOREGROUND_SERVICE and
  FOREGROUND_SERVICE_CONNECTED_DEVICE. Release variant stays disabled.

## First owner pairing attempt — failed, cause not established

On version0.9 the owner entered a Wi-Fi address, tapped Save TV address, Start,
then Pair TV. The app reported "TV connection or pairing failed. Both channels
stopped; check the address or pair again. Native teardown completed. Tap start
for a new run." The entered target and whether a code appeared are unconfirmed.
This is owner-reported cleanup status, not an independently observed teardown.

Source inspection confirms this message merges identity, network, TLS and
pairing-protocol failures. The outgoing pairing sequence matches the working
upstream reference; no specific incompatibility is established. The control
feature mask is reached only after code submission and secret acknowledgment.
Do not attribute this failure to that mask without stage evidence.

Two Nord debugging endpoints were unavailable; a refreshed owner-supplied endpoint
then connected successfully. Permitted diagnostics verified the authorized Nord
AC2001/API31, installed version9, no running probe service, the Bluetooth
enrollment file present and no TV enrollment file. With the owner's confirmed
projector address, both TV Remote TCP ports are reachable from the Mac and Nord.
Public-certificate inspection on the pairing port shows RSA2048/SHA256 with
validity covering the test date. This does not establish the native app's TLS
handshake or server identity. No pairing/control messages or trust-policy
changes were used in these checks.

The owner retried version0.9 with the confirmed projector address and reported
the same error. Version0.10 is installed with closed failure-stage labels only;
the first stage is captured under the existing Stop lock and delivered through
the existing guarded callback. It does not change trust, pairing, input, timers
or cleanup. The actual failed phase and root cause remain unknown until retry.

Version0.10 verification:166 unit tests pass with no failures/errors/skips;
debug assembly passes; lint has0 errors/37 warnings. Tests inject failures at
identity, connection, TLS, session validation, exchange and code phases, retain
the original failure through cleanup, suppress failure reporting on explicit
Stop, and exercise the actual Activity callback text. No native TLS changes or
new native hardware acceptance are claimed. APK SHA256:
`f0c6d6d3c78b69ef1833730dc68e0b23865b78ff5d14c7dd072e1d038f2b3f2d`.

Independent scoped review found no actionable issues in diagnostics, connected
callers/tests or controlled installer. The first install attempt stopped before
mutation because ADB exposed duplicate transports. A reviewed selector requires
all authorized Nord aliases to have the same nonempty in-memory device serial;
it logs/persists none. The retry verified one physical AC2001/API31, stopped
version9, matching signing identity and reviewed source/APK hashes. Version10
installed with both enrollment stores byte-identical. No automatic run started.
Activity launch did not confirm foreground, so the owner was asked to unlock and
open it manually. The0.9 rollback APK remains preserved.

The owner retried0.10 and reported failure. A filtered UI hierarchy read confirmed
PAIRING_TLS and native teardown completed; the temporary hierarchy was deleted.
A separately reviewed, explicitly opted-in native instrumentation diagnostic then
loaded the existing app identity and opened only pairing-port TLS. It reported
HANDSHAKE -> CERTIFICATE -> CERTIFICATE with fixed reason INVALID PEER SIGNATURE;
the client certificate is PSS-signed. This identifies the peer self-signature
verification in TvTls, not a proven client-signing incompatibility. The native
check was executed once, without pairing/control frames; both enrollment stores
were byte-identical afterward, the test package was removed and the probe stayed
stopped. Its successful test execution is not a successful TLS handshake.

Independent Mac OpenSSL `verify -check_ss_sig` also returned certificate signature
failure; the temporary public certificate was deleted. Neither diagnostic
authenticates the server. The default test suite skips this live diagnostic unless
given its explicit mode and a canonical private target address. The reviewed host
runner verifies stopped state, source/artifact/signature identity, unchanged
enrollments and test-package cleanup, and bounds execution with process teardown
on timeout. Three review findings in package-presence/error cleanup were corrected
before any live diagnostic execution. The immediate blocker is established;
the trust-anchor compatibility decision was then awaiting owner approval, subsequently
granted and verified in the current fix section above.
The TLS-only diagnostic test APK SHA256 was
`9b25190b4b869bd892f77f31ce6fc414b8a3e1b8fc1f549ecaaf1977a1cba1cc`;
the main app remained the recorded0.10 artifact. Independent design review found
no added impersonation counterexample in the accepted threat model if every
retained check holds, but identified deliberate loss of certificate
self-consistency validation. Native validator rejection is not bypassable under
the proposed amendment; positive/negative platform fixtures remain required.

## Owner acceptance — pairing failed, remaining checks NOT RUN for version0.9

- TV-code pairing: first attempt failed as above; combined Start/Connect NOT RUN.
- Saved reconnect and full app/process restart without re-pairing.
- Home/D-pad/OK, Back, volume-/+, play/pause on owner-selected content.
- Netflix launch, both channels still usable after20 seconds.
- Pointer movement, title/profile click and both scroll axes.
- Stop blocks both, physical remote still works; foreground loss/return stays stopped.
- Five-minute expiry; deliberate Wi-Fi loss and Bluetooth loss each stop both.
- Portrait/landscape, enlarged text and pairing keyboard remain usable.

Hardware address change remains NOT RUN unless observed. No write/send callback
proves a projector action. The five-minute foreground diagnostic limit remains;
daily-use lifecycle/packaging and cross-device access are deferred to later work.

## Controlled installation

- Same signing identity as preserved0.7/0.8; reviewed app SHA256:
  `0419da1e79e017c82c38ab549404a2f49d7475f24db2c047836bbd64071a2f82`.
- Verified one authorized Nord AC2001/API31 and stopped probe before update.
- Installed version9 in place, preserving both enrollment stores byte-for-byte;
  no clearing, unpairing, firmware change or projector input.
- Probe foreground confirmed after launch; no automatic service/control run.
- APK has backup and cleartext traffic disabled, non-exported connectedDevice
  service, launcher-only exported Activity, and backup/transfer rules. Test server,
  fixture identities and fixture certificates are absent from app DEX.

## Remaining interoperability limits

The app negotiates mask579 (ping/key/volume/app-link), omitting H0's POWER status
bit. Both parse RemoteStart as readiness/power status. Available client/schema
sources do not prove whether that status bit controls delivery of RemoteStart;
only the owner TV test can establish this. A missing start times out and stops,
never bypasses readiness. No power input capability is exposed.
Native TLS1.3 negotiation and live child-certificate handshakes remain unproven;
TLS1.2 mutual authentication and native child-policy rejection are proven.

Final emulator output SHA256: `d2da8a45cf4aa424a7f34a6d5f3cf4aeeda07a1b652a979c0eacd58b8adfcf23`.
