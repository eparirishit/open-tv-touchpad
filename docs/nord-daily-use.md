# Open TV on the Nord — private daily-use candidate

Version0.21 is a private candidate for the tested OnePlus Nord/Android12 and
XGIMI Horizon XK03H. [Readiness](gates/n4-readiness.md) remains NOT READY until
its hardware/pilot checks pass. Public distribution and other phone models are
separate work. Normal operation uses Bluetooth HID and pinned-TLS TV controls;
it needs no Mac server, router reservation, CA installation or runtime ADB.

## Connect and stop

Keep both existing pairings. Open Open TV on the Nord and tap **Connect** on
Remote or Touchpad. Both transports must become ready before their controls
work. Use the pad for Netflix pointer/tap and four scroll buttons; sensitivity
remains below scrolling. Use Remote for TV navigation, Home, Back and playback.

Input works only while a resumed/focused Remote or Touchpad is visible and the
phone is interactive and unlocked. Opening Setup pauses input. Short app switches
or locking retain healthy established links for up to30minutes; returning before
expiry grants a fresh input epoch. Lift your finger and begin a new gesture.
There is no five-minute cap during visible control. If a frame was still in flight
when input revoked, the app may stop both channels and require another Connect.

**Stop**, notification Stop, idle expiry or either link's loss ends the session.
Restoring Wi-Fi/Bluetooth does not connect again automatically. Wait for actual
teardown completion, then tap Connect. Swiping the task away stops it too. The
notification offers only Open and Stop; it never sends projector input.

No wake lock, screen-on rule or battery exemption is added. Android/OEM/network
suspension may lose a connection; the app must stop input and require Connect.

## First setup or deliberate re-enrollment

Existing0.20 users should not unpair, uninstall or clear storage for this update.
For a fresh Bluetooth enrollment, use Setup → Projector → Start setup, wait for
registration, make the Nord visible and pair from the intended TV. Compare the
numbers on both intended screens, accept only matching numbers, then use
**Codes match on both screens. Save and connect**. Keep pairing numbers out of
issues/logs. An existing OS bond by itself does not authorize a new app record.

For TV controls, stop the session, open Setup → TV controls, enter the projector's
own private Wi-Fi IPv4 address and save it. Start setup, then Pair TV. Enter the
six-character code displayed on the intended projector in the six visible cells,
then Confirm. Pairing ends stopped; return to Remote and tap Connect. Setup is
attended/bounded separately from ordinary control. Code text is excluded from
saved state/autofill/learning/logs and clears on Stop or UI loss.

## Diagnose safely

- Mouse connected / TV not paired: pointer-only enrollment exists. Stop, then
  configure/pair TV controls in Setup. The TV address stays locked during a run.
- PAIRING_CONNECT or CONTROL_CONNECT: confirm the projector's own address and
  same reachable Wi-Fi; guest/client isolation can block devices even on one
  subnet. Don't use the phone's debug address or the router/gateway address.
- Lost connection/idle expiry: keep pairings, wait for teardown, then explicitly
  Connect. Don't accept a changed certificate or reset trust to fix reachability.
- Native teardown unconfirmed: input stays disabled. Follow the app's explicit
  Android Force stop/reopen recovery instruction, preserving storage/pairings.
  Report the build and fixed status label. Timeouts do not prove cleanup.
- Forget deliberately revokes app enrollment; OS unpairing and re-enrollment are
  separate attended actions. Don't use Forget, app-data clearing or uninstall as
  routine connection troubleshooting.

Updates require the same application ID and signer and preserve both records and
Keystore identity. Keep the known-good APK locally; no automatic downgrade or
storage rollback is promised. Debugging is for development/installation only.
