# M0.1 Gesture Investigation Checklist

Use this checklist only on the certified XGIMI Horizon XK03H. Keep every result
`PENDING` until it is observed on the projector. Installation and read-only
diagnostics may use ADB; gesture or input injection may not.

The source commit and APK digest below were produced by the reviewed
emulator-only pre-hardware gate. They do not claim projector behavior.

## Evidence Restrictions

Record only the closed categorical values shown below.
Do not record or attach raw logs, screenshots, exact coordinates, raw key traces, profile names or screen content, package inventories, raw ADB output, or raw protocol output.

## Evidence Identity

| Field | Recorded value |
| --- | --- |
| Git commit | `a6f1b518961d80af9ca67b35b96bcdb4d292b45b` |
| APK SHA-256 | `66173e45f1f0e392de9ce418cee55fdcdb1f5a5670a8ef77aa152e08d1af37bd` |
| Projector | `XGIMI Horizon XK03H` |
| Android TV | `11` |
| Firmware | `RT.3.24.327 release-keys` |
| Netflix | `8.112.1 build 6 50665` |
| Tested application | `com.netflix.mediaclient` |

The immutable original M0 identity is commit
`6223c19e4900c8086ba549e9fa326fd41d156c11`, APK SHA-256
`f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a`,
decision `FAIL`, and investigation count `1`. Record M0.1 separately with
`M0.1-Investigation-Count: 1`.

## Prerequisites and Attestations

Replace each `PENDING` value only with `PASS` or `FAIL`.

| Check | Result |
| --- | --- |
| One clean debug APK is tied to the recorded commit and digest | `PASS` |
| APK installed with ADB before probe execution | `PASS` |
| Accessibility service enabled manually | `PASS` |
| No ADB gesture or input command used after installation | `PASS` |
| Evidence from every attempt uses the same APK identity | `PASS` |

## Certified Netflix Probe Order

For each attempt, record exactly one of `REJECTED`, `CANCELLED`, `TIMEOUT`,
`HANDOFF_FRAME_TIMEOUT`, `COMPLETED_NOT_ACTIVATED`, or
`COMPLETED_ACTIVATED`. Use `NOT_RUN` only when the certified order did not reach
that attempt. A second attempt is permitted only after
`COMPLETED_ACTIVATED`; never run a third attempt.

Record admission as `NOT_RUN`, `ACCEPTED`, or `REJECTED`; callback as
`NOT_RUN`, `NOT_APPLICABLE`, `COMPLETED`, `CANCELLED`, or `TIMEOUT`; and manual
target as `NOT_RUN`, `ACTIVATED`, or `NOT_ACTIVATED`.

| Order | Mode | Attempt | Result | Admission | Callback | Manual target |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `BASELINE_80_MS` | `1` | `CANCELLED` | `ACCEPTED` | `CANCELLED` | `NOT_ACTIVATED` |
| 1 | `BASELINE_80_MS` | `2` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| 2 | `SYSTEM_TAP_TIMEOUT` | `1` | `CANCELLED` | `ACCEPTED` | `CANCELLED` | `NOT_ACTIVATED` |
| 2 | `SYSTEM_TAP_TIMEOUT` | `2` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| 3 | `CONTROLLER_HANDOFF` | `1` | `CANCELLED` | `ACCEPTED` | `CANCELLED` | `NOT_ACTIVATED` |
| 3 | `CONTROLLER_HANDOFF` | `2` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |

Stop immediately after key leakage, a lifecycle failure, a
`HANDOFF_FRAME_TIMEOUT`, or uncertain evidence identity. Do not run later modes
or calibration after a safety failure.

## Calibration

Run calibration only after all three Netflix modes fail. Run each fixed mode
once against the first-party target. Calibration success is diagnostic and
does not satisfy M0.

| Mode | Result |
| --- | --- |
| `BASELINE_80_MS` | `CANCELLED` |
| `SYSTEM_TAP_TIMEOUT` | `CANCELLED` |
| `CONTROLLER_HANDOFF` | `CANCELLED` |
| Classification | `PROJECTOR_OR_GESTURE_DISPATCH` |

Use `APPLICATION_SPECIFIC_COMPATIBILITY` when at least one calibration attempt
activates the intended target. Use `PROJECTOR_OR_GESTURE_DISPATCH` when all
three calibration attempts fail to activate it. Use `NOT_RUN` for every
calibration field when a probe wins or safety stops execution first.

## Final Outcome

| Field | Recorded value |
| --- | --- |
| Outcome (`PROCEED_TO_M0_RETEST` or `STOP`) | `STOP` |
| Winning mode or `NONE` | `NONE` |
| Key leakage (`PASS` or `FAIL`) | `PASS` |
| Controller lifecycle (`PASS` or `FAIL`) | `PASS` |
| No ADB input (`PASS`) | `PASS` |
| Evidence integrity (`PASS`) | `PASS` |
| Stop reason (`COMPATIBILITY_EXHAUSTED`, `HANDOFF_FRAME_TIMEOUT`, `KEY_LEAKAGE`, `CONTROLLER_LIFECYCLE`, or `NONE`) | `COMPATIBILITY_EXHAUSTED` |

Only two consecutive `COMPLETED_ACTIVATED` results for one mode, complete
safety attestations, and verifier exit zero authorize a complete M0 rerun on
the same APK. M0.1 never authorizes M1.

Validate the completed report with:

```sh
sh scripts/verify-m0-1-report.sh docs/gates/m0-1-report.md
```
