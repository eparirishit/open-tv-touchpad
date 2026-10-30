# Nord guided setup wizard

Status: APPROVED by the owner (“go ahead”),2026-10-30.
Reference: owner-supplied Open TV Setup Flow.html and the six-part flow described
in chat. The attachment is a visual/interaction reference; its simulated progress,
timers and example values are not production state. Implementation evidence is
recorded in ../../gates/nord-setup-wizard-report.md.

## Goal and selected approach

One user journey: Overview → Bluetooth → TV address → Pair TV → All set.
The owner selected a guided three-step wizard to remove the repeated Start setup
and manual Stop/Start between Bluetooth and TV pairing. Ordinary saved Connect
stays on Remote/Touchpad. Manage and Reset remain separate from first setup.

Approaches considered: the selected sequential wizard matches the supplied
prototype and makes the next action explicit. A single-page checklist could
remove repeated Start but still leaves the user coordinating sections; it was
not selected. A new combined Bluetooth/TLS pairing protocol is unnecessary:
reuse existing bounded native operations behind the single user flow.

Use the existing native Android/Java views, light/dark styles, service/runtime,
trusted stores and closed UI requests. No WebView, embedded HTML, dependency,
new discovery/transport, public release or new remote commands.

## Invariants

- Preserve app ID/signer, Keystore identity, both existing enrollment formats,
  peer comparison, pinned TLS and native trust validation. Opening or upgrading
  setup never erases or re-pairs an existing enrollment.
- The service/runtime owns operations and actual cleanup. The UI renders current
  immutable state and submits explicit actions; it retains no native owners.
- Bluetooth and TV enrollment remain separate bounded native runs. Closing the
  Bluetooth stage and preparing TV pairing are internal transitions, not another
  user-facing Start setup. Do not overlap native owners.
- Setup never grants a remote input epoch. This includes any temporary HID link
  established by the exact Save and connect action. Preserve all normal Remote
  visibility/epoch rules and explicit Connect after setup; do not relax hidden
  control admission to make the wizard work.
- A timer, callback request return, unbound Activity or stored progress is never
  native completion proof. Existing failed cleanup latches and actual resource
  completion still gate all new operations. Closing may become recovery-required.
- Preserve the five-minute setup-run ceiling and existing phase/code/discovery
  bounds, including at most120-second discovery and code-entry windows. Display
  remaining monotonic time; never restart a deadline on render or rotation.
- No pairing code, Bluetooth number, active session, pending native action or
  comparison authorization persists across process restart. No code in saved
  instance state, autofill, learned input, capture, logs or review artifacts.
- The optional Nord background tip opens this app’s Android settings only.
  Nothing changes automatically; auto-launch remains Off, with no new exemption,
  wake lock, boot start, reconnect loop or relaxation of security/timeouts.

## Screens and actions

### Overview and resume

Use the supplied checklist, Have ready list and Done / Next / To do chips.
Start setup is shown when nothing is configured; Continue setup when partially
configured; Review setup leads to Manage when all three items are configured.
Have ready is an instruction list, not a green-checked claim that the projector
is reachable or that the physical remote was detected. The first missing
required step is Next, even if a later item is already Done.
For example, forgetting Bluetooth must retain a valid TV address and pairing.

Done means configuration/enrollment saved, not currently connected or a promise
that network, HID, background retention or battery gates have passed. Saved
pairings remain saved when a live connection fails. Where current verification
is unavailable, say so without clearing trust to make the checklist look neat.

Derive completion from authoritative enrollments and a validated saved address,
not an integer progress counter. Resume after reopen/process restart at a safe
screen for the first missing configuration step. Never resume a code/compare
screen as if its expired operation were still authorized.

### Step1 — Bluetooth

Start shows the three plain instructions and Nearby devices permission note.
Start pairing handles permission, native registration and Android discoverability
as one explicit user action. Deferred work after a permission/discoverability
result belongs only to this still-current, attended setup intent; cancellation,
UI departure or process restart cannot launch a stale operation later.

