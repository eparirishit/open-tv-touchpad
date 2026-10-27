# Nord UI Details Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement
> independent units, with parent integration and review.

**Goal:** Deliver the owner’s prototype refinements with bounded pointer sensitivity
and a visible six-cell OTP pairing field.

**Architecture:** Native Android presentation over existing Activity admission.
Gesture scaling stays in TouchpadGesture; a native EditText keeps one secret buffer.

**Tech Stack:** Java, Android API31+, current Gradle/dependencies only.

**Spec:** ../specs/2026-10-26-nord-ui-details-design.md

## Global constraints

No transport/permission/dependency or lifecycle change. No code persistence,
logging, autofill or IME learning. Preserve30Hz, signed127, bounded accumulator,
fresh-down cancellation and default1×. Keep accessibility fallback and scrolling.

## Review focus

- Slider changes during a pending gesture must not flush movement or grant input.
- Six cells must retain native keyboard paste/backspace/filtering and visible OTP characters.
- Theme/navigation/rotation must not save code or enable pending controls.
- Small widths,1.5font/landscape/IME must retain reachable setup and Stop.
- Mouse-only connection must not make the independent TV dot green.

## Tasks

- [x] A: Gesture implementer owns TouchpadGesture.java, TouchpadView.java and
  TouchpadGestureTest.java. Add setter setSensitivity(double) on gesture/view,
  reject nonfinite/out-of-range0.5..2, cancel old gesture/frame before gain change;
  scale relative deltas before bounded accumulation, keep slop in physical dp.
  Add focused failing tests, implement and run unit proof. No other files/commits.
- [x] B: Field implementer owns new PairingCodeField.java and its standalone native
  tests only. Subclass EditText with one backing value; own secure field config,
  six native rectangular cells, visible OTP input, native caret/edit/paste/filter rules,
  setPalette(surface,border,focus) for integration. No copied secrets/state, no
  dependency. Prove tests including native layout. No shared-file edits/commits.
- [x] Parent: integrate RemoteScreen/ProbeActivity/status/slider/preferences and
  numbered setup, update existing native tests and version19. Run narrow red/green
  then unit/lint/build and focused UI+field native tests. Inspect screenshots.
- [x] Independent review and Codex review of complete incremental change. Fix only
  in-scope actionable defects and rerun affected proof. Record other findings.
- [x] Commit coherent implementation, record source/APK evidence and current network
  block, optionally same-signature install only if the authorized Nord is available
  and stopped. Preserve enrollment; hardware acceptance remains deferred.

Owner correction: OTP characters must be visible, superseding masking only.
All other privacy/admission/cancellation requirements remain unchanged.
