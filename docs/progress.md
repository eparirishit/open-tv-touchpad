# Project Progress

##0.20 owner acceptance and next work (2026-10-27)

The owner reports testing0.20 and “everything works fine”, accepting its requested
UI changes and normal-use smoke. This closes the0.20 UI acceptance item; it does
not independently certify previously unresolved failure/revocation scenarios.
Next priority is Bluetooth-loss cleanup/reconnect, whose actual native completion
acknowledgments are still missing in the captured trial. Keep the barrier and
current Force stop recovery until a reviewed completion policy is proven. The
TLS close-read fixture order issue also remains open. Then complete remaining
N3 reliability evidence, integrate draft PRs #8/#9 in base-first order and design
N4 daily-use lifecycle/packaging. No merge or new lifecycle contract is approved
by the acceptance report alone.

## Touchpad simplification and setup overlap (2026-10-27)

Owner requested removing redundant alternate mouse UI, moving sensitivity below
scrolling, fixing overlapping numbered text, shortening confirmation text, bold
tabs and taller OTP cells. Regression reproduced the row overflow at450dpi and
normal font size. The row alignment/spacing fix stays in RemoteScreen; accessible
input replaces visible fallback buttons through the same Activity gates. Final
verification:176unit/26focused native UI/OTP/accessibility tests pass, lint0errors/
41warnings and both APK builds pass. Reviews/rereviews found no actionable defects.
Reviewed0.20 is installed with current enrollment bytes preserved and stable launch
verified; owner acceptance of the requested UI/normal-use smoke is now recorded
above. Installation/source/
artifact evidence is in the UI report.

## UI details and visible OTP (2026-10-26)

Owner requested prototype status indicators, quieter diagnostics, touchpad speed,
numbered Bluetooth instructions, muted-blue disabled OK and six TV-code cells.
The explicit follow-up requests visible OTP characters, superseding masking only.
Implementation uses existing admission and native views; transport, saved pairings,
Stop and lifecycle remain unchanged. Hardware Wi-Fi testing is deferred by owner
until tomorrow.176unit and19focused native UI/OTP tests pass, lint0errors/41warnings,
and both APK builds pass. Independent/Codex rereviews found no actionable defects.
Reviewed0.19 is installed with current enrollment state preserved and stable launch
verified; owner hardware presentation/OTP/sensitivity acceptance is pending.
Verification/install evidence is in the UI report.

## Header visibility and theme spacing (2026-10-26)

Owner requested connected-only Stop visibility, Settings at the right edge and
compact alignment of theme icons/labels. Implemented in0.18 using existing
readiness and native compound drawable layout. Verification/install evidence
is in the UI report. Reviewed0.18 is installed with current enrollment bytes
preserved and stable launch verified. All12 focused UI native checks pass;
167unit tests pass and lint/build pass. Broader native suite retains a TLS
close-read assertion failure that passes standalone; tracked separately, with
no full-suite PASS claim. Owner visual acceptance remains pending. Bluetooth
cleanup and enrollment recovery remain separate.

## Connection-state UI refinements (2026-10-26)

Owner requested connection-dependent Stop, disabled-looking OK, borderless tab
selection and theme/Netflix icons. Implemented in0.17 using existing admission
and native presentation. The real-Activity Stop and missing-icon regressions
failed on0.16 before implementation.167unit/27native tests pass (one opt-in
projector diagnostic skipped); both reviews found no actionable issues. Reviewed
0.17 is installed with current enrollment bytes preserved and stable launch
confirmed. Owner visual/connection-state acceptance is pending; full evidence is
in the UI report. Bluetooth cleanup and enrollment recovery remain separate.

## UI refinements (2026-10-26)

Owner requested hiding Setup’s bottom tabs, removing Close, adding a Stop square
and simplifying D-pad styling. Implemented in0.16 without changing transport,
admission or the launch fix. The footer regression reproduced the old behavior
before implementation.167unit/26native tests pass (one opt-in diagnostic skipped);
complete review found no actionable defects. Reviewed0.16 is installed with
current enrollment bytes preserved and stable launch confirmed. Owner visual
acceptance is pending; see the UI report.

## Prototype launch correction (2026-10-26)

