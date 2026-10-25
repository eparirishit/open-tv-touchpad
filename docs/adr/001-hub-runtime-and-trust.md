# ADR001: browser-only hub runtime and trust

Status: accepted for H1B implementation, 2026-10-24. Actual browser/Keychain
behavior remains gated; acceptance is not a certification claim.

Use Python3.13, cryptography50.0.2, aiohttp3.14.5 and keyring25.7.0. aiohttp owns
HTTP/WebSocket parsing; Python asyncio/OpenSSL owns TLS. keyring supplies the
native macOS backend without building our own Security.framework binding.
This adds17 locked distributions including the existing crypto stack. Plain
browser JS and Node's test runner require no UI framework/build tool.

A per-install ephemeral root signs a constrained intermediate. The intermediate
and leaf keys live in native Keychain. This is at-rest protection, not hardware
non-exportability or isolation from same-user software. Runtime TLS loading
requires short-lived0600 key files; no secure-erasure claim. The chain is scoped
to one reserved RFC1918 IPv4 and restricted DNS namespace, not a port/network.
Browser path constraints and actual phone fingerprint ceremonies must pass H1B.

## Dependency evidence

Public PyPI wheels downloaded before installation, metadata licenses inspected,
and SHA256 locks written in hub/requirements.lock. OSV querybatch on2026-10-24
returned no listed advisories for any of the17 exact versions. This is a current
advisory check, not a source security audit or promise of no vulnerabilities.
Installed offline with --require-hashes. Lock targets Python3.13/macOS arm64;
other platform wheels need a separately reviewed lock. H0 adapters are unchanged.

|Distribution|Version|License metadata|
|---|---|---|
|aiohappyeyeballs|2.7.1|PSF-2.0|
|aiohttp|3.14.5|Apache-2.0 AND MIT|
|aiosignal|1.4.0|Apache 2.0|
|attrs|26.1.0|MIT|
|cffi|2.1.1|MIT-0|
|cryptography|50.0.2|Apache-2.0 OR BSD-3-Clause|
|frozenlist|1.8.0|Apache-2.0|
|idna|3.20|BSD-3-Clause|
|jaraco.classes|3.4.0|License :: OSI Approved :: MIT License|
|jaraco.context|6.1.2|MIT|
|jaraco.functools|4.6.0|MIT|
|keyring|25.7.0|MIT|
|more-itertools|11.2.1|MIT|
|multidict|7.2.1|Apache-2.0|
|propcache|0.5.4|Apache-2.0|
|pycparser|3.12|BSD-3-Clause|
|yarl|1.25.1|Apache-2.0|

## Resource admission

The listener must cap accepted sockets including TLS handshakes, not merely
HTTP handlers. Use asyncio's standard accepted-socket TLS API with an owned
32-slot budget; verify saturation/header timeout on loopback before LAN binding.
No custom TLS, ASN.1 or certificate validator. Failure of this check blocks H1B.

## Projector boundary

No device imports or backend in H1B. Protected projector transport remains H1A
NOT PASSED; user explicitly declined the plaintext beta path.
