# Research: Plugin package signing

Former ADR-009 (sections 1–8).

## Decision: Sign the package hash, not each file

- **Rationale**: The lock already binds every file; one signature over its hash is equivalent
  and keeps builds reproducible (sign after build). Domain separation (`agp-package-v1`) stops a
  signature from being reused for another purpose.

## Decision: ECDSA P-256 / SHA-256

- **Rationale**: Native on Android and the JVM, no extra library. The algorithm is recorded so
  others can be added.
- **Alternatives**: Ed25519 (native support uncertain on the baseline; would need a library);
  X.509 certificates and a CA (infrastructure and online checks on an offline phone).

## Decision: Identity is a key, shown as a fingerprint

- **Rationale**: Without a CA there is no verified person, only "whoever holds this key". The
  owner compares fingerprints out of band and names trusted keys.

## Decision: Allow unsigned packages with a warning

- **Rationale**: The owner wants personal plugins to stay easy. Financial and secret-holding
  plugins are the exception (007).
- **Alternatives**: Reject all unsigned packages (owner declined).

## Decision: Signer continuity, as on Android

- **Rationale**: Prevents another package with the same id from taking over an installed plugin
  (and its secrets). Losing a key means uninstall + reinstall, which deletes secrets — an
  accepted cost.

## Consequences

Developers must protect their private keys; trust decisions are manual; no key rotation in v1.
