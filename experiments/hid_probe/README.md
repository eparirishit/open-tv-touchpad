# Disposable Nord remote — N3 diagnostic

## Prototype UI in0.15

Version0.15 corrects the0.14 Activity launch crash by deferring system-bar setup
until the view is attached. The owner-provided design separates TV buttons on **Remote** from mouse input on
**Touchpad**. Touchpad offers a surface or **Buttons instead**, with four scroll
buttons available in both modes. The header keeps Setup, Stop and Close reachable;
Setup groups **Projector**, **TV controls** and **Display**. Display changes light/
dark colors in place and saves only the appearance preference. Real connection
status and the full diagnostic message remain visible in the scrollable content.

Start/Connect, both pairings, masked TV-code entry and all control admission stay
unchanged. Page and theme switches cancel pending gestures without starting or
restarting a session. The five-minute limit and unresolved0.13 Bluetooth cleanup
barrier remain. No re-pairing is required for this presentation update. See the
[prototype UI report](../../docs/gates/nord-prototype-ui-report.md) for build,
installation and owner-test status.

## Earlier diagnostic increments

The owner accepted remembering the verified projector for this isolated diagnostic
on 2026-10-24, including the OS-bond continuity limitation. This is not a product
companion, network bridge, or H1A PASS. The physical TV remote remains independent.

The owner approved the N1 direct-Nord touchpad on 2026-10-25. Version0.5 adds a
finger-operated surface to this same isolated probe; it is not the complete
unified TV remote. Version0.6 adds four scroll buttons and a one-time fresh
pairing migration. TV buttons and daily-use lifecycle remain separate increments in the [direct-remote design](../../docs/superpowers/specs/2026-10-25-nord-direct-remote-design.md).

Version0.7 adds a Google TV-inspired charcoal Remote screen with the existing
touchpad, four scroll buttons and native mouse alternatives. Setup/Remote switches
pages inside the Activity and cancels pending pad input. Stop and Close remain
fixed; smaller/landscape screens use scrolling. Start then Connect remains explicit.
Existing0.6 enrollment and Bluetooth bond are retained: do not forget or re-pair
for this UI update. The five-minute foreground-only limit is unchanged. See the
[UI design](../../docs/superpowers/specs/2026-10-25-nord-remote-ui-design.md) and
[verification report](../../docs/gates/nord-ui-report.md).

The owner approved [N3 unified TV controls](../../docs/superpowers/specs/2026-10-25-nord-tv-controls-design.md).
The reviewed0.8 platform diagnostic retains the0.7 UI and adds native Keystore/TLS,
bounded wire/pairing and TV-enrollment helpers. It has no runtime TV caller yet.
INTERNET is present solely for the approved N3a androidTest loopback fixture at
this stage. The fixture uses test-only native identities and never reads retained
enrollment or connects to the projector. It passed independent review,126 unit tests,14 emulator tests and12 native
TLS tests on the Nord. The installed update preserves Bluetooth enrollment. [Platform evidence](../../docs/gates/n3-platform-report.md).

Uses native `BluetoothHidDevice` on API31+, the existing Gradle/JUnit toolchain,
and pinned protobuf Java lite for N3 wire messages; no custom crypto. Separate
application ID; opt-in Gradle module; debug-only variant. No scan, location,
accessibility or storage permission. Nothing starts the app in response to a
Bluetooth broadcast. Version0.7 remains the preserved owner-tested behavior baseline.
Version0.8 was installed for the platform proof with no live TV-controls caller;
it is superseded by the installed0.9 integration described below.


## Installed0.9 unified controls setup

Version0.9 combines the existing Bluetooth mouse with TV buttons over pinned TLS.
Automated checks and independent review pass; it is installed on the Nord. [N3 evidence](../../docs/gates/n3-report.md)
separates automated checks from owner acceptance. Keep the existing Bluetooth
bond and saved enrollment; this update requires no Bluetooth re-pairing.

To perform the pending owner acceptance checks:

