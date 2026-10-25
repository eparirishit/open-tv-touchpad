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
