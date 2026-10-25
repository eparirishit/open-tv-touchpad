# Disposable Nord HID probe — 0.3-diagnostic

The owner accepted remembering the verified projector for this isolated diagnostic
on 2026-10-24, including the OS-bond continuity limitation. This is not a product
companion, network bridge, or H1A PASS. The physical TV remote remains independent.

Uses native `BluetoothHidDevice` on API31+, the existing Gradle/JUnit toolchain,
and no new dependencies or custom crypto. Separate application ID; opt-in Gradle
module; debug-only variant. No INTERNET, scan, location, accessibility or storage
permission. Nothing starts the app in response to a Bluetooth broadcast.

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
- Durable enrollment contains only format version `1` and one validated peer
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
  reopen; no automatic recovery or hidden Bluetooth operations are attempted.
- The Activity observes selected-peer pairing/re-pairing and any departure from
  BONDED even after Stop, revokes the record and stops input. Missing bond at Start
  or Connect also invalidates enrollment. Forget clears the app record/session;
  removing the OS bond is a separate manual action in system settings.
- Only fixed small arrows and one immediate left-button press/release exist.
  No held drag, keyboard, continuous motion, background input or remote endpoint.
  Crash/radio loss may prevent release; actual cleanup remains a hardware check.

## Build and local checks

With SDK36 and the repository's pinned wrapper/plugin:

```sh
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./gradlew --offline \
  --console=plain -PhidProbe=true :experiments:hid_probe:testDebugUnitTest \
  :experiments:hid_probe:lintDebug :experiments:hid_probe:assembleDebug
```

The parent project's normal build excludes this module; release is disabled.
The APK is under this directory's ignored `build/outputs/apk/debug/`.
JUnit exercises real session, codec/persistence policy and native-owner barrier
logic. Only the Android file boundary is replaced in store failure tests. Native
Bluetooth/service callback order, AtomicFile behavior on the phone, and actual
report delivery require the live checks below; unit tests cannot establish them.

## Manual checks still pending for 0.3

No installation or device action is part of this implementation task.
After review and separately authorized installation:

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
