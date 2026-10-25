# Unified Remote Hub Proof Implementation Plan

**Goal:** a disposable local Mac window combining TV remote keys and Netflix pointer input.
**Architecture:** two bounded device connections, one local UI; no browser server.
**Tech stack:** Python 3.13/Tk, androidtvremote2 0.3.2, installed scrcpy 5.0.1.
**Spec:** ../specs/2026-10-24-unified-remote-hub-proof.md

## Global constraints

Preserve M0/M0.1 evidence. Work on hub-remote-proof. No network listener, root,
firmware changes, unsolicited credential output, or release-runtime changes.

## Review focus

Certificate changes must block keys; failed reconnect must not replay input;
UI flooding must remain bounded; disconnect must release input; partial server
startup must remove only this proof's process/socket resources.

## Tasks

1. [x] Inspect existing evidence, installed tools, and target connectivity.
2. [x] Build `experiments/hub_remote/tv_remote.py`: async pairing and pinned
   Remote v2 connection; allowlisted keys and Netflix shortcut; temporary
   credentials and bounded calls. Verify pairing validation and wrong-peer
   rejection with local TLS fixtures before live control.
3. [x] Prove scrcpy control-only viability, then build
   `experiments/hub_remote/pointer.py`: persistent local control socket,
   version fence, relative movement and press/release lifecycle. Test wire
   fixtures, bounds, failure cleanup and no replay using local socket pairs.
4. [x] Compose `experiments/hub_remote/app.py`: local Tk controls, masked
   pairing code, visible status, bounded input scheduling, Stop and reconnect.
   Document launch and manual hardware checks in README.md in the same folder.
5. [x] Finish scoped local Mac functional acceptance: corrected Netflix shortcut
   live retest passed. The 55-test suite and independent reviews passed; hardware
   observations and source snapshots are recorded in `docs/gates/h0-report.md`,
   with progress, capabilities and architecture updated. Hardware results are
   recorded separately from local tests.

## Execution decisions

H0 PASS is scoped to the local Mac functional proof.
UHID profile selection passed by explicit user observation: "Yes, it works".
The earlier shortcut PASS is preserved but contradicted by six remote-service
ActivityNotFoundException entries for `market://launch?id=com.netflix.mediaclient`.
The implemented/reviewed fix sends `nflx://www.netflix.com/browse`; main freshly
reran 55 tests with ResourceWarning treated as errors: PASS. DEFAULT resolution
to NetflixComLaunchActivity and local TLS payload tests do not establish live
Remote v2 success. Separately, the reopened GUI's live retest passed on snapshot
6be01a1d…: "Netflix opens and both stay connected" after Home → Netflix, a
20-second wait and pointer movement. All enumerated H0 functional checks have
evidence. Abrupt hardware link/power loss and active-drag interruption remain
untested production reliability limitations; passing local STOP tests does not
certify them. Next is new hub trust/PWA design and its gate, not network implementation.
Direct SDK profile activation passed separately. UHID visibility, title selection,
both wheel axes and drag/release passed; the user confirmed ordinary controls,
Stop/reconnect and playback/pause/resume. Lifecycle and ADB-recovery fixes passed
55 local tests and independent review. See the report for source hashes and
separate live observations. Do not infer one backend's results from another,
or certify hardware behavior solely from local tests.

The user's go-ahead authorizes the scoped proof. Use a sub-agent for the remote
adapter while the coordinator owns device checks, scrcpy and composition.
Do not ask for reapproval of this same proof; request only a live pairing code
or readiness observation when technically necessary. Connection is a runtime
precondition: build local verification while the projector is unavailable.
