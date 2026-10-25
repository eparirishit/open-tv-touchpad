# N2 — Nord scroll-button compatibility

Status: APPROVED on 2026-10-25. After reviewing the scope and one-time re-pairing
requirement, the owner instructed “looks good, lets start”. This authorizes N2
implementation and its controlled migration; physical bond removal and compared
pairing remain owner-operated steps after the reviewed build is ready.

## Outcome and approach

Add Scroll up/down/left/right to the existing Nord interface, using Bluetooth
mouse wheel and horizontal pan reports against the XGIMI mobile Netflix app.
Reuse the N1 touchpad, session admission, native ownership and Stop behavior.
No Mac, LAN control, new permission, dependency, cloud account or firmware change.

Approaches considered:

- **Four explicit buttons (recommended builder choice):** smallest complete
  two-axis scroll increment, with one observable notch per activation and clear
  direction checks. N1's finger gestures stay unchanged.
- Two-finger scrolling now: adds gesture arbitration and tuning before the
  underlying Bluetooth axes are verified. Defer to a later interaction increment.
- Held-button drag fallback: changes the failure/cleanup model and hides whether
  wheel/pan works. Not authorized; a failed axis stays a recorded failure.

The owner accepted the proposed initial button approach. N2 is complete only if the exact Nord/XGIMI/Netflix setup passes
both axes and the defined regression checks. One working axis is partial, not PASS.

## Report format and ownership

Extend the current three-button relative mouse descriptor to report ID1 with
five payload bytes: buttons, signed X, signed Y, signed vertical wheel, signed
horizontal pan. Keep three button bits and five padding bits; append Generic
Desktop Wheel (usage0x38) to X/Y and Consumer AC Pan (usage0x0238) as the final
relative signed8 field. Logical range is[-127,127]. Do not copy the UHID wire
framing or a second report-ID byte into Android's payload.

The existing H0 `HardwarePointer` uses the same five payload fields; its descriptor
is adapted from scrcpy5.0.1 with recorded attribution. Reuse that reference and
retain attribution/license in the Android helper if adapting its bytes. This is
format/function evidence from another transport, not a Bluetooth compatibility
or security result for the Nord.

A small package-private `MouseReport` owns descriptor, report ID, payload size,
neutral, movement, click-down and scroll encoding. This consolidates every
existing hardcoded three-byte construction rather than creating an optional
second format. Return fresh arrays; do not expose mutable shared descriptor or
payload state. Keep crypto, trust and native operations outside this helper.

All Activity consumers must change together: registration, connection-time
neutral, GET_REPORT reply/size validation, movement, click press/release and Stop
neutral. Input payload length must be5 everywhere. GET_REPORT permits only input
type/report ID1 with bufferSize0 or at least5; reject negative/short/other requests
through the existing protocol-error path. Keep existing active-peer eligibility
before replying, and existing Stop on unsupported boot/unknown protocol mode.
Do not introduce a three-byte fallback. Preserve exception-to-Stop and teardown
failure latching, including the N1 IllegalArgumentException correction.

## Button behavior

Closed directions map to a single signed unit: Up wheel+1, Down wheel-1,
Left pan-1, Right pan+1. Other axes and buttons are zero. These match the actual
report signs in the successful H0 button implementation; verify visual direction
on this Bluetooth path instead of assuming identical app handling.

One click sends at most one report, with no repeat/hold gesture, timer or queue.
Allow at most one scroll submission per100ms across all four buttons in a run;
drop quicker activations, never replay them. Admission remains the current
foreground, selected/confirmed peer, live registered run and neutral-first gate.
Cancel the existing pad gesture and pending movement on a scroll button's
ACTION_DOWN, before waiting for its click/UP. The touch listener must return false
so native button click/accessibility semantics remain intact. Also cancel in the
admitted click path before sending, including rate-limited activations and
keyboard/accessibility clicks without ACTION_DOWN. Otherwise a pad tap or queued
frame can fire while the second finger holds the scroll button. Retain
one-finger movement/tap and second-finger cancellation on the pad itself.

Buttons are labeled native controls with at least48dp targets. Place them beside
the fixed pad area, outside the setup ScrollView; Stop and Close remain visible.
Allow two rows on narrow screens. Never make room by hiding Stop or disabling
the existing directional/Click accessibility alternatives. Check actual Nord
layout, including landscape, before asking for the scroll trial.

Use the existing guarded Activity input boundary for native failure handling;
failed/throwing scroll submissions Stop without retry. No new trace export,
coordinate logging or report-body logging. Native submission success remains
different from Netflix visibly responding.

## One-time descriptor migration

The old Bluetooth HID descriptor may be cached by the host. Do not rely on an
in-place application update to refresh it. Require fresh compared pairing once
for0.6; subsequent explicit saved-projector reconnects use the existing N1 flow.

Version0.6 keeps the package but uses enrollment record version2 (same19-byte
shape, only the version byte changes). Version1 records never authorize a
connection under the new descriptor. Recognize a structurally valid old record
only to display a migration explanation, not to adopt its peer. Leave its bytes
untouched until explicit Forget or a freshly verified enrollment replaces it.
Missing/corrupt/unsupported records remain unselected; I/O failures still latch.
Successful save/Forget still requires atomic write and readback verification.

