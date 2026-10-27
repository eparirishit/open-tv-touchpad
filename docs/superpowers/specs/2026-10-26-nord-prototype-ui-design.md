# Prototype-based Nord UI refresh

The owner supplied `Open TV Android App.html` and requested implementation of its UI.
This authorizes a native presentation update, not recovery or transport changes.

Use existing RemoteScreen/TouchpadView and Activity bindings. Remote and Touchpad
are separate bottom tabs; Setup toggles from the header, with Projector, TV controls
and Display sections. Touchpad offers surface/buttons alternatives plus four scroll
buttons. Remote has Back/Home/Netflix, a circular spatial D-pad and volume/playback.
Settings stays at the right end of the header; Stop sits immediately before it
and is hidden while disconnected. Close is removed per owner follow-up.
Stop has a leading square icon and is enabled only once the input connection is
ready. Its visibility follows that same readiness. During setup the existing foreground-notification Stop test remains available.
OK is visibly disabled before TV admission and blue when enabled; directional
arrows have no circular backgrounds, retaining a rectangular keyboard-focus outline.
Tabs use fill/selected semantics and fill-based keyboard focus without border
highlights. Display choices center sun/moon icons directly above their labels
with a small native drawable gap; Netflix uses its N mark with
a disabled treatment and the existing accessible Open Netflix label. Use the prototype's light/dark palettes, rounded
cards, blue accents, native vector icons and a persisted appearance-only preference.
Theme changes update existing views in place; never recreate a live run or save
pairing fields. Keep status truthful; do not reproduce the mock timer or address.

Maintain all Activity-owned admission and button bindings, masked pairing code,
bounded inputs, explicit Start/Connect, five-minute foreground run, Stop behavior,
and known Bluetooth cleanup block. No permissions/dependencies/transport changes.
All page, setup-section, touchpad-mode and theme navigation cancels pending gestures
without sending input or granting admission. Keep existing accessibility mouse
buttons. Every interactive target >=48dp, selected state accessible, all screens
usable at320dp width, landscape,1.5 font scale and with IME. Use scrolling for short
viewports with the scrollbar hidden, rather than prototype's fixed844px canvas. No screenshot may contain real
codes or addresses. Original HTML is local design evidence, not executable app code.

Implementation choice: extend native views; WebView conversion would duplicate
state/control boundaries and change trust surface. Header fixed, status and page
content scroll together when space is scarce; bottom mode tabs are hidden on Setup and while the IME is visible, restoring
only when both conditions clear. The header Setup toggle returns to the previous
Remote/Touchpad page. Light default, saved dark preference allowed.

Acceptance: native screenshots of all pages/themes; real-view tests for navigation,
large text/IME reachability, independent status and unchanged masking/admission;
all Nord unit tests, lint and APK assembly; independent review before device update.
Hardware UI smoke pending until owner tests the exact installed artifact. Bluetooth
recovery remains unresolved and is not part of this UI request.
