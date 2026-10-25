# Nord direct remote: staged design

Status: N1 APPROVED on 2026-10-25. After reviewing the proposed first increment,
the owner instructed “start with the implementation”. The owner selected using
the Nord itself as the remote, rather than as an iPhone/laptop bridge. N2–N4
remain separately scoped follow-ups; no historical gate is relabeled PASS.

N1 execution outcome (2026-10-25): source `fdb4905`,0.5-diagnostic completed
automated checks, independent review and the planned owner-operated gesture/
lifecycle sequence. [N1 diagnostic behavior acceptance passes](../../gates/n1-report.md)
for this artifact. The five-minute foreground-only architecture remains; this
does not authorize N2–N4 implementation or claim broader product certification.

## Outcome and decisions

The OnePlus Nord AC2001 on Android12 is the handheld controller for the XGIMI
Horizon XK03H. The eventual single screen contains TV keys and a Netflix mouse
touchpad. No Mac runtime, browser, fixed IP, installed root, VPN, cloud account,
hotspot or router change is required for the first Bluetooth-only increment.
TV Remote v2 will still need phone-to-projector LAN reachability when introduced.
Discovery cannot bypass network isolation or authenticate a peer by its name.

Approaches considered:

- Nord direct remote: selected by the owner. Reuse the tested native Bluetooth
  HID path; introduce Android TV Remote v2 separately after Android feasibility.
- Nord bridge for iPhone/laptop: deferred; it introduces another authenticated
  network hop and companion lifecycle which the current probe does not implement.
- Mac PWA hub: deferred. Its address-bound browser trust setup cannot currently
  be completed on the building-managed network. Preserve its code and evidence.

The current probe is already a native Android app. Broader native clients and
distribution are future work, not prerequisites for using this phone directly.
Bluetooth-only HID keyboard/consumer keys are not silently substituted for Remote
v2: their mappings and Netflix launch behavior have not been established.

## Delivery order and gate boundary

1. **N1: real touchpad in the existing isolated probe.** Add finger-controlled
   relative movement and tap-to-click to its already tested mouse report. Keep
   its five-minute foreground-only run and existing enrollment/lifecycle rules.
   This is the first buildable increment defined completely below.
2. **N2: scrolling compatibility.** Separately specify and test vertical and
   horizontal HID scroll reports on this exact projector/mobile Netflix version.
   The current three-byte report has no wheel fields. Changing the descriptor
   can require forgetting/re-pairing; disclose that before installation. Do not
   promise scroll compatibility from the old Mac UHID result.
3. **N3: TV keys in the same Android interface.** Select/review a maintained
   Android-compatible Remote v2 implementation or a narrowly scoped port of the
   reviewed protocol behavior. The existing adapter is Python, not reusable
   Android source. Establish code-bound enrollment, retained peer verification,
   credential storage, discovery/address-change handling and bounded lifecycle
   before adding INTERNET permission. Required controls: Home, Back, D-pad/OK,
   volume, play/pause and the verified Netflix launch URI. Discovery is only a
   candidate locator; changed certificates require explicit re-enrollment.
4. **N4: daily-use lifecycle and packaging.** Review the five-minute diagnostic
   limit, held-input cleanup if drag is added, reconnect reliability and supported
   Android foreground-service behavior. Validate the combined device paths, then
   define a separately approved product/release contract.

Approval of this document authorizes N1 only and records N2–N4 as sequenced work
requiring their concrete designs. It does not claim that an N1 touchpad completes
the unified remote. Keep the physical TV remote for navigation until N3 passes.

N1 extends the accepted isolated HID diagnostic, not the hub product. H1A remains
NOT PASSED; accepted OS-bond custody assumptions remain unchanged. H1B applies to
the deferred browser architecture and is not relabeled PASS or used to block this
no-network diagnostic extension. No hub device backend is authorized. Existing
M0/M0.1/H0 reports and the unresolved first-attempt reconnect failure remain intact.

## N1 ownership and reuse

Retain `experiments/hid_probe`, application ID, enrollment format, three-byte
mouse descriptor and backup exclusions. Increment the diagnostic version. Do not
clone Bluetooth connection, trust, storage or teardown code into a second app.
An in-place update should retain app enrollment and OS bond; never automatically
clear either if update/reconnect fails. Preserve the previous source/APK evidence.

- `ProbeActivity` remains the composition root and sole native input submitter.
  Its current `move` and `click` admission checks remain authoritative for every
  report. Neutral-before-input, native generation ownership and permission checks
  are unchanged. No successful write is called confirmed delivery. Move the
  existing input exception-to-Stop protection into the shared Activity-owned
  submission boundary so buttons, touch events and scheduled movement all use it.
  A native SecurityException, IllegalStateException or IllegalArgumentException
  must cancel gestures and Stop rather than escape the callback; preserve click's
  release-in-finally. Do not rely on the old button-listener wrapper for new callers.
- A small platform-independent touch-state helper owns active-pointer selection,
  threshold/time classification and relative motion arithmetic. It cannot access
  Bluetooth, enrollment or connection state. Use the existing JUnit dependency.