1. Keep the Nord and projector reachable on the same local Wi-Fi, with Bluetooth on.
   Find the projector's Wi-Fi IPv4 address in its network settings.
2. While stopped, open Setup, enter that private IPv4 address without a port, and
   tap Save TV address. This address locates the TV; its certificate authenticates it.
3. With Bluetooth already enrolled, tap Start, then Pair TV in Setup. Do not tap
   Connect during this first TV-pairing run. Enter the six hexadecimal characters
   shown on the intended projector into the masked field and tap Confirm TV code.
   Keep the code out of chat/logs. Leaving the app stops the attempt.
4. Successful pairing saves the separate TV identity and ends the run. Tap Start,
   wait for HID registration, then Connect. TV TLS verifies first; mouse and TV
   controls enable only after both channels are ready.
5. Keep the app open. Stop/Close, expiry, foreground loss or either connection
   failure stops both. Recovery is explicit Start/Connect after native teardown.

If DHCP changes the address, Stop, edit it in Setup and save. This preserves the
certificate identity. A wrong/expired/changed certificate fails closed; do not
expect editing the address to replace trust. Explicit Forget TV controls removes
only this app's TV record/key, then permits a new intended-TV pairing. A failed
Forget remains blocked until a successful retry. Forget Bluetooth is separate.
Apartment Wi-Fi client isolation can block TV keys even when Bluetooth works;
there is no insecure transport fallback. The diagnostic remains foreground-only
and limited to five minutes, including the pairing time.

`ProbeActivity` owns public Bluetooth calls and the manual UI. Its `singleInstance`
launch mode prevents multiple screens from holding conflicting enrollment views,
including launches from another task or multi-window. Each Start creates immutable
callback ownership. `NativeRunGate` keeps a process-wide slot through Activity
recreation until native teardown completes. `ProbeSession` owns input admission.
`EnrollmentStore` is process-owned so a storage-failure latch survives Activity
recreation; it validates one versioned selector; `AtomicEnrollmentFile` writes
it with Android `AtomicFile` in `getNoBackupFilesDir()`. The native
`PairingGuardService` has no input API: it supplies a non-exported, non-restarting
foreground notification with Stop and a fixed five-minute elapsed deadline.

## Enrollment and trust

- First enrollment requires an observed fresh Numeric Comparison request, a new
  bond, and explicit confirmation in the resumed app that the numbers matched on
  BOTH intended screens. Existing unrecorded bonds cannot be adopted. No pairing
  digits are read, entered, stored, or logged. The app never approves system
  prompts or uses hidden bond-removal APIs.
- Durable enrollment contains only format version `2` and one validated peer
  address. No authorization, key, pairing code, queued input or gesture is saved.
  Missing, corrupt, unsupported or oversized records select no peer. App backup
  and device transfer remain excluded by the manifest/XML rules and native
  no-backup directory; this does not promise protection from privileged extraction.
- Saving must complete and read back correctly before the initial connect call.
  Forget writes and verifies an empty atomic record before claiming removal.
  Read/write/verification failure disables enrollment use in this process. Failed
  Forget explicitly warns that disk may retain the old selector: successfully
  retry Forget or clear app storage in Android settings before re-enrolling.
  An unwritable disk cannot be promised erased, including after a process restart.
- The address selects the **current OS-managed bond**, not an app-pinned original
  key. Android's public API31 HID callbacks do not attest encryption or original
  key continuity. Both OSes and privileged Bluetooth components are trusted.
  Replacement of a bond while the Activity/process is absent may go undetected;
  this accepted limitation is not solved by signing or encrypting a local record.

## Run boundaries

- Start opens one run and one fixed five-minute service window. Registration
  requires the actual native callback, with a five-second setup timeout.
  Start alone never connects. Connect saved projector is a distinct owner action,
  rechecking permissions, adapter, registration and current bond. First comparison
  saves and starts the same single attempt. Connect never renews the deadline.
- Only one connection attempt is allowed per run, with a ten-second timeout.
  Saved selection plus an unsolicited connection cannot authorize reports.
  Unknown/unsolicited connections receive no neutral or input reports and are
  disconnected. No discovery fallback, peer substitution, retry or input replay.
