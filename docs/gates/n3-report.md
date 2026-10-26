# N3 unified Nord remote — pairing, combined smoke and saved restart passed on0.12

Contract: [approved N3 design](../superpowers/specs/2026-10-25-nord-tv-controls-design.md).

## Diagnostic0.13 cleanup correction — built; hardware retest pending

The owner requested fixing the captured shutdown blocker. The bounded
[correction plan](../superpowers/plans/2026-10-25-nord-bluetooth-cleanup.md)
retains the teardown contract and NativeRunGate's permanent failure latch.
Profile loss before Stop remains fatal. During Stop it is pending cleanup:
actual selected-peer disconnect and unregister callbacks, no observed foreign
link, successful proxy close and TV/service completion are required. Disabled
or detached proxy query defaults are never evidence. New connection events or
connect attempts invalidate prior disconnect evidence; independent native
failures remain fatal. No automatic input/reconnect or enrollment change.

The new test executes the Activity's actual callbacks against controlled native
collaborators. RED fails on the captured Stop/profile-loss/disconnect/unregister
order. GREEN covers22 scenarios, including missing evidence, both callback
orders, query defaults, foreign links, invalidation, existing failure latches,
proxy-close failure, TV/service completion and stale callbacks. All167 Nord unit
tests pass with zero failures/errors/skips; lint has0errors/37warnings and debug
assembly passes. Independent review identified stale disconnect evidence after a newer pending-link
query. A second RED regression reproduced it; native state/enumeration observations
now invalidate the evidence before detached cleanup. All22 scenarios pass.
This does not replace a real Nord recovery test.

Built version13/0.13-diagnostic APK SHA256:
`b74ae6d58413669732495cdff3b774cd4f321696ccd4fc62fb1084a9730e3a32`.
Independent scoped re-review and complete-change Codex review found no remaining
actionable findings after the stale-evidence correction. Installation and owner
hardware retest are pending. The0.12 observations below
remain tied to their original artifact; no overall N3 PASS is claimed.

The owner also confirmed the full app-restart check: Stop/teardown, Android
Force stop, reopen with input disabled until explicit Start/Connect, then Home,
pointer movement and a scroll step without another pairing code, followed by
Stop. Both pairings were retained. This is a successful owner-observed restart
trial on0.12, not proof of crash cleanup, projector reboot or long-run reliability.

The owner subsequently confirmed "works as expected" for the requested0.12
combined-control smoke: explicit Start/Connect, Home/D-pad/OK, Netflix launch,
pointer movement, title opening, scrolling, Back, volume, Stop preventing app
control, and the physical remote remaining usable. This is owner-reported
behavior on the same recorded artifact, not an automated input test.
The owner also confirmed foreground loss/return keeps all app input disabled,
and explicit five-minute expiry stops all app controls; the physical remote
remains usable in both checks on the same0.12 artifact.
Projector Wi-Fi loss also passes the owner check on0.12: with the Nord app
visible, both channels stopped within the requested45-second window and remained
stopped for the20-second observation after Wi-Fi restoration. The physical
remote remained usable. Exact stop latency and status text were not recorded.
The owner then verified Home → Netflix followed by at least20 seconds with
both channels usable: pointer opening of title details and the app Back button
worked. Play/Pause visibly paused, resumed and paused owner-chosen playback,
followed by Stop. The owner also verified pointer/tap selection of their usual
Netflix profile and Scroll down/up/right/left with no unintended title opening
or pointer movement, then Stop. Remote/Setup layout also passes owner checks
in portrait/landscape at normal and largest configured font size: status and
all controls, including Stop/Close, remain readable and reachable by scrolling.
Live Nord keyboard layout also passes at the usual font size in both
orientations, with Setup fields/status and Stop/Close reachable by scrolling.
Remaining Bluetooth-loss acceptance is open.
No overall N3 PASS is claimed.

The preceding owner retry on0.11 failed at CONTROL_EXCHANGE, after the code/secret
acknowledgment and pinned control TLS. The owner reports10–15 seconds between
Confirm TV code and failure. This is consistent with a negotiation deadline, but
does not identify the last received frame or prove missing RemoteStart.

