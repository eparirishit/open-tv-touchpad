# Nord UI details and touchpad sensitivity

Owner requested prototype status dots, quieter diagnostic copy, touchpad simplification
advice, disabled blue OK, optional sensitivity, numbered Bluetooth setup and six
TV-code boxes. This extends only the isolated native diagnostic; Wi-Fi/projector
acceptance is deferred by the owner until tomorrow.

## Approach and scope

Extend existing RemoteScreen, TouchpadView and bounded TouchpadGesture. Retain the
secondary Buttons instead mode because the pad is excluded from accessibility;
four scroll controls remain necessary for Netflix browsing. No new transport,
permission, dependency, commands or lifecycle/recovery change. The owner supplied
the detailed UI request; native implementation choices below are builder choices.

- Mouse/TV labels gain9dp leading dots: mouse green when ready, otherwise gray;
  TV green when its transport is ready, otherwise amber. Text still states status;
  no color alone communicates availability. Keep TV/Mouse states independent.
- Diagnostic message is13sp with line spacing/padding and a quiet inset background;
  keep the complete message and live-region behavior, no fake timer or truncation.
- Disabled OK is muted blue; enabled OK stays accent blue. Disabled input remains
  blocked by the existing Activity/session checks.
- Pointer sensitivity is a native seven-position SeekBar,0.5× through2× in0.25×
  steps, default1×. Persist only the bounded non-secret step in touchpad preferences.
  Scale movement deltas after physical-dp slop recognition and before the existing
  bounded accumulator. Preserve30Hz, signed127 reports, fractional remainder and
  dispatch clock. Changing value cancels the active/pending gesture; never emits
  movement/tap or enables input. Tap/long-press/multitouch/Stop rules do not change.
- Bluetooth setup mirrors the prototype’s three numbered instructions and retains
  manual TV pairing, both-screen code comparison, explicit confirmation, saved-bond
  assumption, foreground warning and Forget semantics.
- TV code is one native EditText presented as six rectangular cells. This avoids
  six competing focus/secret buffers. Preserve native editing/backspace/paste,
  six-character hexadecimal filtering, visible characters per the owner’s explicit follow-up, no saved state/autofill/
  learning, admission-controlled enabling and clear-on-stop/pause. The field owns
  no extra code copy. All drawing is native and never logs or saves its contents.

## Acceptance and ownership

TouchpadGesture/View own scaling/cancellation; RemoteScreen owns the slider and
preferences, dots, message, instructions, OK palette and field composition;
ProbeActivity passes actual TV readiness to the status display and retains all
input and pairing decisions. PairingCodeField owns the secure cell presentation.
No protocol/session trust or enrollment changes are authorized.

Unit proof covers gain, fractional/bounded/rate-limited movement and cancellation.
Native proof covers real Activity launch, layout/IME/font/theme, status independence,
slider bounds/persistence/no admission, cell rendering/editing/visible OTP/unsaved state
and existing navigation/Stop guarantees. Review whole incremental change and
callers, including untracked files. Inspect synthetic native screenshots; no real
pairing values. Preserve the unresolved broader TLS fixture and Bluetooth blocks;
never claim those gates PASS. Build/install only after review and a stopped-device
check; preserve actual current enrollment state. Hardware feel/TV pairing remains
unverified until the owner’s network test.

## Explicit OTP visibility correction

The owner explicitly requested unmasked OTP-style input during implementation.
Display each typed hexadecimal character in its cell. This supersedes only the
older masking requirement for this field; keep six-character validation, no
state/autofill/learning/logs and clear-on-end/lifecycle unchanged. Never capture
real pairing values in screenshots/reviews. Synthetic test content is allowed.