Owner0.14 launch failed. The real Activity called the system-bar controller
before its window decor existed; API31 emulator launch reproduced the failure.
Version0.15 waits for view attachment and adds a real-Activity launch/theme test.
167unit/24native tests pass; one opt-in projector diagnostic is skipped. Reviewed0.15 is installed and owner confirms app launch, screens and themes work.
Both enrollment files were absent BEFORE the correction install; system logs show
an app-data clear event of unknown origin. The owner denies clearing data. The
installer preserved the verified-empty state. Pairing recovery/control smoke remain
pending; see the [UI report](gates/nord-prototype-ui-report.md).

## Prototype UI refresh (2026-10-26)

Owner requested implementing the supplied HTML design in the native Nord app.
Version0.14 separates Remote/Touchpad, groups Setup sections, adds light/dark
appearance and keeps fixed Stop/Close. Only presentation and its tests change;
Bluetooth recovery remains blocked. Reviewed0.14 is installed with both enrollment
stores unchanged and no automatic run; owner smoke is pending.167unit/23native
tests pass, with one opt-in hardware diagnostic skipped. Full evidence is in
the [UI report](gates/nord-prototype-ui-report.md).

## Bluetooth-loss follow-up: recovery still blocked (2026-10-25)

Owner confirmed0.13 Home/pointer smoke. Adapter interruption reached BLE_ON and
was restored to ON. The captured run reached the teardown deadline after
profile loss without selected-disconnect or unregister acknowledgments. No
barrier bypass or further code change was made. Owner confirms both channels
disconnected and all buttons disabled. Physical-remote confirmation for this
trial and explicit reconnect remain pending. See [N3 report](gates/n3-report.md).

## Bluetooth cleanup correction installed (2026-10-25)

Owner-requested0.13 changes only shutdown recovery: profile loss during Stop
waits for actual selected disconnect/unregister callbacks and all other cleanup,
without clearing independent failure latches or trusting proxy defaults.
The captured-order regression fails before and passes after;167 unit tests,
lint (0errors/37warnings) and debug assembly pass. Scoped independent and complete
Codex reviews found no remaining actionable findings. Source `f5b7de1` is
installed as0.13 on the verified stopped Nord, with matching signer, both
enrollment stores unchanged and no automatic remote run. Hardware recovery
confirmation remains pending; prior owner acceptance applies only to0.12.
See [N3 report](gates/n3-report.md) for the bounded correction and test limits.

## N3 combined controls and saved restart passed on0.12 (2026-10-25)

The owner confirmed the requested Start/Connect, Home/D-pad/OK, Netflix launch,
pointer/title tap/scroll, Back/volume and Stop checks work as expected; the
physical remote remains usable. This verifies the combined smoke on the recorded
0.12 artifact without new code or installation. The owner also confirmed Force
stop/reopen preserves both pairings, input remains disabled until Start/Connect,
and Home/pointer/scroll work without another code, followed by Stop. The owner
now also verifies at least20-second Netflix post-launch stability with pointer
title selection/Back, and visible pause → resume → pause through Play/Pause,
followed by Stop. Profile selection by Nord pointer/tap and all four scroll
directions now pass with no unintended title opening or pointer movement.
Remote/Setup layouts now pass owner checks in both orientations at normal and
largest configured font size, with readable/reachable status and controls.
Live keyboard layout now passes in both orientations at the usual font size:
Setup fields, Confirm, status, Stop and Close remained readable/reachable with
the address field focused and pairings/address unchanged. Remaining Bluetooth-loss
acceptance is open; this did not repeat live pairing-code entry.
Projector Wi-Fi loss/restoration now passes the owner check with the Nord app
visible: both channels stop and stay stopped, and the physical remote works.
The owner also confirmed foreground loss/return leaves all app controls disabled
and the explicit five-minute expiry stops all controls; the physical remote
works in both checks on0.12. Overall N3 is not yet PASS.
The Bluetooth-loss trial reached the owner-reported disconnected/input-disabled
state but native teardown remained unconfirmed and required Force stop/reopen.
Bluetooth is confirmed ON afterward. The owner now confirms TV/pad/scroll input
was blocked, the physical remote worked, and Force stop/reopen recovered
Home/pointer/scroll without re-pairing, only after explicit Start/Connect.
Documented recovery passes; native cleanup and the controlled OFF transition
remain unconfirmed, so full Bluetooth-loss acceptance stays partial. A fresh
contiguous trace now identifies PROFILE_LOST during Stop as a definite permanent
failure latch, preceding disconnect/unregister acknowledgments. All other native
cleanup is not proven; four focused cleanup tests pass and runtime is unchanged.
See [N3 report](gates/n3-report.md) for exact coverage and artifact identity.