- Input requires the current owner, registered selected attempt, bonded peer,
  accepted CONNECTED callback, live service, resumed Activity, and successful
  native submission of a neutral report. Submission is not delivery acknowledgment,
  receiver-cleanup proof, or cryptographic attestation.
- System pairing screens may cover the Activity during initial setup. Comparison
  and input are disabled while covered. After confirmation/explicit Connect,
  any Activity pause ends control. Stop, timeout, link/registration/service loss,
  adapter loss and Activity destruction fence input and request cleanup.
- Stop ends the service but retains enrollment. A new Start is admitted only after
  native unregister acknowledgment, no connected/pending selected link, profile
  closure and service destruction. The old owner then cannot send reports,
  disconnect, unregister, or stop a newer run. Failed or unconfirmed teardown
  leaves new runs and input disabled with a recovery message. The idle Start button
  also explicitly rechecks teardown, so a recreated screen can retry without
  polling or automatic registration. After five seconds, an unresolved
  teardown directs the owner to force stop the probe in Android settings and
  reopen; no automatic reconnect or hidden Bluetooth operations are attempted.
- Version0.13 treats profile-service loss during an already-stopping run as
  pending cleanup. After profile loss or adapter shutdown, disconnected-proxy
  query defaults cannot release ownership: require this run's actual selected
  disconnect and unregister callbacks, no observed foreign link, successful
  proxy closure, and existing TV/service completion. A new connection event or
  connect attempt or pending-link query invalidates disconnect evidence. Profile loss before Stop,
  rejected native operations and independent cleanup failures remain blocking.
  Finishing confirmed teardown only permits an explicit new Start/Connect;
  it never resumes input automatically.
- The Activity observes selected-peer pairing/re-pairing and any departure from
  BONDED even after Stop, revokes the record and stops input. Missing bond at Start
  or Connect also invalidates enrollment. Forget clears the app record/session;
  removing the OS bond is a separate manual action in system settings.
- Fixed small arrows, a one-finger relative touchpad and immediate left-button
  press/release exist. No held drag, keyboard, background input or remote endpoint.
  Crash/radio loss may prevent release; actual cleanup remains a hardware check.

## N1 touchpad behavior

- Slide one finger to move the projector pointer. Movement starts beyond Android's
  touch-slop threshold; gain is1.0 HID count per density-independent pixel. Lift
  and reposition freely. Pending motion is discarded when the finger lifts.
- A stationary tap lasting at most250ms sends an immediate press/release. A swipe
  returning to its origin cannot become a click. Long press has no click/drag action.
- A second finger cancels the gesture until all fingers lift. Scrolling uses the
  separate buttons; held drag remains unavailable. Keep the physical TV remote
  for navigation.
- Motion is coalesced to at most30Hz, with one accumulator capped at254 counts
  per axis and each submitted delta bounded to[-127,127]. Excess motion is
  discarded, not replayed after delay. Fractional motion is kept only in a gesture.
- Stop, disabled input, focus loss, app pause and detaching the pad cancel gesture
  state and scheduled dispatch. A fresh down is required after re-enabling.
  Bluetooth reports still pass the existing native ownership/admission checks.
- Native submission exceptions share one failure-to-Stop boundary across arrows,
  Click, touch events and scheduled motion; click still attempts release in finally.
- Stop/Disconnect and Close remain visible below the pad, outside the scrollable
  setup area. Labeled arrows and Click provide accessible alternatives.

## N2 scrolling and one-time migration

Version0.6 uses a single five-byte report: buttons, X, Y, wheel, pan; report ID1
is passed separately to Android. Up/down send wheel +1/-1; left/right send pan
-1/+1. Each native button click sends one step, with at most one submission per
100ms across all directions in a run. Excess clicks are dropped. No repeat,
queue, two-finger scrolling or drag fallback is implemented. Button-down cancels
pending pad motion/tap immediately; admitted clicks cancel again, including
keyboard/accessibility and throttled clicks. Input still needs the existing
foreground, selected-peer and neutral-first checks. Submission failure stops.

