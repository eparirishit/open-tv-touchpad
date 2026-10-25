# Nord remote UI — minimal Google TV-inspired pass

Status: user-directed UI implementation. The owner requested “Take the UI design
style from the google tv and build on top of it as a bare minimum,” then installed
and connected Google TV on the Nord for direct reference. Routine visual choices
below are builder choices within that request; no new transport is authorized.

## Reference and outcome

Inspected the actual Google TV remote on the Nord, version
4.39.5193.999139630.2-release, on2026-10-25. It shows a charcoal remote panel,
green connection label, a dominant central navigation surface, circular secondary
actions and paired pill controls. Screenshot stays ignored/local; no Google
artwork, account image, device identifiers or branding is bundled.

Use that visual language with Open TV branding and the working Bluetooth mouse
controls. This is a0.7 diagnostic UI update, not N3 TV protocol integration.
Remote view puts the touchpad and four labeled scroll controls first. Setup is
an in-Activity secondary page for pairing, migration/trust instructions and Forget.
Native pointer alternatives remain reachable and clearly mean mouse movement,
not TV D-pad commands. Stop/Disconnect and Close stay outside scrolling content.
Status and explicit Start/Connect remain visible/reachable. No pretend Home,
volume, power, microphone or TV D-pad actions are added.

## Approach and invariants

Reuse native Android Views; extract view composition to RemoteScreen so Bluetooth
and session ownership remain in ProbeActivity. A Compose/dependency migration
would add cost without improving this small pass. A color-only reskin would leave
setup dominating the remote. Choose native composition with Remote/Setup pages.
No runtime dependency, new permission, network, enrollment migration or descriptor
change. Keep version2 record, reportID1/five-byte payload, native admission,
100ms scroll limit, explicit Start/Connect, teardown barrier and five-minute
foreground-only contract. No automatic connection or pairing.

Touchpad drawing may change; gesture/event/scheduling behavior must not. Changing
pages cancels pending touchpad input on button-down and click, preserving native
click semantics. Merely viewing Setup neither grants input nor reconnects; hidden
pad loses its pending gesture. Error status and migration instructions remain
available; initial first-enrollment can show Setup, returning owners see Remote.
Use labeled native controls, at least48dp targets, readable contrast, visible
keyboard focus/pressed/disabled states. Respect system-bar insets. Remote and
Setup must remain usable in portrait/landscape and with enlarged text; footer
never depends on scrolling. Keep existing accessible pointer buttons.

## Acceptance and limits

Build/test/lint and independent review before replacing0.6. Verify native view
layout and local page transitions with a disposable emulator (no TV connection),
including no pending tap after switching and no HID-triggering setup actions.
Recheck existing101 tests. Inspect actual rendered remote/setup screenshots.
Install same-signature0.7 only on the verified stopped Nord, preserving enrollment;
no re-pairing needed. Verify portrait/landscape and ask owner for touchpad/scroll,
Setup transition and Stop smoke checks. Preserve reviewed0.6 artifact for rollback.
N1/N2 results remain historical for0.5/0.6;0.7 hardware claims need new evidence.
N3 TV controls and N4 daily-use lifecycle remain separate backlog work.