Waiting begins only after native registration and actual Android discoverability
approval. Its ring uses the granted duration, capped at120seconds and any earlier
setup deadline. Cancelled/denied requests show a retry action, not a fake countdown.
Timeline observations distinguish phone ready, visible, OS bond observation and
explicit comparison. A bond alone must never become authenticated completion.

Compare tells the owner to check the number in Android’s pairing prompt on the
phone against the intended projector. No invented/example number appears in the
production app, and no new capture of pairing digits is needed. Preserve:
**Codes match on both screens. Save and connect** and **They don’t match**.
The affirmative action requires the current eligible bonded candidate and
foreground UI, exactly as today. Mismatch cancels local enrollment; it cannot
undo an already accepted OS bond. Explain manual removal on both devices if the
owner already accepted mismatching digits, without altering other bonds.

Save and connect performs the existing explicit save and temporary HID connection
attempt. The wizard remains Setup, so no pointer/key/scroll input is admitted.
Use a Bluetooth-enrollment run intent independent of a saved TV record: repairing
Bluetooth must not require the old TV locator/network link or erase TV trust.
Normal Connect continues to require both channels when TV controls are enrolled.

Closing says Closing the link safely. Advance to Bluetooth done only after the
actual owner’s required native resource completion. Display real failure/recovery
when acknowledgments are missing; Continue stays blocked. If enrollment was saved
before a connection-test failure, retain it and explain the failure; retry saved
connection/completion rather than silently forgetting or requesting fresh trust.
Done shows Bluetooth paired; Continue advances to address or the next missing
step. The app handles any needed internal start when Pair TV is pressed later.

### Step2 — TV address

Use the projector’s own Wi-Fi IPv4 address instructions and the existing private
IPv4 validator, including octet, leading-zero and private-range checks. No ports,
router/gateway address, phone debug address, public IP or IPv6 in this version.
`192.168.1.50` is a placeholder example only: never prefill/save it as a device.

Inline validation and Save and continue replace the separate Save TV address /
Start setup instructions. Saving never claims reachability or successful pairing.
Use the validated saved TV locator if enrolled; otherwise retain a validated
address draft in app-private, backup-excluded storage so interrupted setup can
resume. Draft has no trust/authorization meaning and does not alter the existing
credential formats. A failed draft write stays on address with an honest error.

Changing an enrolled locator preserves exact certificate/client identity and
requires normal verification on later Connect. If a session exists, first stop
and wait for actual teardown internally, then allow mutation. No trust fallback
on a changed peer or network error. Current credential state takes precedence
over a stale draft; if credential/address state is unusable, do not mark Done.

### Step3 — Pair TV

Start explains the intended projector’s six-character hexadecimal code and
at-most120-second entry window. One Pair TV action prepares the bounded native
run and starts the existing pairing operation after required registration. There
is no second Start setup button. Clear pending continuation on cancellation/loss.

Show connecting/requesting code while opening the pairing exchange. Start the
countdown only when the protocol actually requests the code. Remaining time is
the earlier of code and setup-run deadlines. Do not show Connected / Paired as
an authentication success before code-bound pairing has completed.

Use **Enter the projector’s code** to avoid implying the user types into the TV.
Keep six visible cells and one authoritative native editable buffer, reusing
PairingCodeField for keyboard, paste, backspace, TalkBack and privacy behavior.
Do not copy the web prototype’s invisible-input technique into Android.

Leaving the app or losing eligibility cancels TV pairing and clears the typed
code. A later visit offers explicit retry with a new current operation. Back,
Cancel, expired code, permission loss and stale callbacks cannot submit an old
code or automatically start a new run. Existing pre-confirmation Bluetooth
system-dialog handling remains the sole bounded setup-dialog exception.