The package is unchanged, but the HID descriptor and enrollment version change.
Version1 records remain untouched on load and cannot select/connect a peer.
A persistent notice explains the required migration. Only a fresh observed
Numeric Comparison ceremony can save version2. This prevents automatic reuse
of a host's potentially cached three-byte descriptor; the app cannot prove that
the host's bond/cache was removed.

After the reviewed0.6 build is ready:

1. On0.5 tap Stop and wait for native teardown. Manually forget **only the
   Nord–XGIMI test bond on BOTH devices**. Report readiness before installation.
   Do not uninstall or clear app storage. No hidden unpairing is used.
2. Install the reviewed0.6 APK in place with the same signing identity. Verify
   version6, no saved peer usable for control, and disabled input before pairing.
   An old version1 record, if still present, shows the migration notice.
3. Check the actual Nord layout in portrait and landscape before the scroll trial:
   all four labeled scroll buttons, pad, Stop and Close are reachable, and the
   setup/status area can still scroll. Rotation ends any active run; start a fresh
   run afterward. Do not use ADB input as acceptance evidence.
4. Start → HID registered → Make visible. Pair using the physical TV remote;
   compare and accept matching numbers on BOTH screens, then Save and connect
   in the probe. Keep codes out of chat/logs. Never adopt an unrecorded old bond.

The verified0.5 APK is preserved locally at ignored
`.local/artifacts/nord-hid-probe-0.5-fdb4905.apk`; its hash is in the N1 report.
Rollback requires Stop, manual removal of only the test bond on both devices,
a supported same-signature downgrade to that artifact, and fresh compared
pairing with the old descriptor. Version0.5 rejects version2 enrollment. Never
uninstall/clear app data automatically to force a downgrade; retain artifacts
and report any unsupported downgrade for an owner decision.

Successful installation or unit tests do not establish actual-device acceptance.

## Diagnosing TV pairing

Version0.12 requests optional power-status events during Remote v2 negotiation,
matching the working reference. Ordered readiness is still required and there is
no power input command. Owner pairing now completes and stops on0.12; combined
controls and lifecycle hardware acceptance remain pending.

Version0.11 applies the owner-approved leaf trust-anchor exception: an invalid
peer self-signature alone does not block the otherwise checked candidate/pin.
Code-bound authentication, native TLS/private-key proof, exact DER pinning,
validity/algorithm policy and native control validation remain required. Native
client self-signature checks are unchanged. Public test-key/certificate assets
live only in androidTest and never ship in the app APK. See the
[approved decision](../../docs/superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md)
and [hardware evidence](../../docs/gates/n3-report.md).

Use the projector's own Wi-Fi IPv4 address, not the phone's debugging endpoint
or the network gateway. Save TV address validates the entry; it does not test
connectivity. Pair TV is a separate no-input run. Keep its six-character code
in the app's masked field, never in issue reports or logs.

When pairing fails, report the app's status and whether the projector displayed
a code (not the code itself). Native teardown completion reports cleanup, not
successful pairing. Keep the existing Bluetooth bond; do not clear storage or
re-pair Bluetooth to investigate a TV connection failure.

Version0.10 adds a fixed failure-stage label: identity, pairing/control connection,
TLS, session validation, pairing exchange, code, or control exchange. It records
the first failure stage before cleanup; this locates the phase but does not prove
its root cause. No raw exceptions, addresses or protocol values enter this label.
The connection, trust checks, deadlines and combined Stop behavior are unchanged.

`TvHandshakeDiagnosticTest` is a separate explicit live diagnostic, skipped by
default. It requires instrumentation arguments `liveTvDiagnostic=TLS_ONLY` and a
canonical private `projectorAddress`. Run only against the owner's confirmed TV,
with the probe stopped and its existing native identity present. It opens only
pairing-port TLS with that identity, sends no pairing/control frames, creates no
identity and writes no enrollment. Results contain only fixed stage, signature
family and cause/reason categories. A completed diagnostic can report a failed
handshake; it is not hardware acceptance. Use a bounded host runner that verifies
the reviewed test artifact/signature, preserves enrollment, terminates a timed-out
test process and removes the temporary test package. Keep target addresses out of
saved command output and reports.

