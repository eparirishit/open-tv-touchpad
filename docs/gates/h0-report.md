# H0 unified local remote — functional proof evidence

Date: 2026-10-24 (America/New_York)
Decision: PASS — scoped local Mac functional proof on the XK03H; not a product, Windows or universal reliability PASS
Branch: hub-remote-proof
Historical base: 307a0ab (M0.1 STOP evidence retained)
Projector: XGIMI Horizon XK03H, Android TV 11, 1920x1080
Firmware previously recorded: RT.3.24.327 release-keys
Target: existing com.netflix.mediaclient mobile installation
scrcpy: 5.0.1
Server SHA-256: 764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536
androidtvremote2: 0.3.2

Earlier hardware-evidence source snapshot (SHA-256, paths under `experiments/hub_remote/`):

```text
pointer.py       21a73b38f314028f81a5dbb42269f92e6955d3562cdcc1698242e60ff067e624
tv_remote.py     fecaa5c17a7f410ea2c6668f91ae86a2be190bc99331e1e3e70ca7946e8eca98
app.py           bcca789f2547a481ca8dc34c5730b066d1b7f1844fab5bbd5b6996a0d7b78531
requirements.txt 6fcfee56ad42b4a0cb23e8d96464ec0d203b6471ee5b64e80c23a63cad8e4798
```

The observed UHID cursor test preceded the final lifecycle fixes; the reopened
window used this earlier snapshot. The user confirmed a Netflix title tile opens with
the mouse click in this version, then confirmed both vertical and horizontal
scroll buttons move Netflix browsing content.

| Check | Observation |
| --- | --- |
| Authorized ADB | Model/display queried successfully; transient offline then recovered. |
| Android TV Remote Service | Installed version 7.00.999235495. |
| Stock no-video UHID connection | Eight-second run exited 0; cursor/click not certified by that run. |
| Custom no-video SDK connection | Connected, sent hover, cleaned up its session. |
| SDK Netflix profile tap | PASS for direct proof-client test: visually inspected before/after captures; profile selection transitioned to Netflix home. Not a UHID click result. |
| SDK Netflix vertical swipe | PASS for direct proof-client test: home banner moved and next row became visible. Not a UHID wheel result. |
| SDK cursor visibility | FAIL: user reported NO visible cursor in the SDK pad. Tap/swipe results above remain separate PASS observations. |
| Remote v2 pair/connect | User confirmed `TV remote is connected` in the unified window. |
| Home/D-pad/OK/Back/Netflix shortcut/volume — earlier observation | Historical PASS by user observation: "All listed controls work" for Home, D-pad/OK on the launcher, Netflix shortcut, Back after pointer selection, Vol − and Vol + in the Mac window. The Netflix shortcut part is contradicted by the later specific failure below; the original observation is retained. |
| Netflix shortcut — prior failure | FAIL on the prior package-launch link: user reported Netflix button drops both channels with "TV remote connection lost. Both channels stopped." Remote-service logs contain six ActivityNotFoundException entries naming market://launch?id=com.netflix.mediaclient. Retained as historical failure evidence. |
| Netflix shortcut — corrected fixed link | PASS by user observation on tv_remote.py snapshot 6be01a1d…: "Netflix opens and both stay connected" after Home → Netflix, a 20-second wait and pointer movement. This verifies the live Remote v2 path using nflx://www.netflix.com/browse. |
| Default UHID HardwarePointer | Implemented: UHID-only relative movement, mouse buttons and vertical/horizontal wheels; never mixes accelerated cursor movement with SDK coordinates. |
| UHID cursor visibility / movement | PASS by user observation in the new UHID-only window: "Pointer appears and moves". |
| UHID Netflix tile click | PASS by user observation in the reopened window: "Yes, the tile opens" when asked whether clicking a title opens its details page. Playback was not part of this test. |
| UHID Netflix profile activation | PASS by user observation: "Yes, it works" in response to the explicit UHID profile-selection check. This is separate from earlier SDK profile activation and UHID title-tile evidence. |
| UHID wheel scrolling | PASS by user observation using Scroll ↓ and Scroll → over Netflix browsing content: "Both vertical and horizontal work". |
| UHID drag / release | PASS by user observation: "Drag and release work". A Netflix title row moved during a held-button horizontal drag and stopped dragging after release. This does not establish interruption by Stop or link loss. SDK horizontal swipe remains NOT RUN. |
| Touchpad Stop/reconnect/physical remote | PASS by user observation: "All three pass". After STOP, pad motion no longer controlled the projector; physical remote worked; Connect touchpad restored pointer motion. TV-channel Stop/reconnect and stopping an active drag were not established by this check. |
| Lifecycle review | All listed findings fixed and regression-tested: setup-time stale input, all-channel failure stop/epoch, pending TV-child cancellation, idle channel loss, pointer replacement cancellation and synchronous pointer send fence. Main re-reviewed app fixes; independent pointer re-review found no remaining actionable findings. |
| Stop timing boundary | Idle channel loss is checked every 100 ms. Stop synchronously fences further pointer input; cleanup release/destroy remains permitted. Already in-flight sends cannot be retracted. Ordinary touchpad Stop passed; active-drag interruption and abrupt link-loss hardware checks remain pending. |
| Playback | PASS by user observation: "works, and the play/pause also works" in response to the pending reconnect and playback checks. Playback, pause and resume were user-operated; no playback selected during automated checks. |
| Native UI | Render/layout and Stop smoke passed without device connection. |
| Local tests | PASS: 55 tests, freshly rerun by main with ResourceWarning treated as errors after the fixed Netflix-link change. Includes the exact Remote v2 link in the local TLS test, UHID framing, lifecycle, STOP fence, wrong-peer rejection and bounded ADB recovery. Fix implemented and reviewed; live shortcut success is not established by these tests. |
| Browser/PWA/Windows | NOT IMPLEMENTED / NOT TESTED. |

