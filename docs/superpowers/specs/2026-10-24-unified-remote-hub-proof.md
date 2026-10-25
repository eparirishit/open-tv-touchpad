# Unified remote: local hub proof

Status: user approved the proposal and first proof on 2026-10-24.

## Outcome

One remote combines ordinary Android TV navigation and actual pointer/touch
control for mobile Netflix on the XGIMI Horizon XK03H. The proposed product is
a phone/laptop PWA talking to an authenticated local hub, first on Mac, later
on Windows. The first proof is a disposable native Mac control window with
no network listener. It tests the two device transports before PWA work.

## Evidence and alternatives

M0 accessibility taps failed. M0.1 ended STOP after three Netflix and three
calibration cancellations. This establishes failure of our tested dispatch
path, not the precise Android/firmware root cause. Earlier scrcpy operation
worked but had video lag and overly fast scrolling. The PWA is not yet built.
Direct SDK profile tap and vertical swipe passed, but the user reported no
visible SDK cursor. The default is now UHID-only HardwarePointer. The user
confirmed its pointer movement, title clicks, both wheel axes, drag/release,
ordinary controls, Stop/reconnect and playback/pause/resume. UHID profile selection
subsequently passed explicitly. The Netflix shortcut portion of the earlier controls
PASS is contradicted by a later logged ActivityNotFoundException failure. The fixed
Remote v2 URI is now `nflx://www.netflix.com/browse`; resolver/local TLS evidence
does not establish live launch. A separate live retest now passed: "Netflix opens
and both stay connected" after Home → Netflix, a 20-second wait and pointer movement
on the corrected snapshot. H0 PASS is scoped to the local Mac functional proof.
Abrupt hardware link/power loss and active-drag interruption remain explicit
production reliability limitations, not hardware-tested PASS results.

Chosen: local hub with Android TV Remote v2 for a closed set of keys and a
fixed Netflix shortcut, and scrcpy for input. Tradeoff: an awake hub and
ADB authorization are required. A native phone client might remove the hub
but would require separate platform work. A projector-hosted privileged
helper has unproven startup/reboot behavior. Neither is in this proof.

## H0 contract

- Target only the explicitly selected, already-authorized XK03H. Do not scan
  the LAN, use root, modify firmware/apps, or change debugging configuration.
- Use installed scrcpy 5.0.1 with its exactly matching server. No custom
  cryptography and no accessibility gesture fallback.
- Use a control-only UHID mouse by default; prove visible cursor, selection
  and two-axis wheel scrolling. SDK tap/swipe evidence does not certify UHID.
- Native local UI exposes D-pad, OK, Home, Back, volume, play/pause, Netflix,
  touchpad, sensitivity, explicit Connect and Disconnect. No shell entry,
  arbitrary package launch, clipboard, text entry, power, or video stream.
- Ordinary TV commands use androidtvremote2 0.3.2. Pair using the on-TV code;
  never log codes, peer identifiers, coordinates, credentials or protocol data.
  Keep keys in owner-only temporary storage for the proof. After successful
  code pairing, bind subsequent control to the paired server certificate;
  fail closed on mismatch. Upstream's unverified default is not sufficient
  for normal control. Explicit pairing bootstrap is the only enrollment
  exception; no unauthenticated control is permitted.
- Default relative pointer deltas become bounded UHID mouse reports, with
  buttons and vertical/horizontal wheels. Android owns the accelerated cursor;
  never infer SDK coordinates from it or mix UHID movement with SDK touches.
  Bounded SDK display coordinates remain only in the separate diagnostic backend.
  Use a persistent control channel, a bounded worker queue, no stale replay,
  finite I/O deadlines, and release/cancel on stop or failure. A native cursor
  is preferred; a drawing-only overlay must not steal remote focus.
- ADB port 5037 and scrcpy forwarded sockets remain loopback-only. Never
  enable ADB listening remotely, port forwarding on routers, or a cloud relay.
  Existing legacy wireless ADB is a trusted-LAN diagnostic limitation, not
  an encrypted product transport claim. Product transport needs a new gate.
- The physical remote must remain usable. On failure stop the proof and
  surface which channel failed; do not substitute ADB keys for Remote v2.
- No claim of app activation follows merely from successful packet sending.
  User observation or a local diagnostic screenshot establishes activation.

## Acceptance evidence

Record independent results for Remote v2 pairing, D-pad/OK, Home/Back, volume,
Netflix shortcut, visible pointer, profile activation, vertical/horizontal
scroll, drag/release, disconnect, reconnect, physical remote coexistence and
normal playback. NOT RUN stays NOT RUN. Failures do not become PASS by changing
thresholds. Do not purchase/play new content or switch profiles blindly.

H0 PASS authorizes proposing the product trust/PWA design and new acceptance gate,
not network implementation or claiming a
completed app. Phone browsers, trusted HTTPS onboarding, full reconnect and
reboot behavior, Windows packaging, XGIMI-specific controls and other device
certification remain later work. Source, tests and a runbook remain in
`experiments/`, excluded from Android releases.
