# Feature-branch publication history — 2026-10-25

The owner requested pushing all accumulated work and authorized squashing commits
where useful. The75 unpublished commits were consolidated into eight milestone
commits. Published history through `023e445f4193d1d2566e93503872473b51402300` is unchanged.
The final source tree is byte-for-byte identical to the original tip
`71c2e6eba1b852a4cb04066a9d6c10c0d97d5c05`; this document is added afterward.
Original history is retained only in local branch
`backup/hub-remote-proof-before-publication`, which is not part of the push.
No tags, binary artifacts or ignored local state are published.

Each new commit uses the exact final tree of its listed original milestone.
Intermediate implementations are folded into that milestone, not silently
reverted. Original source hashes in gate reports remain historical build
identities; they are not rewritten to suggest the APK was built from a new
commit. An original intermediate commit is not necessarily identical to its
milestone endpoint. Use the retained local history for exact intermediate source.

| Milestone | Published commit | Original endpoint |
| --- | --- | --- |
| add bounded Android gesture diagnostics | `117042f8ddb7875c1a68beba9141dbbb54402a90` | `307a0ab41938a51a29e94e7a6ca0d938f89e2d47` |
| preserve unified Mac remote proof | `feb17ae7b93b4f122ef5e52b4f11d8ef67a0fbe4` | `ee25da53f882d8ecc3cca90678513b1402a1cd45` |
| add protected local browser hub | `fba39bd4c846d2d2e8e832d50cf567ffa7aafb43` | `5b3453c352c1a8e90b4c61af7697618d331b08bc` |
| add foreground Nord Bluetooth HID probe | `a2c70f7da2e6acf3e723b21c656fc20e25471d1b` | `6afcd8d4e329e915d826c6151a875903da914dab` |
| remember verified projector enrollment | `1e5c4d3871a47028a2094ac2493b0d57dbe104dd` | `34b7d363b1f0bf38e0f5b5ca3ff7775941e82ebd` |
| add bounded Nord reconnect diagnostics | `6ec361123435e1b5c42853d490ae4283dc2f8844` | `f8d7dc2f7c2a9631fc5153f728882348f942ff77` |
| add verified Nord touchpad controls | `f00d779659665a42f5b74ed97189e1bd9cf6b5e0` | `1eb217e6e0479946490a3bc7adaa914e1c414d68` |
| add verified Nord scrolling controls | `c45291109f07241871ed49afcaf15404800432ed` | `71c2e6eba1b852a4cb04066a9d6c10c0d97d5c05` |

## Verification and disposition

Pre-publication suites passed:103 hub Python tests,55 Mac proof Python tests,
36 browser Node tests,54 Android app unit tests and101 Nord unit tests. M0.1
report-parser and release-isolation regression scripts also passed. The latest
Nord build/review and owner-observed N2 acceptance remain recorded separately.
History consolidation changes no runtime source or test result.

The Codex CLI review covered the complete final feature change from main's
merge-base `6aad501` through original tip `71c2e6e`, including connected callers
and accepted specification exceptions. Verdict: no actionable in-scope defects.
It independently passed51 Python tests,36 Node tests,12 verifier fixtures and
recorded M0.1 STOP validation. Main separately ran the broader suites above.
Reviewer did not repeat native trust/browser/hardware checks or promote gates;
those remain limited to the existing recorded evidence. The full review log is
local only; the new publication-history document receives a scoped audit.

Privacy/artifact review found no credentials, private device addresses, pairing
material or prohibited binaries. An actual workstation SDK path existed in two
intermediate enrollment-plan commits and was already portable at the final tip.
Those intermediate trees are omitted by this authorized squash; the original
backup remains local. Final scoped review checked all eight publication trees and this document:
no blocking privacy/artifact issue remains. The local backup is explicitly
excluded from publication.
Two pre-existing Markdown hard-break lines in the M0.1 design trigger
`git diff --check`; these are deliberate formatting, not code defects, and are
retained. No merge into main, product release or blocked-gate promotion follows
from publishing this feature branch.

## Nord PR history consolidation — 2026-10-25

The owner explicitly requested squashing and force-pushing PR #7 after its
initial publication. The20 recent Nord commits after `f5cee1c` are consolidated
into seven milestone commits, followed by this history note. The shared ancestry
through `f5cee1c`, main, PR #6 and the remote `hub-remote-proof` branch are
unchanged. The complete pre-note source tree is identical to reviewed tip
`953db3022e8dda645028be36dcd19b728cd717d7`; this note is the only final file change.

Original history remains in local branch
`backup/nord-tv-controls-before-squash-20261025`, excluded from publication.
Every new milestone uses its original endpoint tree without editing code or
reverting intermediate work. Gate-report source hashes remain historical build
identities; they are not claims that installed artifacts came from rewritten
commits. The earlier publication mapping above remains a historical record.

| Milestone | Consolidated commit | Original endpoint |
| --- | --- | --- |
| add verified Nord remote interface | `7a30b17c3a5991a8298512dfa0e6b290c6ee65db` | `b0d0829c3760b35a62f3c1131f826c6b31b4f5c6` |
| define approved Nord TV controls contract | `6bd730641a56c34dd2676841226ad74ad8feb3fd` | `bc17c6f456ec0750da72c6b66e4cce8c7dbb4fc2` |
| add verified TV pairing and TLS foundation | `3b9bfc4d10e7821f7ac184ee6b98324355c64657` | `eb109149209eee3332a99aad6ee065b6979d5fef` |
| combine bounded TV and Bluetooth controls | `4a276ea9e10ef383d4dfffead3cc6a92dfdcca54` | `f66cc939f03bd4bae900aa2ce51ed4a70f11898e` |
| diagnose TV connection and certificate failures | `4e43e304c457a0c63b1b029354ca47f4ba9999db` | `105ec4aa3f5c0f8b348d94b9dd9646ee381a11b8` |
| accept code-bound TV certificate anchors | `6db01f60e6f7263bf36bae498e613070bd81e7cf` | `78a2f1c5468e25ec2cbdb06f93d751e85c8d197d` |
| complete TV readiness and record hardware checks | `78651dce5de6015981fa0d416109a50621277e04` | `953db3022e8dda645028be36dcd19b728cd717d7` |

Verification uses exact Git tree equality for every consolidated endpoint and
for the full pre-note tree. The existing complete-change Codex review at
`953db30` found no actionable findings. The166 Nord unit tests, build/lint results
and APK hash remain applicable because their inputs are unchanged. This history
operation adds no hardware evidence and does not promote N3 or any other gate.
Publication uses an explicit force-with-lease for the observed old feature tip;
no other remote branch is part of the push.