Version0.12 is installed as a bounded compatibility experiment: negotiate optional
POWER status32 alongside the existing features, matching upstream/H0 mask611
instead of579. No power input command is added. Server-feature intersection,
required KEY/APP_LINK checks, ordered RemoteStart readiness, trust, deadlines,
enrollment and Stop are unchanged. RED negotiation test expected611 and received
579; after the one-bit correction all166 unit tests pass, lint/build pass.
Installed0.12 APK SHA256:
`6fbfdc65357d1a0336f5c18099234ce1d33c42954cc5a3ab7b6da4cbd131eb72`.
Independent review found no actionable production/installer findings. The stopped
Nord was updated with matching signing identity and both enrollment stores
unchanged; foreground was confirmed and no run started automatically. The0.11
rollback artifact is preserved. The owner then confirmed: "TV controls paired;
app stopped." Read-only inspection confirms version12, no running probe service,
and both Bluetooth/TV enrollment files present and nonempty; their private
contents were not printed. This verifies first TV-code pairing and the pairing
run stopping after enrollment on0.12. No raw wire trace was collected; success
after the one-bit change supports the compatibility correction without claiming
a captured server-side explanation. Source:9f29047 (TLS fix78a2f1c).

The prior blocker was an explicit self-signature rejection of the certificate
presented at the owner-confirmed projector address. See the
[approved compatibility decision](../superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md).
The approved narrow fix is installed; owner TV pairing now passes on0.12.
Source branch `nord-tv-controls`, implementation `8763f5a` (transport `6dfd599`).
N3 owner acceptance is **PENDING**. Reviewed version0.9 was
installed with both enrollment stores unchanged and no automatic remote run.
The initial failures are retained below; first enrollment now passes. Combined
control smoke and saved restart now pass; remaining lifecycle hardware
acceptance remain pending.

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

## Owner acceptance — pairing and combined smoke PASS on0.12; remaining checks pending

- TV-code pairing and stopped-after-enrollment: PASS on0.12, owner confirmation
  and persisted-record/stopped-state inspection above.
- Explicit combined Start/Connect: owner smoke PASS.
- Saved reconnect after full app Force stop/reopen without re-pairing: owner PASS.
  Input stayed disabled until explicit Start/Connect; Home, pointer and scroll
  worked, then Stop. No projector reboot or held-input crash test was performed.
- Home/D-pad/OK, Back and volume: owner smoke PASS. Play/Pause: owner PASS
  for pause → resume → pause on owner-chosen playback, followed by Stop.
- Netflix launch and at least20-second post-launch stability: owner PASS;
  both channels remained usable, verified with pointer title selection and Back.
- Pointer movement and title click: owner smoke PASS. Profile selection by
  Nord pointer/tap: owner PASS on0.12. Scroll down/up/right/left: owner PASS
  on0.12, with no unintended title opening or pointer movement, then Stop.
- Stop blocks app control and physical remote works: owner smoke PASS.
- Foreground loss/return with both channels: owner PASS; all app controls remain
  disabled after return and the physical remote still works.
- Five-minute expiry: owner PASS after the explicit expired status; all app
  controls stop and the physical remote still works.
- Projector Wi-Fi loss/restoration with Nord foreground: owner PASS on0.12;
  both channels stop, remain stopped after restoration, and the physical remote
  works. This tests projector-side link loss, not Nord adapter shutdown.
- Bluetooth-loss trial: PARTIAL on0.12. Owner reported "TV: paired, disconnected",
  "Mouse: disconnected", "Bluetooth turned off. No input enabled" and the
  native-teardown-unconfirmed/force-stop recovery instruction. This establishes
  the reported adapter-loss stop state, not successful native cleanup or a
  directly verified OFF state. The receiver uses that message for any non-ON
  adapter state. The debugging disable/restore commands returned errors;
  subsequent independent reads confirmed Bluetooth ON and the probe service absent.
  The initial bounded trace read returned no events. The later captured retry
  below establishes a profile-loss failure latch, without proving all cleanup. Owner follow-up confirms actual rejection of TV/pad/scroll
  input and continued physical-remote operation. Android Force stop/reopen preserved both pairings,
  kept input disabled until Start/Connect, and restored Home/pointer/scroll
  without another code, followed by Stop. Input blocking and documented recovery
  pass by owner observation; native cleanup and directly observed OFF timing
  remain unconfirmed. No full controlled Bluetooth-loss PASS.
