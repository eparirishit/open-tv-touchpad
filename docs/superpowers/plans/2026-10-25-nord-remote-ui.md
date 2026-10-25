# Nord remote UI implementation plan

> Use subagent-driven-development for the independent native view task; main owns integration, tests and build.

**Goal:** Google TV-inspired native remote screen for existing Nord controls.
**Architecture:** RemoteScreen owns view composition, local page switching and
styling; ProbeActivity retains every Bluetooth/session gate and binds actions.
**Tech stack:** Existing Java/native Views/JUnit and installed AndroidX test tools.
**Spec:** ../specs/2026-10-25-nord-remote-ui-design.md

## Contract

RemoteScreen(Context, BiConsumer<Integer,Integer> move, Runnable tap) exposes:
root LinearLayout; status/migrationNotice TextView; start/connect/visible/confirm/
forget/stop/close/setup Button; moveLeft/moveRight/moveUp/moveDown/click Button;
scrollButtons EnumMap<MouseReport.Direction,Button>; touchpad TouchpadView;
inputs List<Button>. Methods showSetup(boolean), setConnected(boolean).
No Bluetooth calls; Activity binds all actions except local Setup/Remote toggle.
showSetup and toggle DOWN cancel the pad. Native toggle returns false.
No gesture algorithm or report/session/storage change. No new runtime dependency.

- [x] View worker: create RemoteScreen; style TouchpadView drawing only. Native
  dark charcoal/rounded controls/green state, responsive portrait/landscape,
  always-reachable footer and scrollable content. Preserve pointer alternatives.
- [x] Main: bind existing callbacks/inputs/status in ProbeActivity; retain shared
  guarded button behavior and scroll DOWN cancellation; version7,0.7-diagnostic.
  Dark native app theme. Explicit permission/lifecycle gates unchanged.
- [x] Main: native instrumented UI tests with fake callbacks, no Bluetooth:
  disabled startup, footer/scroll/setup reachability at phone sizes and enlarged
  text; Setup DOWN/click cancels a pending pad tap. Main uses existing pinned
  AndroidX test dependencies, not new runtime libraries. Existing pure tests pass.
- [x] Main: Gradle build/lint/unit/instrumented checks, rendered screenshots on a
  disposable emulator; review full diff including callers and tests. Fix scoped
  failures and rerun affected checks. Preserve0.6 APK and compare runtime invariants.
- [x] Document actual checks/limits, source/APK hash, backlog and install only on
  stopped verified Nord with unchanged enrollment. Owner smoke remains separate.

Preflight: clean published basef5cee1c, existing linked worktree reused on new
nord-remote-ui branch. Baseline101 probe tests passed immediately before this work.
Owner explicitly requested build and selected Google TV styling; no additional
scope/transport/lifecycle decision is needed for this minimal presentation pass.
