# Protected wireless input options

2026-10-24. Read-only feasibility research; repository snapshot `92b83358c75e4217287f27cf4717bf75f6885c81`. This records research only; it does not authorize a new companion implementation. No device access, settings, installation, code changes, or subagents. Existing browser work continues; H1A remains **NOT PASSED**.

**Extra authentication alone cannot protect the existing plaintext ADB input link.** A password, token, or client signature can restrict command admission without hiding commands or authenticating the remote endpoint. The replacement must provide encryption and verified peer identity on every wireless hop. Local certificate/key enrollment can meet the no-account/no-cloud/no-VPN requirement in principle; it does not establish that either input path works. The known Android TV Remote v2 pinned-TLS key path remains separate from the unresolved pointer path. Native ADB TLS onboarding and host peer pinning remain unresolved as supplied in the task.

| Candidate | Primary-source support | Feasibility verdict |
| --- | --- | --- |
| Projector companion → local shell scrcpy UHID helper | scrcpy can write `/dev/uhid` under the shell identity; a companion can handle network transport under its own app identity. | **Unproven and bootstrap-blocked under current constraints.** Protected shell launch, local IPC authorization, and survival after disabling ADB all need evidence. |
| Mac → encrypted/authenticated commands → spare Android phone → Bluetooth HID → projector | Android has a public HID Device API from API 28; AOSP implements foreground eligibility and HID authentication/encryption. | **Best narrow experiment, not a confirmed solution.** Phone profile availability, projector mouse/keyboard behavior, actual pairing security, and lifecycle remain unverified. |

