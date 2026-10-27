# Nord UI Simplification Implementation Plan

> **For agentic workers:** Use subagent-driven-development for the native-accessibility
> unit; parent owns UI composition and integration proof.

**Goal:** Simplify Touchpad and fix setup/OTP layout without losing accessible input.
**Architecture:** Native View accessibility actions replace redundant visible mouse
buttons and reuse the existing Activity callbacks. Layout stays in RemoteScreen.
**Tech Stack:** Existing Java/Android API31+, no new dependencies.
**Spec:** ../specs/2026-10-27-nord-ui-simplification-design.md

## Constraints and review focus

Preserve all admission, cancellation,30Hz gesture, enrollment, code privacy and
Stop/lifecycle contracts. No network/control tests or silent trust restoration.
Check disabled accessibility actions, touch-click duplication, pending motion,
long/scaled numbered text and OTP editing/IME after size change.

- [x] Regression first: confirm old nonbold tabs/56dp OTP/current order fail; measure
  setup row overflow at real Nord metrics and large fonts before layout correction.
- [x] Accessibility worker: own TouchpadView.java, new TouchpadAccessibilityTest.java
  and res/values/ids.xml only. Named fixed16-unit directional actions plus Click,
  no actions while disabled, cancel touch before action, preserve one touch click.
  No shared UI/Activity edits, commits, installs or device gestures.
- [x] Parent: delete alternate-mode fields/pages/callers/tests, flatten pad page,
  put sensitivity after scroll, fix native step-row measuring/spacing, use exact
  label, bold visible tabs and72dp OTP height. Update docs/version20 and callers.
- [x] Unit/lint/APK builds, focused native proof and screenshot inspection.
- [x] Independent and Codex review, fix scoped findings and rerun affected proof.
- [x] Coherent commits, exact evidence, stopped same-signature update preserving
  enrollment if available, push existing feature PR. Live acceptance remains pending.
