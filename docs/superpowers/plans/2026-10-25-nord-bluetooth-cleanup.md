# Nord Bluetooth shutdown cleanup correction

The owner explicitly requested fixing the observed0.12 blocker. Scope is the
existing diagnostic cleanup barrier, not daily-use lifecycle or automatic
reconnect. Captured events and artifact identity are in `docs/gates/n3-report.md`.

## Behavior and proof

Profile loss before Stop remains fatal. Profile loss during Stop is pending
cleanup, with existing independent failed latches preserved. Detached/disabled
proxies cannot prove disconnection through empty/default query results. Release
requires the owner's actual selected-peer disconnect and unregister callbacks,
no observed foreign connection, successful proxy closure and the existing TV
worker/socket and service completion. New nonterminal callbacks, native pending-link observations and
new native connect attempts invalidate disconnect evidence. Missing callbacks
keep ownership unavailable. All events remain tied to the immutable Run owner;
late old callbacks cannot affect the next owner. Explicit Start/Connect remains
mandatory after completion. Pairings, permissions, TLS and five-minute limit
are unchanged.

The Android12 API explicitly states unregister disconnects the active link and
prevents new connections until register; its boolean is command acceptance,
not completion. `onAppStatusChanged(false)` is the registration acknowledgment.
The connected native unregister path waits for CLOSE/VC_UNPLUG before completion.
We still demand the actual selected-link disconnect callback, including for
outstanding attempts; query defaults and Bluetooth state alone are not proof.

Platform references (AOSP android-12.0.0_r1):
- [BluetoothHidDevice](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-12.0.0_r1/core/java/android/bluetooth/BluetoothHidDevice.java): unregisterApp, callbacks, query defaults.
- [HidDeviceService](https://android.googlesource.com/platform/packages/apps/Bluetooth/+/refs/tags/android-12.0.0_r1/src/com/android/bluetooth/hid/HidDeviceService.java): application/connection state dispatch and callback retirement.
- [Native state machine](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/bta/hd/bta_hd_main.cc) and [actions](https://android.googlesource.com/platform/system/bt/+/refs/tags/android-12.0.0_r1/bta/hd/bta_hd_act.cc): connected unregister close ordering.

## Execution

1. Reproduce the captured order using the Activity's actual extracted callback
   methods with native collaborators; expect recovery only after all confirmations.
2. Change only Activity cleanup evidence and profile-loss classification. Keep
   NativeRunGate's permanent failed latch unchanged.
3. Cover missing acknowledgments, detached-query defaults, both callback orders,
   invalidated disconnects, foreign links, independent failures, normal Stop,
   late callbacks, proxy-close failure and TV/service barriers.
4. Run focused regressions, all Nord unit tests, lint and debug assembly;
   independent review before a stopped-only same-signature0.13 install preserving
   both enrollment records. Hardware acceptance remains pending until observed.
5. Recheck actual Bluetooth-loss Stop and explicit reconnect without Force stop;
   if another native cleanup failure remains, retain the barrier and its evidence.

RED reproduced the existing captured-order assertion. Vendor behavior is verified
by the hardware follow-up, not inferred solely from AOSP or fake native tests.