- Remote/Setup portrait/landscape at normal and largest configured font size:
  owner PASS on0.12; all controls/status readable and reachable, including
  Stop/Close. Numeric font scale was not measured.
- Live Nord keyboard layout: owner PASS on0.12 at the usual font size in both
  orientations. While stopped, focusing the saved TV-address field opened the
  keyboard; address/code fields, Confirm, status, Stop and Close remained
  readable/reachable by scrolling. Keyboard dismissed; address and pairings
  unchanged. This was a layout-only check with pairing controls disabled, not
  a new pairing or enlarged-font live code-entry test. Existing native tests
  separately simulate IME insets in both orientations at fontScale1.5.

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

Through0.11 the app negotiated mask579, omitting H0's POWER status bit.
Installed0.12 adds that optional receive capability, producing611 when offered.
Available client/schema sources do not prove whether that bit controls delivery
of RemoteStart; owner verification remains required. A missing start still times
out and stops, never bypassing readiness. No power input capability is exposed.
Native TLS1.3 negotiation and live child-certificate handshakes remain unproven;
TLS1.2 mutual authentication and native child-policy rejection are proven.

Final emulator output SHA256: `d2da8a45cf4aa424a7f34a6d5f3cf4aeeda07a1b652a979c0eacd58b8adfcf23`.

## Bluetooth-loss recovery observation — 2026-10-25

The current diagnostic explicitly requires unregister acknowledgment, no remaining
link, profile closure and service destruction before admitting another run. A
failed native operation latches the process unavailable even if later completion
callbacks arrive; unresolved teardown displays the recovery instruction after
five seconds. The observed message is consistent with this documented safeguard
(README lifecycle section and NativeRunGate), not evidence that it is safe to
clear the latch. The initial trial did not identify the native failure; the
subsequent capture below establishes one definite failure-latch path. Owner follow-up confirms disabled TV/pad/scroll controls, a usable physical
remote and successful documented Force stop/reopen recovery with both pairings
retained. Input stayed disabled until explicit Start/Connect; Home, pointer and
one scroll step worked without a pairing code, then the owner pressed Stop.
This does not establish automatic native cleanup. No product code or safety
policy was changed.

## Captured Bluetooth cleanup blocker — 2026-10-25

Same installed0.12 diagnostic; no new APK or pairing. Read-only preflight verified
Nord AC2001/API31, app version12, Bluetooth ON, foreground Activity and active
probe service. A live fixed-tag capture started before owner Start/Connect. The
owner verified Home and Move left; the trace contains registration, selected
connection and accepted movement followed by these contiguous events:

| Sequence | Elapsed ms | Event |
| --- | --- | --- |
| 24 | 87000 | STOP |
| 25 | 87049 | SELECTED_DISCONNECTING |
| 26 | 87320 | PROFILE_LOST |
| 27 | 87340 | SELECTED_DISCONNECTED |
| 28 | 87340 | UNREGISTERED |
| 29 | 92006 | TEARDOWN_DEADLINE |

All29 events from RUN_SAVED are contiguous, with no truncation. Sanitized event
record SHA256: `90462ef7dc876ba669be2f2f8802f7ec37ca43974123f719320f2cfbda807cf2`.
No PAUSE or RUN_DEADLINE precedes this Stop. The debug disable and restore
commands both returned exit137; an independently polled OFF state was not
confirmed. Subsequent reads confirmed ON and no running probe service. Do not
attribute exit137 to a particular cause or promote the controlled OFF test.

`Run.service.onServiceDisconnected` emits PROFILE_LOST then calls
`ownership.fail()` even when Stop is already in progress. NativeRunGate never
clears that flag; later disconnect/unregister callbacks cannot make the slot
available. This is a directly evidenced, sufficient blocker to restart, not
proof that it was the only failing cleanup operation. The trace does not expose
completion of every TV/service/profile resource.

Four focused existing cleanup tests pass (NativeRunGateTest and NativeStopTest).
They preserve the conservative failure policy; no runtime change was made.
A future recovery change must retain actual unregister acknowledgment, confirmed
link retirement, profile closure and TV/service completion. Merely ignoring
PROFILE_LOST, relying on empty results from a disconnected proxy, or clearing
failed ownership on timeout is not justified by this trace. Force stop/reopen
remains the verified recovery path from the earlier owner trial.