Verification command: `.venv/bin/python -B -W error::ResourceWarning -m unittest discover -s experiments/hub_remote -v`.

## Missing ADB transport and combined connection recovery

Recovery source snapshot (SHA-256, paths under `experiments/hub_remote/`):

```text
pointer.py fc6d8be4415cf0378b72b1442bfb49ada19b8edd827a54e3867cd8fc305bb469
app.py     6819664fd4b7ff847b33f8f4f0a36a29f3a6deb82fd89b20b7fc4105af6440f2
```

The user reported that Connect touchpad disconnected TV controls and displayed
`Pointer failed (ConnectionError)` with misleading pairing advice. Main found
the configured ADB target absent and its old `.local` hostname unresolvable.
The daemon also reported no route to the known private-IP endpoint despite
successful direct TCP reachability checks on ports 5555 and 6466. These checks
did not establish a usable ADB transport. Main manually restarted the daemon
only after confirming it had no devices or forwards; ADB then connected and
reported model XK03H. This was a one-off manual recovery, not app behavior.

Explicit **Connect touchpad** now checks the configured transport and, if missing,
makes at most one eight-second `adb connect` attempt to the exact configured
private-IPv4:port or `.local`-hostname:port endpoint. It rechecks readiness and
validates the model and pinned server hash. There is no automatic daemon restart,
discovery, alternate-target fallback or background retry. Setup failure still
stops both channels; the sanitized message directs the user to check the address,
awake projector and same Wi-Fi, then use Reconnect TV, without claiming pairing
will fix ADB.

- **Missing-transport recovery: PASS.** Main disconnected only the selected
  projector ADB transport. Updated `pointer.start` reconnected the known private
  IP, validated model/hash, opened a control-only session, remained connected
  after 0.5 seconds and closed its owned session. No clicks or keys were sent.
- **Original combined-connect regression: PASS.** The GUI was reopened on the
  verified IP using this recovery snapshot. After Pair TV then Connect touchpad,
  the user confirmed "Both stay connected and work", testing Home and pointer.
- **Full Stop/reconnect of both channels without pairing: PASS by user report.**
  After these remaining checks were called out, the user replied "works, and the
  play/pause also works". This is user-reported evidence, not an automated trace.
- **Playback/pause/resume: PASS by user report**, from the same response.
- **UHID profile activation: PASS by user observation**, subsequently confirmed
  with "Yes, it works". The subsequent Netflix shortcut failure and successful
  corrected-link retest are recorded below.
  Earlier hardware observations remain historical evidence and are not overwritten.

## Netflix shortcut failure and successful fixed-link retest

After confirming UHID profile selection, the user reported that the Netflix
button drops both channels with `TV remote connection lost. Both channels stopped.`
Remote-service logs contain six `ActivityNotFoundException` entries explicitly
naming `market://launch?id=com.netflix.mediaclient`. This specific failure
contradicts the Netflix shortcut portion of the earlier broad controls PASS.
The stop-all response is preserved; it is not evidence of a new pairing failure.

Resolver diagnostics found `UIWebViewActivity` for the standard launcher and no
activity for LEANBACK. The fixed URI `nflx://www.netflix.com/browse` resolves with
DEFAULT to `NetflixComLaunchActivity`. Resolution alone does not prove that a
Remote v2 command launches Netflix successfully.

The implemented and reviewed adapter now sends exactly that fixed URI; its local
TLS test checks the payload. Main freshly reran 55 tests with ResourceWarning
treated as errors: PASS. Corrected shortcut source snapshot (SHA-256):

```text
tv_remote.py 6be01a1d5070b3a7fb814d53cc85dc90b36770e183efeef2a5e6702fc96f0b68
```

The pointer.py and app.py hashes remain those of the recovery snapshot above.
The GUI was reopened on this corrected snapshot. After Home → Netflix, a
20-second wait and pointer movement, the user confirmed **"Netflix opens and
both stay connected"**. Live Remote v2 launch and continued connection: **PASS**.
All enumerated H0 functional checks now have user or diagnostic evidence,
including UHID profile selection, the corrected Netflix shortcut, both-channel
reconnect and playback. **H0 PASS is limited to this local Mac functional proof.**

## Remaining production reliability limitations and next gate

Abrupt hardware link loss, power loss and interruption of an active drag were
not hardware-tested. Local STOP/fence regressions passed; these do not establish
those hardware behaviors. Idle loss is checked every 100 ms, and in-flight sends
cannot be retracted. These are explicit production reliability limitations,
not claimed hardware PASS results.

Next is a new hub trust/PWA **design** and acceptance gate. This result does not
authorize network implementation or certify PWA, Windows, other devices or
universal robustness. Historical SDK cursor FAIL, M0 FAIL and M0.1 STOP remain.

Captures and device addresses are intentionally outside Git. Future reliability
checks must be recorded from observation. The legacy ADB transport is not claimed
encrypted. No APK, firmware, root, or debugging-setting changes were made.