## Bounded observational trace

Version 0.4 (versionCode 4) adds observations only; it does not fix reconnect,
change input admission or native operation order, or establish a new gate result.
Each existing run owns a pure `ProbeTrace`. Its Activity helper writes only to
the fixed Android log tag `OpenTvHidTrace`, with this message format:

```text
v1 seq=1 elapsed_ms=0 event=RUN_SAVED
```

The message contains only a fixed format version, a per-run sequence, elapsed
milliseconds since run creation, and a closed enum name. No free-form payload,
peer identifier/name/address, pairing value, coordinate, report bytes, exception
text or absolute time is accepted by the formatter. Sequence and elapsed time
restart per run; there is no persistent run identifier. Events describe observed
registration requests/results and callbacks, explicit native connect requests and
returns, selected/other peer connection states, admission rejection, neutral/move
submission returns, selected-peer protocol mode, pause, Stop, teardown completion,
service/profile loss and deadlines. Selected means the target or saved selector,
not a claim of authentication. Submission acceptance is not delivery evidence.
The service's existing fixed expiry reason identifies its five-minute deadline;
other service stop reasons share `SERVICE_STOP`. No new timer is introduced.

At most 64 messages are emitted per run: up to 63 observations, then a final
`TRUNCATED` marker in slot 64 instead of that observation; later events return no
line. Old callbacks retain their ownership checks. Trace state never controls
Bluetooth, storage, session admission or cleanup. Android's bounded log buffer
may retain lines after app closure. There is no app-owned trace file/export UI,
network capture, new dependency, continuous collector or global log clear.
Missing/truncated capture is incomplete evidence; a missing event is not proof it
never happened. Manual input can exhaust the budget. Click/report-reply and
best-effort teardown report submissions are not individually traced.

For the separately authorized single restart reproduction, first complete a new
compared enrollment if Forget cleared the app record; never adopt an unrecorded
OS bond. After Stop and completed teardown, reopen the process, Start and Connect
saved projector once. Leave a failure visible without retrying until current
process, fixed-tag messages are captured and validated. On success, manually
confirm actual left/up pointer movement. Tie every observation to the new source
and APK hashes; absence of a reproduced failure does not close the earlier one.

## Build and local checks

With SDK36 and the repository's pinned wrapper/plugin:

```sh
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./gradlew --offline \
  --console=plain -PhidProbe=true :experiments:hid_probe:testDebugUnitTest \
  :experiments:hid_probe:lintDebug :experiments:hid_probe:assembleDebug
```

The parent project's normal build excludes this module; release is disabled.
The APK is under this directory's ignored `build/outputs/apk/debug/`.
JUnit exercises real session, codec/persistence policy, native-owner barrier and
trace format/time/order/budget logic. Only the Android file boundary is replaced
in store failure tests. Native
Bluetooth/service callback order, AtomicFile behavior on the phone, and actual
report delivery require the live checks below; unit tests cannot establish them.
N1 adds pure gesture/submission tests and a source-linked Stop regression: the
test compiles the current Activity `Run.stop` method and scroll submission field
with fake native collaborators
using the installed JDK, then injects report failures for tap, scheduled motion
and scrolling, including permission loss and a rejected native submission. It checks cleanup ordering without duplicating the Stop implementation;
it is not Android framework or hardware execution.

## Existing enrollment checks (retain prior evidence)

After review and the controlled migration above:

1. Open the probe, grant Nearby devices, tap Start and wait for HID registered.
   With no saved projector, manually remove only the old test bond in both OSes
   if necessary, then use Make visible. Use the physical TV remote to pair.
2. Compare the numbers on BOTH intended devices before accepting their prompts.
   Cancel if matching numbers are absent. Return within five minutes and explicitly
   confirm in the probe. Durable save precedes connect; neutral submission precedes
   enabled controls. Keep the probe open after Connect.
