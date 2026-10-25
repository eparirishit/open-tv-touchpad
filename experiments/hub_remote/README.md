# Unified remote H0 proof

Disposable **local Mac** test, not a PWA, packaged app or network service.
The projector runs Netflix normally. Video, audio and clipboard are not streamed.
**H0 PASS: scoped local Mac functional proof on the XK03H.**
The SDK pad had no visible cursor.
Direct SDK profile tap and vertical swipe passed separately. The new default is
UHID-only `HardwarePointer`. The user confirmed that its pointer appears and
moves, clicks open Netflix tiles, both wheel axes work, and drag/release works.
Missing-ADB recovery and combined TV/touchpad connection now passed separately.
Full Stop/reconnect of both channels without pairing and playback/pause/resume
passed by user report. UHID profile selection now also passed explicitly:
"Yes, it works". The earlier broad shortcut PASS was contradicted by a later
Netflix-button failure that disconnected both channels; that history remains in
the evidence report. The corrected fixed-link live retest passed: "Netflix opens
and both stay connected" after Home → Netflix, a 20-second wait and pointer movement.
Next is new hub trust/PWA design and its gate, not network implementation.
PWA, Windows and universal robustness are not certified by this proof.

## Prerequisites and launch

- Python 3.13 with Tk, local ADB and scrcpy 5.0.1 from the existing installation.
- An already authorized, awake XK03H on the same trusted LAN.
- Android TV Remote Service on the projector. No root or app changes required.

From the repository root:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r experiments/hub_remote/requirements.txt
.venv/bin/python experiments/hub_remote/app.py --serial YOUR_ADB_DEVICE --host YOUR_PRIVATE_PROJECTOR_IPV4
```

Use the exact authorized target shown by `adb devices -l`. Obtain the private
IPv4 address from projector network settings. For wireless ADB, configure the
exact authorized private-IPv4:port or .local-hostname:port endpoint as `--serial`;
`--host` remains the TV Remote private IPv4 address. Never paste credentials into
arguments. The proof uses the installed server at `/opt/homebrew/share/scrcpy/`;
its SHA-256 is fenced to the inspected upstream 5.0.1 binary. Other installations
and Windows need separate packaging/testing, not a guessed path substitution.

## Test in this order

1. Click **Pair TV**. Enter the six-character code shown by the projector in
   the masked field and click **Confirm code**. Do not send the code to chat.
2. Verify Home, D-pad/OK, Back and the Netflix shortcut. These use Remote v2,
   with no ADB key fallback. A shortcut's send acknowledgment is not activation.
   The corrected shortcut sends only `nflx://www.netflix.com/browse`. In the
   reopened GUI, verify that it opens Netflix and both channels stay connected;
   a successful URI resolver check or local TLS test is not this live acceptance.
3. Click **Connect touchpad**. If its configured ADB transport is missing, this
   explicit action makes at most one `adb connect` attempt to that exact endpoint,
   with an eight-second connect timeout, then verifies transport readiness and
   the XK03H model before opening the session. There is no background retry,
   discovery, alternate-target fallback or automatic ADB daemon restart.
   Moving the Mac pointer inside the dark pad sends
   relative UHID mouse movement; clicking sends mouse-button press/release. Hold and move
   to drag. Leaving the pad releases a drag. Check pointer visibility on the
   actual projection; screenshots may omit hardware cursor layers.
4. Use the four scroll buttons to test vertical and horizontal mouse-wheel
   scrolling, not SDK touch swipes. Pointer sensitivity affects motion, not
   wheel steps. This proof does not implement a two-finger recognizer.
5. **STOP / Disconnect all** ends both channels and discards pending commands.
   **Reconnect TV** reuses the paired identity for this process; connect the
   touchpad separately. Check the physical remote still works.
6. Close the window to release resources and delete temporary client credentials.
   A fresh process needs pairing again. No PIN or private key is persisted in Git.

## Troubleshooting and limits

- Connection failed: verify the projector is awake and on the same network.
  Do not enable public ADB, use `adb -a`, or forward router ports.
- Touchpad connection failed: both channels stop. Check the configured ADB
  address and that the projector is awake on the same Wi-Fi. An unresolved
  `.local` name requires correcting the configured endpoint to an explicitly
  known authorized private IP; the app does not discover or substitute one.
  Direct TCP reachability alone does not prove that the ADB daemon has a usable
  transport. Use **Reconnect TV** to restore TV controls in the same process,
  then explicitly **Connect touchpad** again. This is not a TV pairing failure.
- No pairing code: confirm Android TV Remote Service is present and reachable.
- Netflix button drops both channels: the old package-launch link produced six
  remote-service ActivityNotFoundException entries for
  `market://launch?id=com.netflix.mediaclient`. The fixed URI now sent resolves
  with DEFAULT to NetflixComLaunchActivity. The corrected snapshot's live retest
  passed with Netflix opening and both channels remaining connected.
  The status `TV remote connection lost. Both channels stopped.` records the
  stop-all response; do not treat it as evidence that re-pairing fixes app launch.
- Pairing succeeded but verified connect failed: the TV certificate may be
  unsupported/expired or different on its two ports. Fail closed; do not disable
  certificate verification. This proof has no fallback hostname verifier.
- Touchpad connected but no cursor: record that result separately from taps.
  The SDK pad had no visible cursor in the user's test. The user subsequently
  confirmed UHID visibility/movement, Netflix tile clicks, both wheel axes and
  horizontal drag/release. Full Stop/reconnect of both channels without pairing
  and playback/pause/resume subsequently passed by user report. The default
  uses UHID for movement, buttons and wheels only, never SDK coordinates inferred
  from an accelerated cursor. A drawing-only overlay is future work.
- Too much queued input: the proof stops instead of replaying stale actions.
- A drag expires after two seconds without input or fifteen seconds total.
- Legacy ADB on the trusted LAN is a diagnostic exception, not an encrypted
  product link. Browser trust and device transport security remain separate gates.
- Closing the socket attempts release, but injected input during abrupt power
  loss or process death still needs hardware acceptance; no universal guarantee.
- Abrupt hardware link/power loss and active-drag interruption were not tested.
  Passing local STOP/fence tests does not certify these production reliability cases.

## Verification

```sh
.venv/bin/python -B -W error::ResourceWarning -m unittest discover -s experiments/hub_remote -v
```

Tests use local socket pairs and TLS fixtures. They do not contact the projector.
Coverage includes UHID mouse framing and dispatcher lifecycle regressions.
Main freshly reran 55 local tests after the fixed-link change: PASS, with
ResourceWarning treated as errors. The fix is reviewed and live launch passed
on tv_remote.py snapshot 6be01a1d…; see the report for the full source hash and test sequence.
The recovery reviewer found no actionable findings. The user also confirmed
"Both stay connected and work" after Pair TV then Connect touchpad, testing Home
and pointer in the updated GUI. The subsequent response "works, and the play/pause
also works" confirmed the remaining Stop/reconnect and playback checks. UHID
profile selection subsequently passed with "Yes, it works"; SDK evidence remains separate.
Hardware results belong in `docs/gates/h0-report.md`; keep captures outside Git.

The control framing is based on scrcpy v5.0.1's `ControlMessageReader.java` and
`control_msg.c`; scrcpy and androidtvremote2 are Apache-2.0 projects. The mouse
descriptor is adapted from scrcpy v5.0.1 `app/src/hid/hid_mouse.c`, copyright
Genymobile / Romain Vimont, under [scrcpy-LICENSE](scrcpy-LICENSE). Only that
descriptor is vendored/adapted; no other upstream source or binaries are vendored.
Re-review private hooks when upgrading.
