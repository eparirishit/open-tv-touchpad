# N3 native platform foundation — provider prerequisite passed

Contract: [approved N3 design](../superpowers/specs/2026-10-25-nord-tv-controls-design.md).
Branch `nord-tv-controls`; foundation source `81a1ebe` (wire `691f5f2`, TLS `9aec0b2`).
This report does not certify N3a, N3b or owner hardware acceptance.

## Evidence and limits

- Wire/pairing: seven pure tests pass; independent synthetic RSA/code digest vector.
- Consolidated offline Gradle unit suite:126 tests,0 failures/errors/skips.
- Enrollment:18 tests, including malformed/truncated records, key mismatch,
  verified write/readback failure, ordered Forget and locator-only change.
- Debug/test assembly passes. Lint:0 errors/28 warnings, including two custom
  trust-manager warnings; no suppression. Installed0.7 baseline had26 warnings.
- API31 emulator:14/14 instrumentation tests pass (12 TLS/identity, two UI).
  The fixture test APK was removed afterward.
- Root cause: Conscrypt performs prehashed/preencoded RSA through its native
  private-key Cipher adapter using SIGN purpose. The key needs DIGEST_NONE and
  no-padding authorization in addition to SHA256/384/512 and PKCS1/PSS signing.
  ENCRYPT/DECRYPT purposes remain absent; native key material remains opaque.
- Native generation uses RSA-PSS certificates. The initial PKCS1-only algorithm
  filter was too narrow. Platform AlgorithmParameters/PSSParameterSpec now requires
  SHA256/384/512, matching MGF1 digest, digest-length salt and trailer1. Native
  signature verification, validity, exact-leaf pin and native trust checks remain.
- Sockets explicitly enable only TLS1.2/1.3. Positive fixture uses TLS1.2;
  TLS1.3 interoperability is not separately established by this fixture.
- Initial wrong/expired generic handshake failures were inconclusive. Stronger
  assertions now verify certificate-policy causes with a passing positive baseline.
  Child rejection uses a genuine native-trusted CA/child chain against the pin
  policy; it is not a live child-certificate handshake.
- Independent store review reports no concrete defects. Scoped native re-review
  resolves both original blockers with no remaining actionable findings. Review
  covers the complete pending foundation and connected callers; runtime integration
  is absent and remains deferred until the Nord gate.
- Reviewed same-signature version8 platform diagnostic installed on the stopped
  authorized Nord AC2001/API31. The phone passed12/12 TvTlsTest methods in32.276s.
  No real-TV network operation or input occurred. Bluetooth enrollment was byte-
  identical before/after; fixture aliases cleaned and only instrumentation APK removed.
  The app APK remains installed with the unchanged0.7 presentation/controls.
- The initial unavailable-device report was a sandbox ADB access error, not a
  disconnected Nord. Permitted read-only inspection found the existing authorized
  connection and stopped probe. No new pairing/address or daemon restart was needed.

Native provider prerequisites pass. The bounded transport/cancellation integration
and owner-observed combined TV behavior remain pending; this is not full N3 acceptance.
A native write result is not an observed projector action. TLS1.3 negotiation and
live child-certificate handshakes remain outside this fixture's observed coverage.

## Artifact identity

Consolidated build artifacts (same signature as preserved0.7):

- App SHA256: `9f51bf1078ced9a224228d5e765a9c7f46851468db13b06904b7f7ae9cfb3be6`.
- Instrumentation SHA256: `316027c2076c65c6a9079ddb6c8debe6f2f551e4f25d5680a6126948d3be30d9`.
- Emulator run output SHA256: `248bb436edf43f389674bfb0aa70b73773ced6e6694818207448a2b9905b15cc`.

Native source SHA256 reviewed and checked against current files:

- TvIdentity: `071c4031465b5bf566d8265f397bfe539cb9cae39dc2476e3ef355eb3a45567b`.
- TvTls: `0ef36132b672e1f1269cc5a93b6123ced6a196b39426f9879f8117b71dac9969`.
- TvTlsTest: `dcfa1508c11fec42167aaabbed0ff0bc233c658d3f27cd16063de86c069e0e4b`.

Nord TLS fixture output SHA256: `d2ce32edad6752ff6b9a1ca29be1a176ea985f460fcf6ee22582b127a0ba2291`.