## N3 TV pairing verified on0.12 (2026-10-25)

The owner0.11 pairing retry reaches CONTROL_EXCHANGE then fails after10–15
seconds. Thus code binding/secret acknowledgment and pinned control TLS completed,
but overall enrollment did not finish. Version0.12 tests the smallest difference
from the working Mac reference: include optional POWER status in negotiation
(mask611 when offered). There is no power command or relaxation of readiness,
message ordering, authentication or timeout. The expected-feature regression
is RED at579 before the change; all166 unit tests and build/lint pass afterward.
Reviewed0.12 is installed with signing identity and enrollments preserved; the
owner confirms "TV controls paired; app stopped." Inspection confirms version12,
no probe service, and both enrollment records present and nonempty. The pairing
blocker is resolved; combined-control/reconnect/lifecycle acceptance remains
pending. Source9f29047; artifact identity is in the [N3 report](gates/n3-report.md).

## N3 certificate compatibility fix verified (2026-10-25)

The owner-approved0.11 fix removes only the explicit peer certificate
self-signature check. The fixture first reproduced the same rejection; after
the fix, all15 native TLS tests pass on both emulator and Nord, including invalid
self-signature pairing/pinned handshakes, missing/wrong private-key rejection,
and different-DER/same-key pin rejection. All166 unit tests pass; lint0 errors/37
warnings and APK assembly pass. Independent review found no production blocker.

Version11 is installed with the same signer and unchanged enrollment stores.
The real projector TLS-only diagnostic now completes with the existing app
identity, without sending pairing/control frames. Test instrumentation is removed.
The owner has been asked to retry TV-code pairing. **The immediate TLS blocker is
fixed; owner pairing and combined N3 acceptance remain pending.** See
[N3 evidence](gates/n3-report.md) for artifact identity and limits.

## N3 pairing diagnosis (2026-10-25)

The0.10 UI now confirms PAIRING_TLS. A reviewed TLS-only native diagnostic and
independent Mac OpenSSL check establish the immediate blocker: the server's
certificate fails the explicit self-signature check. No pairing/control frames
were sent by these checks; enrollments are unchanged and instrumentation was
removed. A [narrow trust-anchor compatibility decision](superpowers/specs/2026-10-25-tv-trust-anchor-compatibility.md)
was approved by the owner on2026-10-25. Implementation is limited to the explicit
peer self-signature check; completed verification and installation are recorded above.

The owner reproduced the0.9 pairing failure with the confirmed projector address.
Both Remote v2 ports are reachable from the Nord and Mac; the app's failed phase
is not yet known. Reviewed0.10 now displays a fixed stage label while preserving
trust, protocol, deadlines and Stop behavior. All166 unit tests pass; debug
assembly passes and lint remains0 errors/37 warnings. A same-signature update on
the stopped Nord preserved both enrollment stores; no automatic run started.
The retry and TLS-only evidence above identify the blocker. This is diagnostic
instrumentation, **not a pairing fix or N3 acceptance PASS**.
See [N3 report](gates/n3-report.md) for artifact identity and installation evidence.

## N3 combined remote installed (2026-10-25)

Reviewed implementation `8763f5a` adds TV controls to the existing Nord Bluetooth
remote. Version0.9 is installed in place on the stopped authorized Nord; signing
identity and both enrollment stores are preserved, foreground confirmed, no run
started automatically. All164 unit tests and17 emulator native tests pass; lint
has0 errors/37 warnings. Independent review and focused re-reviews close the
handshake-write race, deferred-Forget recovery and cleanup-order findings.
The owner's first Pair TV attempt failed with the generic connection/pairing
error and reported native teardown completion. **N3 owner acceptance is blocked
at pairing**; the failure stage and entered target remain unconfirmed. Refreshed
Nord debugging is connected: version9 is stopped, Bluetooth enrollment is present,
and TV enrollment is absent. Both projector TV Remote ports are reachable from
the Mac and Nord; native app TLS/pairing failure still needs stage evidence.
No live TV control by0.9 has been observed. See [N3 report](gates/n3-report.md) for artifact identity,
review disposition, remaining compatibility limits and hardware checklist.

## N3 native foundation history (2026-10-25)