3. On a safe launcher area try one small arrow, then one immediate click on a safe
   Netflix profile/title tile if desired. Avoid Play; observe the TV directly.
4. Stop. Check controls disable and the physical remote works. Wait for native
   teardown completed, then explicitly Start → Connect saved projector without
   forgetting the bond. No input may resume before Connect and neutral submission.
5. Close/reopen and repeat explicit Start → Connect. Separately test foreground
   loss, selected-link loss, adapter loss and notification Stop: none may reconnect
   or resume input automatically. Check multiple launcher/notification launches
   still expose one Activity owner.
6. Test selected bond removal/re-pairing while the Activity exists, including after
   Stop: it must revoke enrollment. Test Forget and fresh comparison. Record late
   callback/unknown-peer rejection only where reproducible; label other cases untested.

Record only observed outcomes with source/APK hashes in the main H1A evidence.
Never record peer addresses or pairing digits. H1A remains NOT PASSED; H1B remains
independent. Exact vendor encryption enforcement, native teardown ordering,
reconnect, and held-button fault cleanup are not established by these local checks.
Held-button testing needs its own accepted contract; an immediate click cannot prove it.

## N1 manual acceptance for the reviewed 0.5 artifact

The recorded0.5 sequence passed; see [N1 evidence](../../docs/gates/n1-report.md).
This historical procedure preserved the then-current bond. ADB may install/diagnose but
must not inject the gestures used as evidence.

1. Open → Start → wait for HID registered → Connect saved projector. Only after
   connection should the pad enable. With the physical remote open Netflix's
   profile chooser or browsing screen. Slide on the Nord pad in all directions;
   lift/reposition without jumps. Swipe and return to the starting point: no click.
2. Position the projector pointer over a safe profile/title and tap once on the
   Nord pad. Confirm actual profile/title opening. Avoid Play. Long stationary
   press and adding a second finger must not click. Lift both fingers, then check
   a fresh single-finger gesture works.
3. Stop during movement, then touch the pad: no more input. Check physical TV
   remote use. Start/Connect explicitly; no pending old motion should resume.
4. While connected, leave the app and return. Controls must remain stopped until
   a fresh Start/Connect. A finger held while input becomes disabled must not gain
   authorization when input re-enables; lift and start a new gesture.
5. In a fresh run, allow the five-minute deadline to expire while the app is
   visible. Confirm pad disables and no input resumes without explicit reconnect.

Record source/APK hash and actual observations in the N1 report. Do not mark the
whole N1 gate complete from movement alone, or erase the earlier reconnect failure.

## N2 manual acceptance for the reviewed0.6 artifact

The recorded0.6 sequence passed by owner observation on2026-10-25; see
[the N2 report](../../docs/gates/n2-report.md). For a new trial, first complete migration and layout
checks above. Each run has the same five-minute deadline.

1. Use the physical remote to open Netflix. Confirm pad movement in all directions
   and one safe profile/title tap still work. Avoid Play.
2. With room to scroll both ways, place the pointer over the relevant browsing
   area. Click Scroll down and up separately; confirm content moves both ways.
   Place the pointer over a title row and test right and left separately. Confirm
   no pointer jump or unwanted title opening from any scroll button.
3. If an axis does nothing, reposition once away from an edge and repeat that
   axis. If still unhandled, stop and report the direction/status. Do not use
   drag or ADB as a fallback; one working axis is only partial acceptance.
4. Tap Stop; pad/scroll controls must no longer send input and the physical remote
   must work. Wait for native teardown, then Start → Connect saved projector
   without pairing again; verify movement and a scroll step resume only then.
5. Leave the connected app and return: input must stay disabled. In another fresh
   run, leave it visible until five-minute expiry; pad/scroll input must disable
   with no trailing action and the physical remote must still work.

Broader screen-reader/font-scale, native crash cleanup, vendor encryption and
repeated reconnect reliability are not certified by this sequence. H1A/H1B and
product integration remain blocked independently of N2 behavior results.
