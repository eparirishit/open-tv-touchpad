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
The owner's spare-phone model and Android version are pending. Browser-only
implementation remains valid and continues independently.

## Remaining proof

- Supported onboarding and enforced encryption on every reconnect.
- Rejection of an unexpected projector and plaintext downgrade.
- Alternatively, physically controlled USB after actual OTG/data-port and cable
  capability are confirmed. USB is physical protection, not a TLS claim.
- Device-side cleanup after link loss/crash before real input integration.

Only this report was updated in the narrow follow-up. No settings, firmware,
helper app, ADB daemon or user trust was changed; no device probes were performed.
No phone-browser HTTPS result can satisfy this separate gate.
