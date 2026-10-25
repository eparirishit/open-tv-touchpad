# H1A — Protected projector transport

Status: NOT PASSED; device integration blocked. Policy selected 2026-10-24.

The owner explicitly requires a protected projector link and authorizes independent
browser-only H1B implementation. No legacy plaintext ADB beta exception applies.

## Existing evidence

Certified target: XGIMI Horizon XK03H, Android TV 11. H0's functional prototype
uses legacy Wi-Fi ADB, which does not satisfy the product requirement.
Prior read-only checks found Wi-Fi/QR support flags true, no active TLS port,
and no wireless-debugging settings activity. These flags do not establish a
usable or authenticated TLS transport. Reviewed AOSP host code accepts any server
certificate; installed ADB37.0.1 has no verified mandatory-TLS/peer-pin option.
The exact binary/source correspondence has not been established.

## Narrow follow-up conclusion (2026-10-24)

No supported existing software path in the reviewed evidence demonstrably meets
both mandatory encryption and authentication of the enrolled projector on the
selected connection and every reconnect. This is an unresolved acceptance gate,
not a claim that every existing implementation is incapable of meeting it.
Native wireless ADB remains a candidate, not an approved product adapter.
Browser-only H1B work may continue; real device integration must remain blocked.

The first hardware blocker is supported vendor pairing/onboarding. The wireless
debugging settings intent did not resolve; the general development settings
intent did resolve. That does not prove the general screen contains a functioning
wireless pairing flow, nor does it prove such a flow is impossible. Capability
flags and an advertised port cannot substitute for this proof.

The existing source review establishes separate transport limitations:

- A TLS service name is discovery/selection information, not an enforced
  encryption policy. In the reviewed AOSP snapshot, mDNS calls ordinary
  `connect_device()`, which registers the socket with `use_tls=false`.
  Host protocol handling starts TLS on `A_STLS`, but also accepts ordinary
  `A_CNXN`/legacy authentication. Thus a TLS-looking service resolving to a
  legacy listener can still satisfy ordinary connection/state checks.
- Host TLS certificate verification accepts any server certificate. Pairing
  stores a device GUID, not a server-certificate pin for subsequent connections.
  A matching service name, address or model is not cryptographic peer proof.
- The reviewed device-tracking schema has transport ID and socket/USB type,
  but no negotiated-TLS or authenticated-peer field. A transport ID selects a
  transport; it does not attest to encryption. Reconnection can reuse the
  transport object, so an ID is not an immutable TLS-session identifier.
- Installed CLI version/help were checked previously: ADB 37.0.1-15733141
  exposes no documented mandatory-TLS or peer-pin option. Source findings are
  qualified by the missing exact binary/source correspondence; they do not
  constitute a completed audit of that installed binary.

No separate TLS peek probe, advertisement check, or log-parsing gate is accepted
as enforcement on the actual control connection. No custom TLS implementation,
VPN alternative, plaintext fallback or new dependency is proposed.

## Evidence references

These are the sources already reviewed; this follow-up performed no new device
check. The AOSP `main` snapshot reviewed was commit
`1cf2f017d312f73b3dc53bda85ef2610e35a80e9`; it is not asserted to be the source
revision of the installed CLI.

- [mDNS connection selection](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/client/transport_mdns.cpp)
  and [ordinary connection registration](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/client/transport_emulator.cpp).
- [ADB packet handling](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/adb.cpp)
  and [pairing GUID storage](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/client/adb_wifi.cpp).