**Projector companion: the privilege boundary is the obstacle.** Android 11 declares `INTERNET` and `FOREGROUND_SERVICE` as normal permissions, but `INJECT_EVENTS` is signature-only. Running a shell executable from an ordinary app does not acquire the ADB shell identity: the app remains sandboxed. AOSP gives ADB subprocesses the `uhid` supplementary group, permits the shell SELinux domain to access UHID, and creates `/dev/uhid` as `0660 uhid:uhid`. This explains the shell-based H0 result; it supplies no ordinary-app permission to replace the helper. Sources: [Android permissions](https://android.googlesource.com/platform/frameworks/base/+/android11-release/core/res/AndroidManifest.xml), [app sandbox](https://source.android.com/docs/security/app-sandbox), [ADB groups](https://android.googlesource.com/platform/system/core/+/android11-release/adb/daemon/main.cpp), [shell policy](https://android.googlesource.com/platform/system/sepolicy/+/android11-release/private/shell.te), [device permissions](https://android.googlesource.com/platform/system/core/+/android11-release/rootdir/ueventd.rc).

The helper must already be launched with shell privileges through an acceptable bootstrap. With no USB and protected wireless ADB onboarding unresolved, there is no demonstrated protected bootstrap or restart path. A companion foreground service does not promote its child to shell or restart a dead shell helper with that identity. Reboot loses the running helper. ADB shell transport closure can send SIGHUP; scrcpy closes its UHID devices when its control loop ends. Detachment and survival across the actual firmware's ADB-disable operation cannot be inferred from H0. Sources: [ADB subprocess lifetime](https://android.googlesource.com/platform/system/core/+/android11-release/adb/daemon/shell_service.cpp), [scrcpy 5.0.1 controller](https://github.com/Genymobile/scrcpy/blob/v5.0.1/server/src/main/java/com/genymobile/scrcpy/control/Controller.java).

Local scope also needs proof. Stock scrcpy accepts a local Unix socket without authenticating the companion; its socket name is not a credential. Abstract Unix sockets lack filesystem path permissions, and cross-domain connection access remains subject to SELinux. The checked app policy does not establish a general app-to-shell socket grant. Peer-credential checking is available, but stock scrcpy does not perform it here. A loopback TCP alternative would still need authentication against other local apps; loopback is not app isolation. No socket alternative is claimed feasible on this projector. Sources: [scrcpy connection handling](https://github.com/Genymobile/scrcpy/blob/v5.0.1/server/src/main/java/com/genymobile/scrcpy/device/DesktopConnection.java), [LocalServerSocket](https://developer.android.com/reference/android/net/LocalServerSocket), [LocalSocket peer credentials](https://developer.android.com/reference/android/net/LocalSocket#getPeerCredentials()), [Android 11 app policy](https://android.googlesource.com/platform/system/sepolicy/+/android11-release/public/app.te).

**Spare Android phone: supported API, conditional hardware path.** `BluetoothHidDevice` supports registering HID descriptors and sending reports to a host without root or projector ADB. It requires Android 9/API 28 or later and an enabled HID Device profile on the phone; API availability alone is insufficient. Only one app can register, registration disables the phone's HID Host service, and success must be observed through `onAppStatusChanged`, not just the method's return value. Sources: [official HID Device API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice), [profile enablement and registration source](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/android/app/src/com/android/bluetooth/hid/HidDeviceService.java).

Permissions depend on OS/target: legacy `BLUETOOTH`/`BLUETOOTH_ADMIN`; Android 12+ targeting apps need runtime `BLUETOOTH_CONNECT`, with `BLUETOOTH_ADVERTISE` for discoverability and `BLUETOOTH_SCAN` if scanning. Older scanning can require location permission. LAN command transport needs `INTERNET`. The dedicated phone requires a native bridge app, but no account or VPN; the iPhone can remain a browser client. Source: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

The API automatically unregisters an app that is no longer foreground. AOSP Android 11 and current main use an `IMPORTANCE_VISIBLE` cutoff (200); a foreground service has importance 125, so **source inspection supports foreground-service eligibility**, not a guarantee of persistence on this spare phone. Use of such a service entails a visible notification and applicable start restrictions; modern targets need the `connectedDevice` type and its permissions. Binder death unregisters the HID app. Screen-off, process death, reconnection, and release of held input still require hardware checks. Sources: [Android 11 HID service](https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android11-release/src/com/android/bluetooth/hid/HidDeviceService.java), [importance constants](https://android.googlesource.com/platform/frameworks/base/+/android11-release/core/java/android/app/ActivityManager.java), [foreground service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device).

Bluetooth security is a separate hop from Mac–phone TLS. AOSP configures HID authentication and encryption, but this does not prove the negotiated pairing mode or firmware behavior. Bluetooth “Just Works” lacks pairing MITM protection; verified Numeric Comparison or authenticated Passkey Entry provides a stronger trust basis. A bonded-device name or successful mouse movement alone does not establish the required protection. Mac–phone transport would also need verified server identity and client authorization; no such bridge transport has been implemented or tested here. Sources: [AOSP HID security setup](https://android.googlesource.com/platform/system/bt/+/android11-release/bta/hd/bta_hd_act.cc), [Bluetooth SIG pairing models, §§5.2.3–5.2.4](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core-62/out/en/architecture,-change-history,-and-conventions/architecture.html).

**One next feasibility check:** in a separately authorized, isolated hardware probe, keep a minimal HID test app visible on the spare Android phone, establish that HID Device registration succeeds, and pair it to the projector while recording the actual pairing method. Send one mouse move/click and one keyboard press/release to the sideloaded Netflix UI, without using ADB input. Stop if the profile is unavailable, pairing is unauthenticated, or input fails. This checks the largest unknown before building a Mac command bridge. Even success would establish only basic compatibility/pairing evidence; encryption evidence, background lifetime, safe release, and the Mac–phone authenticated transport would remain necessary before H1A PASS.

No hardware or security gate was tested. Accessibility gesture injection was not revisited; no new architecture was implemented.

## Reported phone and bounded next check

The owner reports OnePlus Nord, Android12 (2026-10-24). Android12 exceeds the
API28 minimum for BluetoothHidDevice and uses the Nearby devices permissions
for a modern-target companion. This establishes OS eligibility, not that the
Nord firmware enables the profile. The original Nord's official specifications
list Bluetooth5.1, which by itself does not establish HID Device support.
Sources: [Android HID API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice),
[original Nord specifications](https://www.oneplus.com/uk/nord-specs).

The proposed first test remains an isolated foreground HID probe: register the
phone as a mouse, verify the projector pairing ceremony, then demonstrate pointer
movement, a click and Stop/disconnect with the physical remote still usable.
No Mac-to-phone bridge, background-service reliability claim, root, USB or
projector accessibility injection belongs in this first test. A passing test
would justify the next design; it would not certify the entire protected link.
No probe APK was built or installed by this information check.

## Live diagnostic follow-up (2026-10-24)

Authorized ADB diagnostics subsequently confirmed OnePlus AC2001, Android12/API31,
build AC2001_11_F.24, security patch 2023-12-05. Bluetooth/BLE features are present,
the HID Device profile resource is enabled, and the Bluetooth package declares
`HidDeviceService`. After the owner enabled Bluetooth, the service appeared in
the runtime Bluetooth manager dump. The earlier statements about no device
access describe the research phase, not this follow-up.

The firmware prerequisite is now verified. App registration, authenticated
projector pairing and actual mouse input remain untested. No probe app was
installed, no projector pairing was initiated, and no input was sent. See the
[H1A evidence report](../gates/h1a-report.md#nord-live-configuration-check-2026-10-24)
for methods and limits. This does not authorize or establish a production bridge.

The owner subsequently approved the isolated probe. Source `954d666` builds a
separate debug-only native foreground app with no network permission. Build,
21 unit tests and lint passed; an independent review and disconnect-fix review
completed. Installation and launch on the Nord are confirmed. The user then
reported “HID registered,” confirming the actual registration callback. Manual
pairing/input results remain pending. See the
[probe procedure](../../experiments/hid_probe/README.md) and
[H1A report](../gates/h1a-report.md#isolated-hid-probe-prepared-and-installed-2026-10-24).
This is feasibility work, not acceptance of a production companion architecture.

The actual setup trial localized an Activity foreground-loss stop to fresh
pairing; the separate discovery-only trial remained registered. The diagnostic
now uses a bounded native connectedDevice foreground service so setup prompts
can cover the Activity, while explicit comparison and all input still require
the resumed screen. Any pause after comparison stops the test. This replaces
the probe's original no-service assumption; it does not establish background
input, negotiated encryption or production bridge feasibility. Revised hardware
verification remains required. Source `93da0d0` passed 28 unit tests, Android
build/lint and independent review; version 0.2-diagnostic is installed and
launched on the Nord. No service/pairing/input hardware success is inferred from
installation. In the subsequent live retest, the user confirmed “Pairing stays
active; pointer moves”, establishing pairing-screen survival and actual Bluetooth
pointer movement on this setup. The user subsequently confirmed “Click opens
the target; Stop disables controls; physical remote works”. Basic HID
compatibility is established for the tested artifact/setup. Native disconnect
completion, held-button crash release, security and reconnect behavior remain
unverified; this narrow result does not establish H1A PASS or a production bridge.

## Android 12 enforcement trace and remaining verification (2026-10-24)

Read-only source audit against AOSP tag `android-12.0.0_r1`; this is not an audit
of the exact OnePlus firmware binary. Public sources were retrieved with normal
TLS verification; no Bluetooth packet capture, bond key extraction or device
setting change was performed.

- [HID connection implementation](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/stack/hid/hidd_conn.cc):
  both control and interrupt registration, and outbound connection requests,
  supply `BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT`.
- [Flag definitions](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/bta/include/bta_api.h)
  cover incoming and outgoing authentication/encryption. [L2CAP wrappers](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/stack/l2cap/l2c_api.cc)
  apply that security policy before connection processing.
- [Security manager](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/stack/btm/btm_sec.cc)
  adds MITM requirements in SP/SC modes and starts authentication/encryption
  procedures when required state is absent. These flags are platform enforcement
  evidence, not a measured cipher/key-strength result or proof of this vendor's
  every-reconnect behavior. They do not establish Secure Connections Only mode.
- The documented [encryption-change broadcast](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#ACTION_ENCRYPTION_CHANGE)
  was added in API36; [getEncryptionStatus](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#getEncryptionStatus(int))
  in version36.1. These APIs are unavailable to this API31 phone. Their absence
  is not evidence that its HID link is unencrypted. No unsupported reflection
  or separate log-derived runtime security gate is proposed.

The existing probe has no retained enrollment and deliberately requires a new
observed comparison ceremony. Reopening it with the existing bond and starting
registration produced the user report **“Controls disabled; no TV Connect option”**.
Startup rejection of old-bond authorization passed. An incoming reconnect attempt
was unavailable through the tested TV UI and remains untested.

Independent review identified the smallest remaining proof boundaries:

| Check | Current probe / status |
| --- | --- |
| Reopen with old bond; input remains disabled | PASS by user report; TV-side Connect unavailable |
| Leave fresh confirmed session and return; session stays stopped | PASS by user report: exact foreground-loss status, controls disabled except Stop/Close, physical remote works |
| Fresh session, TV-side link loss/restoration without phone app switching | Supported if a suitable TV action exists; not yet run |
| Confirmed foreground session, Nord adapter OFF then ON | PASS: adapter states verified; user confirmed stopped controls and physical-remote usability after restoration; does not substitute for peer-only/out-of-range loss |
| Neutral-state process termination and relaunch | Diagnostic-only test possible; not yet run; force-stop must not be mislabeled as a crash |
| Successfully reconnect to the enrolled peer after link loss/restart | Requires a separate retained-enrollment/reconnect contract; current probe intentionally rejects it |
| Process/radio loss after button-down but before button-up | Requires an explicitly bounded fault-test contract; current probe exposes no held gesture |

A completed click or idle termination cannot prove receiver cleanup after a lost
release. Before a revised reconnect probe, define the OS-bond trust basis,
enrollment/revocation and bond-replacement handling, actual-link security evidence,
neutral-state admission and explicit reconnect behavior. Do not silently treat a
saved name/address as authentication. No product backend or new probe behavior
was implemented by this audit. H1A remains NOT PASSED.