Owner approved the [unified N3 design](superpowers/specs/2026-10-25-nord-tv-controls-design.md)
and [implementation plan](superpowers/plans/2026-10-25-nord-tv-controls.md).
Work started on `nord-tv-controls` with0.7 preserved; the foundation proof and
combined update below subsequently installed0.8 and0.9.
N3 adds Bluetooth mouse plus pinned Remote v2 TLS, native Keystore credentials,
a stopped-only editable private-IP locator, and one shared foreground run/Stop.

The opt-in debug foundation implements locked protobuf schemas/runtime, bounded
framing, closed commands, pairing-transcript helpers, native identity/TLS and
separate fail-closed TV enrollment storage. All126 unit tests pass. Debug/test
assembly passes; lint reports0 errors/28 warnings. The API31 emulator passes14
native tests (12 TLS/identity and two existing UI tests). INTERNET is added only
to the opt-in diagnostic for N3; the current APK has no runtime TV caller.

The native failure was resolved without exporting keys: Conscrypt needs explicit
raw-RSA signing authorization, and native certificate generation uses RSA-PSS.
Strong PSS parameters are parsed/validated with the platform provider; runtime
TLS is restricted to1.2/1.3 and retains exact certificate pinning/native validation.
Wrong/expired-certificate tests now require certificate-policy causes, rather
than accepting an unrelated server failure. Independent storage review found no
concrete defects; scoped native re-review resolved both original blockers with
no remaining actionable findings.

The native provider prerequisite now passes on the Nord AC2001/API31:12/12 TLS
fixture tests, same-signature version8 installation, Bluetooth enrollment bytes
unchanged before/after, test identities cleaned and instrumentation APK removed.
The apparent missing-device result was a sandbox access failure; permitted
inspection found the existing authorized connection. No new address was needed.
No TV connection, pairing or input was used in this fixture. Source foundations:
`691f5f2` (wire), `9aec0b2` (TLS), `81a1ebe` (storage). Bounded transport and combined
UI/lifecycle implementation are complete in the installed0.9 snapshot above. N3 overall is not accepted until
integration review and owner hardware checks pass. See [platform evidence](gates/n3-platform-report.md).

## Nord UI refresh (2026-10-25)

Version0.7 implements the requested Google TV-inspired native presentation.
RemoteScreen owns views; ProbeActivity retains admission, lifecycle and callbacks.
101 unit tests and two emulator UI tests pass; lint has no errors/26 warnings.
Independent code review found no actionable defects. Same signing identity, four
permissions, report and enrollment formats are verified. Implementation source
`b26c05c` is installed in place on the verified stopped Nord; version7 and unchanged
enrollment bytes confirmed. Launch returned success; foreground unconfirmed.
Owner subsequently confirmed “Layout and controls work” for the requested
portrait/landscape, saved Start/Connect, Netflix movement/tap/scroll, Setup/Remote
and Stop sequence. Version0.7 owner smoke passes; the UI refresh is complete.
N1/N2 remain the historical0.5/0.6 results. See [UI report](gates/nord-ui-report.md).

## N2 scrolling implementation (2026-10-25)

Source `a162384` implements the approved N2 increment. Owner approved the [N2 design](superpowers/specs/2026-10-25-nord-scroll-design.md)
and one-time fresh pairing. Version0.6 implements four scroll buttons, a single
five-byte wheel/pan mouse report and version2 enrollment. Version1 cannot select
a peer; it is not rewritten on load. Existing foreground-only five-minute
lifecycle and N1 finger gestures remain. Scroll-button down and admitted clicks
cancel pending pad input; excess submissions within100ms are dropped.

The full probe suite passes101 tests with no failures/errors/skips; lint has
0errors/24warnings and debug assembly passes. The two added warnings concern the
native button touch listener; it returns false so native click/accessibility
handling remains. Independent implementation review passed with no actionable findings and an
independent101-test run. The verified
0.5 rollback APK is preserved. Owner confirmed both test bonds removed. The reviewed0.6 update is installed
in place on the unique authorized Nord; version6 and unchanged legacy enrollment
bytes are verified. Probe service was stopped. Launch returned success but the
probe was not focused; owner layout/disabled-input checks were requested before
fresh pairing. Owner subsequently confirmed both portrait/landscape layouts work and input
remains disabled before pairing. Owner then confirmed fresh connection, four-direction movement and Netflix tap
work on0.6. Owner then confirmed all four scroll directions work without unwanted clicks
or pointer jumps. Owner also confirmed Stop blocks input, the physical remote works, and explicit
saved reconnect restores movement/scrolling without re-pairing. The final owner
response confirms foreground loss/return and five-minute expiry keep input
disabled while the physical remote works. **N2 diagnostic behavior acceptance
PASS** for the recorded0.6 artifact/setup. Source and APK remain unchanged from
the reviewed/tested build. N3 TV controls and N4 daily-use lifecycle remain
separate follow-ups; crash cleanup and broader reliability are not certified.
See [N2 report](gates/n2-report.md). No historical gate becomes PASS.

