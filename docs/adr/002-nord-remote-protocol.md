# ADR002: Nord Remote v2 wire dependencies

Status: selected for approved N3; provider/device suitability remains gated.

Use native Android TLS/Keystore and Java standard cryptography. Add only
`com.google.protobuf:protobuf-javalite:4.36.2` at runtime (no transitive dependencies
in its published POM). Generate checked-in Java lite messages with Maven
`com.google.protobuf:protoc:4.36.2`, reporting compiler36.2. A generation helper
checks the exact macOS ARM compiler SHA-256; other host compiler hashes require
recorded verification before use. No third-party remote client, certificate encoder,
crypto provider or Gradle protobuf plugin is introduced.

The reviewed Mac adapter remains the behavioral reference; its upstream Python
library is not embedded. Wire schemas come from androidtvremote2 v0.3.2, commit
`3f30c275615fc5eb11b4a2da4fe33a02cbb4fd66`. Local changes only set Java package/class
names. Preserve upstream comments and Apache-2.0 license. Protobuf uses BSD-3-Clause;
license text is retained alongside upstream license and source/artifact hash manifest
under `experiments/hid_probe/third_party/`. Generated schemas expose more messages
than N3; only the closed application command API may reach the network.

On2026-10-25 OSV querybatch for both exact Maven coordinates returned empty results
(no listed advisories), not proof of vulnerability absence. Artifacts were fetched
from Maven Central and schemas/license from upstream HTTPS with verification enabled.
Python's unavailable local CA bundle was not bypassed; native macOS curl verified TLS.

Generation: `python3 experiments/hid_probe/scripts/generate_remote.py /path/to/protoc`.
Pinning/hash records: `experiments/hid_probe/third_party/remote-dependencies.json`.
Native key material remains opaque; test fixture private keys are generated only
under test aliases on the instrumented device. No runtime key export fallback.
