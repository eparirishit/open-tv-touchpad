# TV certificate trust-anchor compatibility

Status: independently reviewed proposal, awaiting owner decision. No runtime
policy change applied. Review found no additional impersonation counterexample
within the accepted threat model if the retained checks pass, but this explicitly
relaxes certificate self-consistency validation and requires the proofs below.

## Confirmed blocker

The owner reproduces first TV pairing failure on0.9 and0.10. Both Remote v2 TCP
ports are reachable from the Nord and Mac. Version0.10 reports PAIRING_TLS.
An explicitly requested TLS-only native diagnostic using the existing client
identity fails with SSLHandshakeException caused by certificate exceptions and
the fixed message category INVALID PEER SIGNATURE. This is the explicit
`leaf.verify(leaf.getPublicKey())` check in TvTls, before any pairing frames.
Independent Mac OpenSSL verification with `-check_ss_sig` reports certificate
signature failure for the service at the owner-supplied projector address.
Its reported RSA2048/SHA256 certificate validity covers the observation date.
The native diagnostic changed no enrollment, sent no input/pairing messages and
its test package was removed. The0.10 application remains stopped.

These observations locate the immediate blocker; they do not authenticate the
server, establish why its certificate is malformed, or prove later pairing and
control compatibility. No certificate bytes, addresses or exception text are
included in this record.

## Proposed decision

Treat the peer certificate as an explicitly enrolled leaf trust anchor. Remove
only the additional requirement that a certificate whose issuer and subject
names match must verify its own signature. A self-signature supplies no
independent identity provenance; the accepted protocol obtains its trust from
the on-TV code bound to the actual TLS session keys and then the exact DER pin.
TLS still must prove possession of the corresponding private key.
During bootstrap the peer is an untrusted candidate, not an enrolled anchor;
authorization still follows code confirmation and the pinned control handshake.

The owning change is solely the explicit self-signature verification in
`TvTls.checkPeer`; do not catch and ignore TLS validation errors or add fallback
sockets. Retain bounded native X.509 parsing, validity, RSA key-size and strong
signature-algorithm policy, platform TLS1.2/1.3, no-input pairing-only bootstrap,
the actual-session-key code check and secret acknowledgment, exact DER pinning
before control traffic, and native trust-manager validation. A child certificate,
changed certificate, expired certificate, wrong pairing code, or missing TLS
private-key proof must still fail. No automatic enrollment or reconnect retry.
Native client identity generation and its self-signature checks are unchanged.

This deliberately permits a peer trust-anchor certificate with an invalid
self-signature. It does not restore that signature or claim normal CA-chain
provenance. The existing short-code pairing limitations remain; the owner must
read the code from the intended projector. No broader certificate exception is
authorized by this decision.
If the native trust manager rejects this anchor, stop; this proposal does not
authorize bypassing native validation. A strong signature OID alone does not
make an invalid certificate signature valid.

## Required proof after approval

1. A public fixture with intact RSA key but invalid certificate self-signature
   reproduces the current rejection. Use maintained certificate tooling.
2. Under the proposed policy, native pairing TLS and exact-pinned TLS with that
   anchor succeed only with possession of its private key; no control frame is
   sent before enrollment checks.
3. Existing wrong/changed/child/expired/weak-certificate, code-binding,
   pairing/control mismatch, Stop and cleanup regressions continue to pass.
4. Independent review of the complete change, then a stopped-only same-signature
   update preserving both enrollment records.
5. Owner on-TV code pairing followed by the pending combined hardware checks.
   No N3 PASS or successful pairing claim before that evidence.

## Alternative

Keep the current self-signature requirement. TV-control pairing remains blocked
until the TV service presents a certificate that satisfies it. Do not reset the
TV service, clear app data, change firmware or bypass TLS without separate
authorization. Previously verified Bluetooth behavior does not establish N3.