- [Host certificate verification and reconnection, Android 17 source](https://android.googlesource.com/platform/packages/modules/adb/+/refs/tags/android-vts-17.0_r1/transport.cpp)
  and [host/device metadata schema](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/proto/adb_host.proto).
- [Official Horizon support](https://global.xgimi.com/pages/horizon-support)
  documents USB disk/peripheral use, not a confirmed USB device/debug interface
  for this target. [Android USB documentation](https://developer.android.com/develop/connectivity/usb)
  distinguishes hardware-dependent USB host and accessory roles. A data-capable
  host port for a disk is not sufficient evidence of Mac-to-projector USB ADB.

## Next indispensable input

Physically controlled wired USB is the next concrete candidate under D1, not a
PASS and not an encrypted-transport claim. Before a hardware check, the owner
must confirm that a direct, physically controlled Mac-to-projector cable route
is practical and identify an externally accessible projector USB device/debug
port with the appropriate data cable, supported by model-specific vendor
documentation or confirmation. If the port role is unknown, vendor confirmation
is the next required input; the observed USB-debugging toggle and disk ports do
not settle it. Do not assume an arbitrary USB host-to-host cable is suitable.

After that input and separately authorized verification, the USB candidate must
demonstrate explicit selection of the physically connected intended projector,
host authorization, operation without exposed legacy network ADB, no fallback
to a network transport, and safe link-loss/crash cleanup. The threat boundary
would be controlled physical access, not server-certificate authentication.

## Owner constraint update

The owner reports no USB data connection or matching cable and asks whether
another authentication method can work. Protected projector transport remains
required. Additional browser passwords do not encrypt legacy ADB; wireless
alternatives need encryption and peer authentication on the actual control link.
Read-only feasibility research is evaluating supported native wireless pairing
and a separately designed companion/bridge. No new helper is approved or installed.

[Primary-source research](../research/2026-10-24-protected-wireless-options.md)
identifies the spare Android phone's Bluetooth HID Device API as the next narrow
candidate. It requires actual profile availability, authenticated pairing and
input/lifecycle checks; no gate PASS follows from API availability. A projector
companion still lacks a demonstrated protected shell bootstrap/restart path.
The owner reports a OnePlus Nord running Android12. Subsequent authorized
read-only diagnostics connected to the phone and verified the prerequisites
below. Browser-only implementation remains valid; no companion is installed.

## Nord live configuration check (2026-10-24)

Repository baseline: `5b3453c352c1a8e90b4c61af7697618d331b08bc`.
No probe APK or input implementation was used for this configuration check.

| Read-only observation | Result |
| --- | --- |
| Manufacturer / model | OnePlus / AC2001 |
| Android / API | 12 / 31 |
| Build / security patch | AC2001_11_F.24 / 2023-12-05 |
| Bluetooth and BLE features | Present |
| `profile_supported_hid_device` resource | `true` |
| Bluetooth package declares `HidDeviceService` | Yes |
| Bluetooth disabled, initial runtime HID service | Absent |
| After owner enabled Bluetooth | Bluetooth setting `1`; `HidDeviceService` present in Bluetooth manager dump |

Wireless-debugging pairing completed using a local masked prompt. Direct
connection attempts failed; a discovered ADB transport subsequently connected.
Queries selected the unique connected AC2001 and checked its manufacturer.
Device endpoints, identifiers and raw Bluetooth dumps are excluded from this
report. Discovery/model selection is diagnostic targeting, not cryptographic
peer authentication or approval of ADB as a product transport.

This establishes firmware profile enablement and runtime service availability.
It does not establish an app's successful HID registration callback, projector
pairing security, HID input, reconnect behavior or background lifetime. Those
require an isolated foreground HID probe and actual pairing/input evidence.
No app was installed, projector pairing initiated, or input injected. The owner
enabled debugging and Bluetooth; the diagnostics did not change settings.
H1A remains **NOT PASSED**.

## Isolated HID probe prepared and installed (2026-10-24)

The owner approved continuing the proposed small foreground diagnostic app.
The bounded design and manual procedure are in
[`experiments/hid_probe/README.md`](../../experiments/hid_probe/README.md).
This changes the earlier research-only authorization; it does not authorize
a production companion or change protected-transport acceptance criteria.

- Source commit: `954d66654621f4b05cc7659adb9e281a9d96bd97`.
- APK SHA-256: `5cc4d8c52d64922f0f38e6fa7472a820a9f1712a288b3e993687248459279847`.
- Package / version: `dev.opentvtouchpad.hidprobe` / `0.1-diagnostic` (1).
- Local verification: 21 unit tests passed, debug assembly passed, Android lint
  passed with no errors and 12 warnings about English-only diagnostic text and
  the existing SDK36 toolchain. Normal Gradle project enumeration excludes
  the module; its opt-in build disables release variants.
- Packaged manifest inspection found only Bluetooth CONNECT and ADVERTISE
  permissions; no INTERNET permission. No runtime dependency was added.
- Independent review found a disconnect-before-first-success lifecycle gap.
  Fixed by forwarding every disconnect to the eligibility gate: any selected
  disconnect after comparison confirmation stops even a pending attempt.
  Deliberately rejected preconfirmation automatic connections never enable
  input and do not discard pending pairing observations. Regression tests cover
  both paths. Follow-up review found no remaining actionable issues.
- Installation and Activity launch on the uniquely selected, manufacturer-checked
  Nord were confirmed. No permission was granted through ADB, no pairing was
  initiated and no input was injected. The app waits for the user to Start.

The user subsequently reported **“HID registered”** after granting Nearby devices
and starting the probe. That UI status is set only by
`onAppStatusChanged(..., true)`: actual app registration is confirmed by user
observation for the source/APK above. It is not merely the registerApp return value.
The next manual check is discoverability and matching Numeric Comparison digits
on both the Nord and intended XGIMI. No pairing digits are requested or recorded.

The user then reported completing pairing and, when asked whether both screens
showed matching numbers compared before acceptance, replied: “yes, both were
showing the same number and the tv shows OnePlus Nord connected”. This records
the human matching-number ceremony and TV connection indication. It does not
establish the probe's own post-confirmation HID connection, negotiated encryption
properties or reconnect enforcement. No pairing digits were collected.

The user has been asked to confirm the comparison in the probe, wait for
“Mouse connected,” then try three manual Move right steps without clicking.
That attempt was blocked: the user reported the disabled/different-status option,
then the exact message “App left foreground. Stopped. Close and reopen for another
test”. This message identifies the probe's `onStop` cleanup path; registration and
in-memory pairing eligibility are cleared there. The user could not identify
whether discovery, pairing UI, or another foreground transition triggered it.
Filtered device lifecycle-log inspection returned no matching retained records;
it did not establish a specific triggering system Activity. A discovery-only
reproduction is next, preserving the existing bond and sending no input.
The discovery-only reproduction then completed: after reopening only the probe
(bond retained), the user started HID, approved Make visible, returned without
pairing/app switching, and reported “HID registered — still active”. Thus this
trial does not support discoverability as the stop trigger. Fresh pairing without
manual phone app switching is the next isolation step; the prior bond cannot
authorize input in a new probe session. Source review also notes that a later
`onStop` can overwrite an earlier failure message, so the original callback order
is unresolved. No lifecycle bypass or service was added.
In the subsequent fresh-pairing trial, the user reported that selecting Pair on
the TV immediately produced the same stopped message while both screens showed
matching numbers. This localizes the failure to the pairing phase rather than
the previously successful discovery-only step. The exact callback order before
the final status remains unknown because the original UI could overwrite an
earlier reason on `onStop`.

The scoped diagnostic fix uses a non-exported, non-restarting `connectedDevice`
foreground service with a visible Stop notification and five-minute elapsed
deadline. This preserves native HID foreground eligibility during setup screens;
input and confirmation still require a resumed Activity. After confirmation,
any pause stops the session, including a pending connection. Activity destruction,
Stop, registration loss, service loss and expiry also terminate it. The first
terminal status is now retained. No background input or runtime network bridge
is added. Local build/lint and 28 unit tests pass; lint has zero errors and 13
English-text/SDK-version warnings. Independent review of the entire revised
probe, including service and connected callers, found no actionable findings.

Revised artifact installed and launched on the manufacturer-verified Nord:

- Source: `93da0d08a39ddec450a3382e997534ae820b7a4b`.
- Version: `0.2-diagnostic` (2).
- APK SHA-256: `c75a1e61875fc3af0de0d60087efafa4d8585ab5b8968c03931ab3a2df251d6b`.
- Packaged permissions: CONNECT, ADVERTISE, FOREGROUND_SERVICE and
  FOREGROUND_SERVICE_CONNECTED_DEVICE; no INTERNET permission.
- Only the probe was stopped/updated/reopened. No bond was altered or input sent.

The user has been asked to remove only the test bond on both devices, then repeat
fresh matching-number pairing, explicit probe confirmation and three manual
rightward steps within the five-minute window. The user reported **“Pairing stays
active; pointer moves”** for the revised artifact above. This confirms survival
of the pairing-screen transition and actual Bluetooth pointer movement by user
observation. It does not establish a Netflix click, encryption properties or
reconnect/crash cleanup. No ADB input was used for this check.
The subsequent manual Netflix/Stop check also passed by user observation:
**“Click opens the target; Stop disables controls; physical remote works”.**
The requested target was a Netflix profile avatar or title tile, avoiding Play;
the user's answer does not distinguish which target was used. Record only that
the intended target opened. After explicit Stop, probe controls were disabled
and the physical remote remained usable.

**Basic HID compatibility probe: PASS, scoped to this Nord/XGIMI and artifact.**
Observed: actual HID registration, matching-number ceremony, survival of pairing
UI, pointer movement, Netflix target click, and manual Stop/control disablement
with continued physical-remote operation. The test is now stopped per user report.
It did not measure native disconnect completion, held-button release on process
death, arbitrary background lifetime, scrolling/drag, or negotiated-link security.
H1A remains **NOT PASSED**; the Mac-to-phone secure command hop is also unbuilt.

## Remaining proof

Follow-up source review traced Android12 AOSP HID authentication/encryption flags
through L2CAP into the security manager. This supports native platform enforcement
as a candidate trust basis; it does not establish the Nord's negotiated security
or exact firmware correspondence. Modern encryption-status APIs are unavailable
on API31. Details and primary sources are in the
[enforcement trace](../research/2026-10-24-protected-wireless-options.md#android-12-enforcement-trace-and-remaining-verification-2026-10-24).

One further live negative test passed: after reopening the probe with the old
bond intact and starting registration without new pairing, the user reported
“Controls disabled; no TV Connect option”. Startup did not authorize input from
the retained bond. A TV-initiated reconnect attempt was unavailable in that UI
and was not exercised. This is not a successful reconnect test or full H1A PASS.

Foreground-loss check on the same 0.2-diagnostic artifact: **PASS by user
observation**. The user established a fresh confirmed connection and verified
pointer movement, then left the probe and returned without pressing Stop. All
controls except STOP / Disconnect and Close probe were disabled. The exact status
was “App left foreground after confirmation. Stopped. Close and reopen.” The
user also confirmed “Physical TV remote still works”. Returning did not restore
input eligibility. This tests Activity foreground loss and return, not process
death, radio loss, native disconnect completion or held-button release.

Controlled adapter-loss trial, same 0.2-diagnostic artifact: the user reported
“Mouse connected; pointer moves; ready” after fresh pairing. Read-only checks
selected the unique Nord, verified OnePlus manufacturer, adapter ON and resumed
probe Activity. The supported `svc bluetooth disable` command was issued on the
Nord; `dumpsys bluetooth_manager` confirmed OFF. After one second, a `finally`
cleanup issued `svc bluetooth enable` and confirmed ON. Commands completed
successfully. No input injection or bond change was issued, and no phone settings
screen was opened. This is a controlled local adapter shutdown/restoration, not
an out-of-range peer disconnect, process crash or held-button test.
The user confirmed **“Stopped; movement/Click disabled; physical remote works”**
after restoration without restarting the probe. **Controlled adapter-loss and
restoration: PASS**, combining observed OFF/ON states with that user report.
Restoration did not silently resume input. The exact terminal status string was
not supplied, so no specific callback ordering is inferred. This does not prove
successful reconnect, peer-only link loss, process-crash or held-button cleanup.

- Supported onboarding and enforced encryption on every reconnect.
- Rejection of an unexpected projector and plaintext downgrade.
- Alternatively, physically controlled USB after actual OTG/data-port and cable
  capability are confirmed. USB is physical protection, not a TLS claim.
- Device-side cleanup after link loss/crash before real input integration.

The earlier source-review follow-up performed no device probes. The later Nord
configuration check above adds live diagnostics, not a protected-link PASS.
No phone-browser HTTPS result can satisfy this separate gate.

## Accepted remembered-projector diagnostic (2026-10-24)

After reviewing the retained-bond proposal, the owner selected “remembering the
verified projector approach.” The app may retain one peer selector following a
fresh verified Numeric Comparison ceremony, then require explicit Start/Connect
on later runs. Android and the projector's native Bluetooth stack are the
authentication/encryption authority. API31 cannot independently establish that
the current OS-managed bond still uses the originally compared key after a
replacement while the app was absent. This limitation was explicitly disclosed
and accepted; a saved address/name/bond state is not cryptographic proof.

The [accepted design](../superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md)
and [implementation plan](../superpowers/plans/2026-10-24-nord-hid-reconnect.md)
govern the isolated extension. No network permission or production bridge is
authorized. Previous 0.2 stop/negative results remain valid for that artifact;
they are not successful reconnect evidence for a new APK. H1A remains NOT PASSED.

### Remembered-projector artifact prepared and installed

- Source commit: `7643aa80b0fb5f59d96c0890c28c752e74141b3a`.
- Version: `0.3-diagnostic` (3).
- APK SHA-256: `87b7b6c7e9aa4c278b37d0a4284bbf9a0288c191c796ebbf455f3a192b39209e`.
- Verification: 48 unit tests, no failures/errors/skips; debug build passes;
  lint has 0 errors and 20 diagnostic-localization/pinned-SDK warnings.
- Independent review included the complete module change, all new files and
  connected callers; no actionable findings. Main reran the exact-source Gradle
  checks and verified packaged version, debug flag and the four allowed Bluetooth/
  service permissions. No INTERNET permission; backup rules are packaged and the
  enrollment record uses native app-private no-backup storage.
- Installation: selected the unique online AC2001 and verified OnePlus/API31;
  stopped only the probe, updated its APK without clearing data, verified installed
  version3, launched it and confirmed the Activity resumed. No Bluetooth setting,
  bond, or input command was changed/issued by this installation.

The new implementation persists a selector after verified first enrollment but
never persists control authorization. Explicit Start/Connect, a current bonded
peer, successful registration/connection and neutral submission are required.
Stop retains the selector but fences input; old native ownership must finish
teardown before another run begins. Forget and observed selected-peer re-pairing/
bond departure revoke enrollment. Failed storage operations explicitly warn
that disk may retain an old selector; they do not claim durable erasure.

The owner was asked to establish one fresh compared enrollment, then Stop, wait
for native teardown, and Start → Connect saved projector without removing the
bond. The user answered **“Reconnect works; physical remote works”.**
**Explicit saved-projector reconnect: PASS by user observation**, scoped to the
0.3 artifact and Nord/XGIMI setup above. Pointer operation resumed without another
pairing ceremony, and the physical remote remained usable. This is a reported
end-to-end result, not a captured trace of native callback ordering.

### Process-restart trial: first attempt failed, retry connected (2026-10-25)

The requested procedure was Stop and completed teardown, Android Force stop,
reopen, verify saved selection with input disabled, then explicit Start/Connect
and movement. The owner reported that Connect saved projector did not work after
force restart/reopen and displayed:

> Projector disconnected. No input enabled. Native teardown completed. Tap start for a new run

Source tracing identifies this message with a selected-peer DISCONNECTED callback
after an explicit connection attempt; it can occur before a first CONNECTED
callback. The message does not establish whether the failure was stale callback
ordering, a native link failure, peer readiness or another cause. It must not be
fixed by ignoring disconnects or automatically retrying without evidence.

The owner subsequently reported **“tried again, and this time it connected”.**
The owner initially said connected status appeared but movement did not work.
Without restarting or pairing again, a direction check then produced the user
report **“Buttons enabled; left/up moves the pointer”.** Thus actual pointer input
works in the restarted session on retry. A pointer at the right screen edge is a
possible explanation for the earlier right-only observation, not a proven cause.

**Saved enrollment after process restart, reconnection and movement on retry:
PASS by user observation, with the first-attempt failure retained.** This is not
a first-attempt reliability PASS or an independent negative-input-admission test.
No repeat pairing/restart was requested between the connected-state and direction
checks; no code change was made. Additional diagnostics confirmed the probe was
resumed, its foreground service was running and Bluetooth was ON. The UI hierarchy
was unavailable, so enabled controls and pointer movement are user observations.
A bounded read-only inspection on the manufacturer-verified Nord found no matching
retained HID callback/failure markers in the selected log tags. This supplies no
cause or event-order proof. No raw logs/identifiers were saved or displayed, and
no app, bond, radio, or input was changed by that check.

Force-stop/reopen after Stop tests persistent enrollment; it is not an abrupt
crash or held-button cleanup test. First-attempt reconnect reliability, observed
OS bond-removal/re-pair revocation, fault cleanup and exact-vendor encryption
remain unresolved. H1A remains **NOT PASSED**; H1B/product integration remain gated.

### Explicit Forget with retained OS bond (2026-10-25)

The requested procedure was Stop/completed teardown → Forget projector → Start,
leaving the OS Bluetooth bond intact. The user confirmed **“No saved projector;
Connect and movement disabled”.** **Explicit app-enrollment revocation and
old-bond-only input rejection: PASS by user observation**, on the same 0.3 artifact
and Nord/XGIMI setup above. The bond alone did not authorize a new control session.

This does not certify Forget persistence after process death, automatic revocation
on OS bond removal/re-pairing, storage-failure handling on hardware, or a new
incoming connection attempt. No code/device changes or repeat tests were needed
to record this result. The check is complete; broader H1A/H1B gates remain unchanged.