## N1 touchpad implementation (2026-10-25)

Owner approved implementation of the first Nord-direct increment. Source
`fdb4905` builds0.5-diagnostic with finger movement and tap-to-click through the
existing HID gate. Stop/Close remain visible; queued motion is canceled on
terminal/lifecycle events. No descriptor, enrollment, permission or session-limit
change. Gradle passes76 probe tests, lint0errors/22warnings and debug assembly.
Independent review found a Stop-cleanup exception gap; it is fixed with a failing-
then-passing source-linked regression. Scoped re-review passed with no actionable
findings. In-place installation/version5 and unchanged enrollment bytes are
verified. Launch requested successfully and process running; UI foreground was
not confirmed because the display was asleep. The owner subsequently confirmed
“Movement and tap both work” after the requested connection/movement/Netflix-target
sequence. The next owner response confirmed all three checks: no swipe-return
click, multitouch cancellation/fresh-gesture recovery, and Stop with physical-
remote usability. The owner's final response confirmed long-press rejection,
foreground loss/return staying stopped, and five-minute expiry with no pad input
and continued physical-remote use. **N1 diagnostic behavior acceptance PASS**
for this artifact; planned implementation/review/manual sequence complete.
No code changed during these live checks. Broader accessibility, crash cleanup,
firmware encryption and daily-use reliability are not certified. N2 scrolling is
the next separately designed increment. See [N1 report](gates/n1-report.md).

## Nord handheld remote selected (2026-10-25)

The owner revised priorities to using the Nord itself as the remote. Mac/PWA,
iPhone/laptop access and a Mac-to-Nord bridge are deferred. The building-managed
network cannot provide the requested address reservation; no trust certificate
or listener was created. The [direct Nord design](superpowers/specs/2026-10-25-nord-direct-remote-design.md)
proposes a touchpad increment in the existing foreground-only HID diagnostic,
then separate scrolling, Android TV Remote v2 and product-lifecycle increments.
The first increment was subsequently approved and implemented as recorded above;
N2–N4 remain separately scoped follow-ups. No historical gate changes to PASS.

## Browser setup preflight (2026-10-25)

The owner requested moving on from the intermittent reconnect investigation.
It remains deferred and unresolved; the successful 0.4 trace remains valid for
that single trial. H1A/H1B and product integration gates are unchanged.
Browser setup preflight confirms macOS arm64, Python3.13.3, Tk8.6, the native
macOS keyring backend and installed versions matching the hub dependency lock.
No default hub state directory or visible port8443 listener was found. The Mac's
active default-route interface has a DHCP lease; router reservation is not yet
confirmed. Identity creation and HTTPS startup await stable-address setup.

## Bounded reconnect trace (2026-10-25)

The owner authorized the [diagnostic trace](superpowers/plans/2026-10-25-nord-reconnect-trace.md)
for one restart reproduction. Version0.4 (`d0a8c44`) is implemented, independently
reviewed with no findings, and installed on the verified Nord. Main reran 53 unit
tests, lint (0 errors, 20 existing warnings), debug build and packaged permission
checks. Only fixed event names, sequence and elapsed time are emitted, capped at
64 local Android log lines per run. Connection behavior is unchanged; no fix or
root cause is inferred. The owner prepared fresh compared enrollment and stopped
0.3 before the data-preserving update. Installed version4 is verified; foreground
launch was not confirmed and the bounded crash check found no probe crash.
The owner then manually performed the single0.4 restart trial and confirmed
“Connected; left/up moves the pointer”. The current-process fixed-tag capture
contains contiguous events1–33 without truncation: registration, explicit Connect,
selected connection after1325ms, neutral acceptance and movement acceptance. No
disconnect/failure appears in that prefix; the earlier issue did not reproduce.
The diagnostic task is complete without a behavior fix. The first-attempt failure
and H1A/H1B/product integration gates remain unresolved.

