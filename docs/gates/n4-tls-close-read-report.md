# N4 TLS close-read fixture — reproduced and corrected

Date2026-10-27. This is a test-only correction; production TLS and trust checks
are unchanged. Investigation used an immutable `b956180` source copy, isolated
from the concurrent service extraction, on the existing API31 emulator with the
AndroidOpenSSL provider. No fixture was installed on the Nord or projector.

## Evidence and cause

The unchanged TLS class reports15 passing tests, and the unchanged complete
native runner reports42 tests (41passes and one opt-in skip). To capture the intermittent assertion,
temporary androidTest-only instrumentation recorded the client/server return or
exception class and monotonic close/read completion timestamps. It retained the
original IOException-only assertion, blocked-read100ms check and2s completion
bound; no trust or runtime code was modified.

The instrumented test APK SHA256 was
`74d04818f252fd59bb74b60ae01493dc80236b8ecc344d126b592da393949543`.
The app APK SHA256 was the exact accepted0.20 baseline:
`31c7bb4a0434bf1c25a251a59a90fd69f7642e7fb4d6e5dca895c262e401943c`.
On that same artifact/provider, the isolated close-read test passed with
SocketException. Four complete native runs produced two passes with
SocketException and two failures with the client returning exactlyEOF(-1).
The server observedEOF(-1) in both captured failing runs. Client EOF arrived
11.4045ms and9.768584ms after close began, before close returned at16.028ms and
16.436834ms respectively. The read had remained pending for the preceding100ms.
These captures establish the actual returned value; EOF is no longer a hypothesis.

The failure is the fixture requiring IOException even when native close correctly
unblocks the read with EOF. Returned data, premature completion or a stuck worker
were not observed in these captures and are not acceptable substitutes.

## Minimal correction and verification

The actual close-read test now requiresEOF(-1) if read returns normally, or
accepts IOException; SocketTimeoutException explicitly fails. The existing
100ms pending-read assertion,2s Future completion bound, server completion and
executor-termination checks remain. Temporary timing/provider logs are absent
from the committed test. No production socket, certificate, key or validation
path changed.

Corrected test APK SHA256:
`58cd16a6eae83554b16b80f1ff56dabfa983b8162a267c4bf927fc0fb234d7f6`.
With the same baseline app, the corrected isolated test reports1 pass (0.328s)
and the complete native runner reports42 tests:41passes, one skip (10.784s). The opt-in projector
handshake diagnostic was not enabled; this is emulator fixture evidence, not a
new projector/Nord handshake claim. Integration verification after ownership
extraction is recorded separately in the N4 readiness report.