Unreachable address/network errors show the three supplied checks, Try again,
Change address and the assurance that Bluetooth enrollment is retained. Retry
must wait for actual cleanup. Keep the precise fixed diagnostic stage secondary.
TLS identity/pin validation, rejected code, expiry and unconfirmed cleanup must
have accurate distinct guidance; do not label every failure Can’t reach or offer
Re-pair / Forget as the default fix for network trouble.

Pairing success persists through the existing trust path, stops the native run
and waits for actual cleanup before All set. No automatic new control session.

### All set

Summarize Bluetooth paired, address saved and TV controls paired. State that the
remote will connect on explicit Connect; do not imply the channels are live.
Open remote returns to the existing Remote/Touchpad entry. The optional Nord
background-activity tip explains battery cost and opens this app’s Android app
settings. It never toggles settings or changes the existing owner-approved policy.

### Manage and Reset

Show separate Bluetooth, saved-address and TV-controls rows as supplied.
Change address edits only the locator after safe cleanup, preserving pinned trust.
Re-pair is a trust-replacement action: confirm the affected item and what will
need to be repeated before clearing that item through the existing forget path.
Bluetooth re-pair still requires manual OS unpairing on both intended devices
and a new comparison; no hidden Bluetooth bond-management API. TV re-pair requires
a new on-projector code and existing safe client-key/record replacement. Preserve
the other enrollment. No silent deletion merely by opening Re-pair or setup.

Reset holds the two Forget actions behind per-item confirmation sheets with
Keep pairing as the safe dismissal. Explain the exact affected record and OS-bond
scope, and that Forget is not routine connection troubleshooting. Preserve the
other pairing. Retain the last validated locator as a non-authoritative draft
where possible when TV trust is deliberately removed; address persistence failure
must never prevent credential revocation or pretend it succeeded.

## Completion, lifecycle and layout

Persist only authoritative completed configuration and the address draft.
Keep step/page navigation ephemeral; reconstruct a safe resume view on launch.
All in-flight work remains tagged to its current runtime/native generation and
eligible UI attachment. A new screen, binder, rotation or restored view cannot
confirm codes, release cleanup latches or start an abandoned intent.

Reuse current themes and native components. Replace the old Projector/TV-controls
setup tabs and obsolete run copy with the wizard/Manage content; retain Display
settings as a separate secondary Appearance entry from Overview/Manage, and keep
the Remote/Touchpad entry points. Appearance is not an onboarding step. Back/cancel semantics remain clear;
while connected, entering setup pauses input, and a configuration-changing action
handles stopping/cleanup before continuing. Stop/recovery remains reachable during
long operations even when next actions are disabled. No bottom remote tabs on
Setup, as already requested.

Match the supplied spacing, typography, stepper, chips and bottom actions in
native layout; do not hardcode its390×844 canvas. Portrait/landscape, largest
font, TalkBack, keyboard and long error text must keep status and escape actions
reachable. No screenshot capture of real pairing fields.

## Scope, proof and delivery

1. Add only the small service-owned wizard orchestration and validated address
   persistence needed by this flow, with deterministic actual-state tests.
2. Wire native screens/actions and countdowns; reuse trusted stores, timer/kernel
   and code field. No core transport or crypto rewrite.
3. Verify independent missing-step combinations, permission refusal, cancelled
   discoverability, mismatch, cleanup pending/failure, code expiry/UI loss, restart,
   stale callbacks, unreachable peer, identity failure, safe reset and layout.
   Existing visible-input/30-minute/cleanup proof tests remain required.
4. Independent review and repository-required complete Codex review, affected
   software checks, then same-signature stopped Nord update with byte-identical
   existing enrollments. Full destructive/re-enrollment hardware testing requires
   a deliberately chosen test session and explicit owner preparation; upgrading
   never discards the current pairings.

The existing N4 lock,30-minute retention/expiry, loss/recovery, endurance and
battery gates remain open. Wizard completion is not daily-use or public-release
certification. No new lifecycle exemption or weaker trust is authorized.