## Reconnect trust decision accepted (2026-10-24)

The [Nord reconnect proposal](superpowers/specs/2026-10-24-nord-hid-reconnect-decision.md)
compares the current fresh-pairing contract with retained OS-managed enrollment
and explicit Connect. Android12 cannot expose original bond-key continuity to
this app after an unobserved bond replacement. The owner selected “remembering
the verified projector approach,” accepting the documented OS-bond trust basis.
The [implementation plan](superpowers/plans/2026-10-24-nord-hid-reconnect.md)
extends only the isolated diagnostic with saved enrollment and explicit Connect.
The 0.3 diagnostic is implemented, independently reviewed with no actionable
findings, and installed/launched on the verified Nord. Source `7643aa8` passes
48 unit tests, debug build and lint (0 errors, 20 localization/SDK-age warnings).
Saved enrollment is separate from each run's authorization; Start/Connect is
explicit, Stop retains only enrollment, and Forget/observed re-pairing revokes it.
Native teardown must complete before another run begins. The first live fresh
enrollment → Stop → Start → Connect saved projector check passed by user report:
“Reconnect works; physical remote works”. This establishes explicit saved-bond
reconnect for this artifact/setup, not process-restart persistence or fault cleanup.
On 2026-10-25 the process-restart trial initially stopped with “Projector
disconnected. No input enabled. Native teardown completed. Tap start for a new
run”; the owner then reported a retry connected. An initial right-only movement
check appeared unsuccessful, but the same session's left/up check moved the
pointer by user observation. Saved enrollment and working input after restart are
established on retry; the first-attempt failure remains unexplained. No code was
changed. The subsequent Stop → Forget → Start check with the OS bond retained
passed by user report: “No saved projector; Connect and movement disabled”. This
establishes explicit app revocation and rejection of bond-only control, not
observed OS bond-change revocation or durable Forget across process death.
H1A/H1B and integration gates remain unchanged.

## Spare-phone transport investigation (2026-10-24)

