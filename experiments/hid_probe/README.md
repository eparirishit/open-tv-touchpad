# Disposable Nord HID probe

Authorized scope: the owner's 2026-10-24 “sure, continue” to the proposed
registration / authenticated pairing / manual mouse and Stop check. This is a
diagnostic spike, not a product companion or a change to H1A acceptance criteria.

Uses the installed Android build toolchain and native BluetoothHidDevice API;
no new runtime dependency. Separate application ID, opt-in Gradle module,
debug-only variant. No INTERNET, scan, location, accessibility or storage
permission, no receiver that starts the app, no saved peer/secret.
The two runtime permissions are Nearby devices CONNECT and ADVERTISE. The native
`connectedDevice` foreground service also declares its two normal permissions.
It has a visible Stop notification, no input API, no automatic restart and no
external entry point. This addresses the observed Nord pairing-screen transition;
it does not add a background remote-control capability.

`ProbeActivity` owns the manual UI and Bluetooth callbacks; `ProbeSession` owns
pairing, foreground and input eligibility. `PairingGuardService` only owns the
native foreground notification and bounded lifetime, and calls Activity cleanup
when it ends. Destroying the Activity stops both the registration and service.
The service is not an input backend and does not survive the test's owner.

## Boundaries

- Actual registration callback is required, not just registerApp's return value.
- Input and comparison confirmation require a resumed Activity. During setup,
  system pairing screens may cover the app while a native foreground service
  preserves the process's HID eligibility for a maximum five-minute test window.
  Ordinary app switching during this window also cannot enable input. The elapsed
  deadline is rechecked before callbacks/input use after sleep; no wake lock is held.
  After comparison confirmation, any Activity pause terminates the test. Stop,
  Activity destruction, service loss, registration loss, timeout and selected-link
  loss clear eligibility and request neutral release, disconnect and unregister.
  A selected connection attempt begins when the user confirms comparison. Any
  subsequent disconnect stops that session, even before its first success.
  Automatic connections before comparison are rejected without input; their
  deliberate disconnection leaves only the pending pairing observation intact.
- Only a new pairing observed during this session is eligible. This narrow
  probe accepts Numeric Comparison only, followed by an explicit human statement
  that the same digits appeared on BOTH the phone and intended projector.
  An old bond, a device name, or tapping a generic Pair button is insufficient.
  Passkey Entry may be legitimate but is deliberately outside this first probe.
- The app never reads pairing digits, confirms a system pairing prompt, removes
  bonds, or changes Bluetooth settings. Unknown connections receive no input.
- Fixed small arrow steps and one left-button press/release; no held drag,
  keyboard, automatic playback, continuous motion or remote command endpoint.
- Numeric Comparison evidence is not proof of negotiated encryption/key strength
  or every reconnect. This probe cannot award H1A PASS. A crash or radio loss
  may prevent a final release report; actual cleanup remains a hardware check.

## Build and local checks

With Android SDK 36 and the repository's pinned wrapper / Android plugin:

```sh
./gradlew -PhidProbe=true :experiments:hid_probe:testDebugUnitTest \
  :experiments:hid_probe:lintDebug :experiments:hid_probe:assembleDebug
```

The parent project's normal build excludes this module. Release is disabled.
The APK is generated under this directory's ignored `build/outputs/apk/debug/`.

## Manual run

1. Open Open TV HID Probe on the Nord, grant Nearby devices, then Start.
   Wait for the actual “HID registered” result.
2. Tap Make visible. Approve Android's 120-second discovery prompt. Use the XGIMI's
   physical remote to search for a Bluetooth accessory. A temporary native
   notification keeps registration eligible while Android shows pairing prompts.
3. Select the Nord on the XGIMI. Compare the numbers on BOTH screens before
   accepting either system pairing prompt. If there are no matching displayed
   numbers, cancel and record unsupported pairing; do not bypass the gate.
4. Return to the probe within the five-minute test window, explicitly confirm
   that comparison, then connect. After confirming, keep the probe in the foreground.
   An existing bond alone cannot enable controls. If repeating a stopped trial,
   manually forget only the test bond on both devices before a fresh Start.
5. On a safe launcher area, try one small arrow step. Then open Netflix with
   the physical remote and move onto a profile/title tile; tap Click once.
   Avoid Play. Observe the TV rather than treating send success as activation.
6. Tap STOP, check input is disabled and the physical remote still works.
   Backgrounding and link-loss checks are separate; record their actual results.

Record callback results, pairing method, observed activation and stop behavior
with source/APK hashes in H1A evidence. Never record addresses or pairing digits.
Uninstall this separate probe after testing; remove its test Bluetooth bond
manually if desired. No existing projector/phone app is replaced by the probe.
