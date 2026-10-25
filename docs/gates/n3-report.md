# N3 unified Nord remote — installed, owner acceptance pending

Contract: [approved N3 design](../superpowers/specs/2026-10-25-nord-tv-controls-design.md).
Source branch `nord-tv-controls`, implementation `8763f5a` (transport `6dfd599`).
N3 owner acceptance is **NOT RUN**. Reviewed version0.9 is installed and confirmed
in the Nord foreground. Both enrollment stores are unchanged and no remote run
started automatically. The owner has been asked to perform TV pairing in-app.

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

## Owner acceptance — all NOT RUN for version0.9

- TV-code pairing in the masked field, then explicit combined Start/Connect.
- Saved reconnect and full app/process restart without re-pairing.
- Home/D-pad/OK, Back, volume-/+, play/pause on owner-selected content.
- Netflix launch, both channels still usable after20 seconds.
- Pointer movement, title/profile click and both scroll axes.
- Stop blocks both, physical remote still works; foreground loss/return stays stopped.
- Five-minute expiry; deliberate Wi-Fi loss and Bluetooth loss each stop both.
- Portrait/landscape, enlarged text and pairing keyboard remain usable.

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

The app negotiates mask579 (ping/key/volume/app-link), omitting H0's POWER status
bit. Both parse RemoteStart as readiness/power status. Available client/schema
sources do not prove whether that status bit controls delivery of RemoteStart;
only the owner TV test can establish this. A missing start times out and stops,
never bypasses readiness. No power input capability is exposed.
Native TLS1.3 negotiation and live child-certificate handshakes remain unproven;
TLS1.2 mutual authentication and native child-policy rejection are proven.

Final emulator output SHA256: `d2da8a45cf4aa424a7f34a6d5f3cf4aeeda07a1b652a979c0eacd58b8adfcf23`.