Live read-only diagnostics confirmed the Nord AC2001 runs Android12/API31,
enables the Bluetooth HID Device profile, and starts `HidDeviceService` after
the owner enables Bluetooth. This clears the firmware configuration prerequisite
for an isolated foreground mouse probe. Crash/link-loss cleanup and negotiated-link
security remain unverified. The subsequently approved
foreground diagnostic APK (`954d666`) was built, independently reviewed and
installed/launched on the Nord. Its 21 unit tests and Android build/lint pass;
the user confirmed “HID registered,” establishing the actual registration callback.
The user also confirmed matching numbers on both pairing screens and the TV's
“OnePlus Nord connected” indication. The input attempt was blocked by the probe's
“App left foreground. Stopped” state. A discovery-only retest stayed HID registered;
fresh pairing reproduced the stop immediately after Pair on the TV. A bounded
native foreground service now preserves eligibility through setup prompts while
the session gate continues to reject background confirmation/input. Any pause
after confirmation ends the test. Local build/lint and 28 tests pass, and review
found no actionable findings. Revised APK `93da0d0` (0.2-diagnostic) is installed
and launched on the Nord. The user confirmed “Pairing stays active; pointer moves”:
the revised pairing lifecycle and Bluetooth pointer movement passed this live
retest. The user then confirmed “Click opens the target; Stop disables controls;
physical remote works”. Basic HID compatibility is PASS for this artifact/setup:
Netflix click activation and manual Stop/control disablement are observed. No
native disconnect-completion, held-button crash-release or transport-security
claim follows. The probe is stopped per user report.
This is a separate debug app
with no network permission, not an implemented bridge.
H1A remains NOT PASSED and real device integration remains blocked. Evidence:
[H1A report](gates/h1a-report.md#nord-live-configuration-check-2026-10-24).

Follow-up: reopening with the retained bond kept comparison/input disabled by
user observation; TV-side Connect was unavailable, so incoming reconnect was
not exercised. Read-only Android12 AOSP review supports HID authentication and
encryption enforcement in the platform. Exact vendor link security and successful
enrolled-peer reconnect remain unverified; API31 lacks the newer encryption-status
APIs. No product code changed. Lifecycle/fault-test boundaries are recorded in
the [research report](research/2026-10-24-protected-wireless-options.md#android-12-enforcement-trace-and-remaining-verification-2026-10-24).

Foreground-loss retest also passed by user observation on 0.2-diagnostic:
leaving a confirmed, working session and returning showed “App left foreground
after confirmation. Stopped. Close and reopen.” Only Stop and Close remained
enabled; the physical TV remote still worked. Input did not resume on return.
This does not certify process-crash, radio-loss or held-button cleanup.

Subsequent controlled adapter-loss/restoration check: PASS on 0.2-diagnostic.
With the confirmed probe foreground, Nord Bluetooth was disabled and restored
through its supported shell command; OFF then ON were verified. The user
confirmed stopped status, movement/Click disabled and physical-remote usability
after restoration. No input was injected or bond changed. This covers local
adapter shutdown/restoration, not out-of-range loss, successful reconnect,
process death or held-button release.

## Browser-only hub implementation (2026-10-24)

The accepted local-only design now has certificate identity, native approval,
HTTPS/WSS sessions, socket-bound control leases and a bundled PWA implemented
for H1B testing. Real TV control is deliberately unavailable. Native ownership
and browser trust checks are documented in [setup](setup-hub.md) and
[H1B evidence](gates/h1b-report.md). Local verification passes103Python and36Node tests; scoped code reviews and
their fixes are complete. Neither H1A nor H1B has passed. The earlier H0 proof remains separate.

As of 2026-10-24, D1 is resolved: the owner requires protected projector transport
and authorized browser-only H1B implementation. H1A remains NOT PASSED; no
plaintext product exception is authorized. The accepted design supersedes the
earlier proposed-status descriptions below for this hub work only.

## Current Status

The user selected local-only phone/laptop access with per-device certificate trust,
no account and no VPN. The [hub design](superpowers/specs/2026-10-24-local-pwa-hub-design.md)
and [seven-stage plan](superpowers/plans/2026-10-24-local-pwa-hub.md) are drafted.
Builder/security findings have explicit lifecycle and recovery rules. D1 remains
an owner decision: require proven protected projector transport, or explicitly
accept the existing unencrypted ADB hop for a trusted-LAN beta. Local HTTPS does
not resolve that hop. No product listener, trust installation or product backend
has been added. H1A/H1B/H2/H3 have not passed.

On 2026-10-24 the user approved a separate unified-remote hub proof (H0).
The isolated native Mac window has passed the scoped H0 functional proof.
It combines Android TV Remote v2 buttons and a no-video scrcpy input channel.
The user confirmed TV remote pairing/connection; diagnostic screenshots
confirmed direct SDK profile activation and vertical Netflix swiping. The user
reported NO visible cursor with the SDK pad. The default is now UHID-only
`HardwarePointer`, with mouse buttons and wheel scrolling; it never combines
accelerated cursor movement with SDK coordinates. The user confirmed "Pointer
appears and moves" in the UHID-only window: visibility/movement PASS. The user also
confirmed a UHID mouse click opens a Netflix title tile in the updated window.
The user confirmed both vertical and horizontal wheel scrolling work in Netflix.
The user also confirmed touchpad Stop, physical-remote use after Stop, and
touchpad reconnect. Home, D-pad/OK, Netflix shortcut, Back and both volume buttons
were initially reported working together; the shortcut claim is contradicted by
the later specific failure below. The user confirmed
horizontal dragging and normal release work in Netflix. The user subsequently
confirmed the remaining reconnect check and playback/pause/resume work.
All listed lifecycle findings are fixed, with no
remaining actionable findings in the app and pointer re-reviews. Main independently
freshly reran 55 tests with ResourceWarning treated as errors: PASS. Idle channel loss is
checked every 100 ms; Stop fences further input but cannot retract in-flight sends.
A later Connect touchpad failure was traced to a missing ADB transport, an
unresolvable configured hostname and daemon routing failure despite direct TCP
reachability. Main manually restarted an empty daemon (no devices or forwards)
and verified XK03H. Explicit Connect now permits at most one eight-second connect
to the exact configured private-IP/.local endpoint; no daemon restart or discovery
is automated. Missing-transport recovery passed without clicks/keys. The recovery
review found no actionable findings. In the reopened recovery snapshot the user
confirmed "Both stay connected and work" after Pair TV then Connect touchpad,
testing Home and pointer: original combined-connect regression PASS. Full
Stop/reconnect of both channels without pairing and playback/pause/resume were
subsequently confirmed by the user. UHID profile selection now also passed by
user observation: "Yes, it works". A subsequent Netflix-button failure dropped
both channels. Six remote-service ActivityNotFoundException entries name
`market://launch?id=com.netflix.mediaclient`, contradicting the earlier shortcut PASS.
The implemented/reviewed fix sends the fixed URI `nflx://www.netflix.com/browse`;
DEFAULT resolution finds NetflixComLaunchActivity, and main's fresh 55-test run
passed with ResourceWarning treated as errors. Neither resolver nor local TLS
evidence alone establishes live launch. The corrected GUI's live retest now passed:
"Netflix opens and both stay connected" after Home → Netflix, a 20-second wait
and pointer movement on tv_remote.py snapshot 6be01a1d… (full hash in the report).
All enumerated H0 functional checks have evidence: **H0 PASS, scoped local Mac proof**.
Abrupt hardware link/power loss and active-drag interruption remain untested
production reliability limitations; local STOP tests do not certify them.
Next is new hub trust/PWA design and its acceptance gate, not network implementation.
No PWA, Windows or universal robustness claim follows from this PASS.
See `docs/gates/h0-report.md`. No PWA/LAN service has been implemented.

The following status describes the original projector-hosted architecture:

Open TV Touchpad is stopped at the M0 certified-hardware gate. M0 recorded a
`FAIL` on 2026-10-20, so M1 and all product milestones remain blocked.

The gesture-focused M0.1 investigation completed on 2026-10-24 with a verified
`STOP`. All three Netflix modes and all three first-party calibration modes
were accepted by the service and then cancelled without activation. The
calibration result classifies the failure as `PROJECTOR_OR_GESTURE_DISPATCH`.
The original M0 failure and M1 block remain in force.

## M0 Evidence

- Source commit: `6223c19e4900c8086ba549e9fa326fd41d156c11`
- APK SHA-256:
  `f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a`
- Certified device: XGIMI Horizon XK03H, Android TV 11,
  `RT.3.24.327 release-keys`
- Tested app: Netflix `8.112.1 build 6 50665`, package
  `com.netflix.mediaclient`
- Result: the service could be enabled, its controller and cursor appeared,
  and cursor movement worked. Two user-triggered accessibility taps over a
  centered Netflix profile tile did not activate the tile.
- No ADB gesture or input injection was used. Later gesture, cancellation, and
  playback checks were not run after the required tap failed.

The detailed record is in `docs/gates/m0-report.md` and
`docs/gates/m0-checklist.md`.

## M0.1 Evidence

- Reviewed source commit:
  `a6f1b518961d80af9ca67b35b96bcdb4d292b45b`
- Debug APK SHA-256:
  `66173e45f1f0e392de9ce418cee55fdcdb1f5a5670a8ef77aa152e08d1af37bd`
- Emulator verification used only `emulator-5554`; unit, connected, lint,
  debug assembly, release assembly, evidence-verifier, and release-isolation
  checks passed.
- Certified-device result: all six permitted attempts ended `CANCELLED`; no
  intended Netflix or calibration target activated.
- Safety result: no observed key leakage, every cancellation removed the
  controller and cursor, and no ADB input command was used.
- `docs/gates/m0-1-report.md` verifies as `M0.1 report valid: STOP`.

## Milestones

| Milestone | Status | Notes |
| --- | --- | --- |
| M0 — Hardware Gesture Gate | `FAIL` | Required Netflix tap failed after the one allowed compatibility investigation. |
| M0.1 — Gesture Investigation | `STOP` | Compatibility exhausted; first-party calibration identifies projector or gesture-dispatch failure. |
| M1 — Local Trust and PWA Gate | `BLOCKED` | Must not start without a verified M0 PASS. Any redesign must first update the approved contract and pass its applicable prerequisite gate. |
| M2A–M5 | `BLOCKED` | Depend on the preceding hard gates. |

## Decision Required

The approved design makes Android accessibility gesture dispatch the only V1
input backend and Netflix success on this projector a hard gate. Continuing
requires explicit approval of a revised product target, input backend, or gate
contract. The approved H0 proof is scoped separately and does not reopen these gates.

The approved narrow investigation contract is in
`docs/superpowers/specs/2026-10-20-open-tv-touchpad-m0-1-gesture-investigation-design.md`.
Its certified-device execution is complete and does not authorize an M0 rerun
or M1. Continuing requires an approved target, backend, or gate redesign.