On an old record, clearly show: “Scrolling update needs fresh Bluetooth pairing.
Remove only the Nord–XGIMI bond on both devices, then pair again and compare the
numbers on both screens.” Connect saved projector and all input stay disabled;
Start may register for the existing first-enrollment ceremony. Do not use hidden
unpair APIs, silently delete OS bonds or adopt a pre-existing unrecorded bond.
The migration explanation must remain available during first-enrollment setup,
not disappear merely because registration replaces a status message.

Before changing the device, preserve the verified0.5 APK in ignored local storage
and verify its N1 hash. Build/review0.6 first. Then ask the owner to Stop, manually
remove only the test bond on BOTH devices, install the reviewed update and carry
out the existing fresh Numeric Comparison ceremony. No pairing values go into
chat/logs. The app's saved record cannot prove that the projector cleared its
cache; owner confirmation plus successful fresh pairing/behavior is required.

Rollback is deliberate: Stop, reinstall the preserved0.5 artifact only through
a supported same-signature downgrade path, then re-enroll using fresh comparison
and the old descriptor. Version0.5 must reject version2 records as it already
rejects unsupported versions. Never uninstall/clear data automatically to force
a downgrade. Retain artifacts and explain if rollback requires owner action.

## Scope and trust

N1's report format and enrollment-version clauses are superseded only for this
approved N2 diagnostic once accepted. Its five-minute deadline, foreground-only
input, explicit Start/Connect, OS-managed bond trust limitation, unexpected-peer
rejection, backup exclusion and native generation/teardown rules remain.
No new authentication guarantee or runtime ADB path is introduced. H1A/H1B and
historical gates remain unchanged. N3 TV buttons, held drag, two-finger scrolling,
daily-use sessions and public release remain outside N2.

## Implementation and acceptance

1. Test the report helper before integration: descriptor input field widths and
   usage pages agree with the payload; full neutral/move/click/axis fixtures,
   signed bounds and report size agree. All fields return to zero on release.
2. Test migration: version1 cannot select/connect or send neutral/input; version2
   round-trips; old bytes are not rewritten during load; corrupt/I/O/failed-Forget
   cases remain fail-closed. Test both new enrollment and explicit reconnect.
3. Test button rate/drop/cancel and invalid direction/eligibility paths, including
   pad down/move → scroll-button down → queued pad frame/up → button up: the old
   pad action must not fire before the scroll click. Cover keyboard/accessibility
   activation, canceled button touch and throttled click without stale motion.
   Update
   GET_REPORT request-size tests and the source-linked native Stop regression to
   compile the actual report helper and assert the new neutral payload size.
   Review every native send/reply caller for stale three-byte payloads.
4. Run all probe unit tests, lint, debug assembly and package/backup/release checks.
   Independently review the full change and fix actionable in-scope defects before
   install. Record source/APK hashes, actual warning changes and review disposition.
5. After the controlled fresh pairing, first verify N1 movement and Netflix tap
   still work. Put the TV pointer over the relevant scrollable region; verify
   vertical up/down and horizontal left/right using discrete button presses.
   Test in a region with room to move both ways, away from edges. A no-op at an
   edge is inconclusive; allow one reposition check, not repeated blind trials.
6. Confirm no pointer jump/click from a scroll press, no trailing input after
   Stop/background/expiry, physical remote usability, and explicit saved-projector
   reconnect under the new descriptor. Recheck unchanged N1 gesture cancellation
   only if migration or integration produces evidence of regression.

If either axis is unhandled or boot mode is required, stop and record the exact
result. No implicit drag/touchscreen/ADB fallback. Return to design review before
changing input semantics. Passing code tests is not a scrolling hardware PASS.

## References and qualification

- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice#sendReport(android.bluetooth.BluetoothDevice,%20int,%20byte[])):
  sendReport takes report ID separately; payload does not include it. Retrieved
  2026-10-25. The API return is not observed Netflix behavior.
- Repository `experiments/hub_remote/pointer.py` HardwarePointer and `app.py`
  scroll button mappings, with `scrcpy-LICENSE`: five-byte mouse format and signs
  used in H0. This local evidence is not a guarantee for the Bluetooth transport.

No device state, enrollment record, descriptor or product code changed while
preparing this design. Source N1 remains `fdb4905`, artifact hash in its gate report.

## Independent design review (2026-10-25)

The reviewer identified a cross-control ordering gap: canceling only when the
scroll button's click fires leaves time for an old pad tap/frame while the button
is held. The design now cancels at button ACTION_DOWN as well as the admitted
click path and requires event-order regressions. No other material gaps were
reported in report callsites, enrollment migration/rollback or existing gates.
This is a design review, not Bluetooth compatibility evidence.

The unchanged0.5 APK was preserved and hash-verified at ignored local path
`.local/artifacts/nord-hid-probe-0.5-fdb4905.apk`; it is not committed or published.
