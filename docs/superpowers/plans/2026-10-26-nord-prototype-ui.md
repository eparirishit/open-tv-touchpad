# Nord prototype UI implementation plan

Goal: implement the supplied design using the existing native presentation layer.
Spec: ../specs/2026-10-26-nord-prototype-ui-design.md

- [x] Extend RemoteScreen with tabs, circular D-pad, setup sections, truthful status
  card and in-place theme selection. Preserve its public Activity bindings.
- [x] Add bundled native vector icons and themed TouchpadView drawing only.
- [x] Adapt native view tests to the page structure; prove gesture cancellation,
  masking, admission preservation,48dp targets and small/large/IME layouts.
- [x] Run unit/native tests, lint/build; inspect screenshots and fix actual issues.
- [x] Review complete change; record exact APK and update capabilities/progress.
- [x] Offer/install same-signature update only on verified stopped authorized Nord;
  preserve enrollment and report owner smoke as pending until observed.

Write ownership: implementation subagent RemoteScreen.java only; parent icons,
TouchpadView, tests, version/styles and docs. Independent reviewer reads all changes.
No change to Bluetooth recovery, session ownership, TV protocol or admission.

Owner0.15 presentation smoke passes after correcting0.14 Activity launch. The
real-Activity regression now covers launch/theme changes; combined controls await
pairing recovery after a system-reported app-data clear of unknown origin. No N3
or Bluetooth-recovery acceptance is claimed.
