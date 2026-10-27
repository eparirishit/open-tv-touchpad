# Nord touchpad and setup simplification

Owner requests sensitivity below scrolling, removal of redundant Buttons instead/
directional mouse UI, nonoverlapping numbered setup, exact confirmation wording,
bold tabs and taller OTP cells. Continue the isolated diagnostic on its existing
feature branch; no transport, enrollment, lifecycle or network change.

## Native approach

Flatten Touchpad to pad → four scroll controls → sensitivity. Delete the alternate
mode, fields and Activity bindings. Preserve accessibility by making the pad a
native accessible control with named bounded Move left/right/up/down and Click
actions, routed through the same existing callbacks/Activity gates. Each action
cancels pending touch input; disabled actions submit nothing. Ordinary touch taps
must still click once. No accessibility service/permission or projector app change.

Numbered setup rows use top alignment with baseline alignment disabled, natural
multiline height and explicit readable line spacing; badges size to their text.
Verify real Nord font/display metrics plus small/large font layouts. Confirmation
label is exactly “Codes match on both screens. Save and connect”. Visible tab
labels are bold. OTP field minimum height becomes72dp while retaining its one
native editor, filtering, visible characters and no state/autofill/learning contract.

## Acceptance

Native regression for old row overflow, slider below every scroll button, removed
alternate UI, bold tabs, exact label and72dp cells. Accessibility node/action tests
prove bounded callbacks only while enabled, cancellation and exactly one click.
Retain existing unit/native layout/IME/privacy/admission proof. Inspect emulator
screenshots, review complete change and callers, preserve current enrollment on a
stopped same-signature Nord update if available. Hardware projector/network
acceptance and existing Bluetooth/TLS-fixture issues remain separate.