- A native touch surface translates MotionEvents into the helper and forwards
  eligible actions through the Activity. It owns at most one scheduled movement
  callback and clears it on disabling/detaching. No external UI dependency.
- `ProbeSession`, `NativeRunGate`, enrollment storage and `PairingGuardService`
  retain their contracts. Do not relax a gate to accommodate the new surface.

The gesture helper is necessary for deterministic cancellation/quantization tests,
not an interface for imagined input backends. Use the installed Android platform
and Gradle toolchain; no network or runtime dependency additions.

## N1 input contract

One finger moves the projector pointer relatively. A short stationary tap sends
the existing immediate left-button press/release. There is no held drag,
double-click recognizer, long-press action, two-finger scroll or kinetic motion.
Retain labeled directional buttons and Click as accessible alternatives.

Builder choices for this first measured build:

- Read local coordinates in density-independent pixels. Start with fixed gain
  1.0 HID count per dp; preserve fractional remainder within the current gesture.
  Record live usability before adding a sensitivity setting.
- A tap lasts at most250ms and never travels farther than the platform's touch
  slop from its origin. Once slop is exceeded, the gesture is movement-only;
  returning to its origin never makes it a tap. No movement is sent before slop.
  The first movement includes the accumulated displacement since down.
- Track the original pointer ID. A second finger, missing pointer, cancellation,
  disable, focus loss or lifecycle Stop cancels the gesture without a click.
  After a multitouch cancellation, require all fingers up and a fresh down.
  Ignore historical samples; never interpret another finger as the original one.
- Coalesce eligible deltas in one bounded accumulator, at most254 counts per
  axis, dispatch no faster than30Hz, and clamp each report to [-127,127]. Discard
  overflow instead of accumulating catch-up movement. A scheduled callback is
  tied to the current gesture/run; old callbacks cannot act in a new run.
- On ordinary movement up, discard unsent motion and cancel the callback rather
  than moving after the finger lifts. On tap up, submit only the immediate click.
  Reset pointer, displacement, fractions and pending motion on every terminal
  event. Movement never holds a button; leave existing click release-in-finally.
- A rejected native report follows the existing Stop path. No automatic retry,
  replay, reconnect, alternate peer or bypass of foreground eligibility.

Stop and Close stay reachable outside any scrolling setup panel. The touchpad
has a labeled fixed visible area and does not let a parent ScrollView intercept
an active gesture. Native buttons retain keyboard/accessibility semantics and
at least48dp targets. A screen reader can use directional buttons and Click;
do not label a gesture surface accessible without equivalent controls.

All Stop paths, including service timeout and radio/connection loss, clear local
gesture state immediately as well as fencing native sends. Finger-down while
disabled cannot become authorized merely because connection later succeeds;
require a new down after controls become enabled. Activity pause and return still
stop the current run and require explicit Start/Connect, as before.

## Proof and acceptance

Before implementation, add focused failing unit tests for tap versus movement,
slop crossing and return, fractional accumulation, signed bounds, rate/coalescing,
multitouch, stale callbacks, disable/re-enable and terminal cancellation. Test
actual helper behavior, not duplicated arithmetic. Retain the existing session,
enrollment, native-ownership and trace regression tests. Run the probe's unit
tests for throwing submissions as well: failure must cancel pending input and
request Stop from both direct-touch and scheduled dispatch paths. Run existing
tests, lint and debug build; inspect packaged permissions, release exclusion and
backup rules. Review the complete change independently before pushing/installing.

On the reviewed APK, explicitly connect to the saved projector. Verify movement
in all directions, lift/reposition without pointer jumps, a stationary tap on a
Netflix profile/title, no click after a swipe, and no action from a second finger.
Then test Stop, app background/return and the five-minute expiry: no pending
movement or click resumes; explicit new connection is required; physical TV
remote remains usable. Record exact source/APK and observed results. Do not
repeat already established pairing tests unless the change or failure requires it.

The original fixed64-event diagnostic trace may truncate quickly with continuous
motion. Do not enlarge or continuously export it to claim gesture evidence;
use deterministic tests and manual observations. No identifiers, touch coordinates,
pairing values or report contents enter committed logs or review artifacts.

N1 is complete only when focused automated checks, independent review and the
specified actual-device acceptance pass. Unknown/crash-time click release and
exact firmware encryption attestation remain limitations, not inferred successes.

## Deferred scope

PWA/HTTPS enrollment, Mac-to-Nord bridge, iPhone/laptop/Windows clients, background
control, automatic reconnect, discovery UI, held drag, scrolling, TV-key protocol,
unlimited sessions, telemetry and public distribution are absent from N1. Later
increments revisit only the items needed for the agreed Nord unified remote.

## Independent design review (2026-10-25)

A read-only builder/security review identified that the current exception guard
lives in button listeners, outside `move`/`click`. The ownership and acceptance
sections now require shared exception-to-Stop protection for touch and scheduled
dispatch as well as buttons, including a throwing-submission regression. No other
material N1 planning gaps were reported. This is design review, not implementation
or hardware verification.
