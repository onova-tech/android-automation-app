# Feature Specification: Plugin package signing

**Feature Branch**: `feature/package-signing` (merged, PR #8)

**Created**: 2026-10-01

**Status**: Implemented (JVM); trusted-keys UI written, not run on a phone

**Input**: Developers sign `.agp` packages like APKs. Unsigned packages can still be installed,
but the phone warns that their identity could not be verified. Financial plugins require a
signature from a key the owner trusts.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A developer signs a package (Priority: P1)

The developer creates a key once (`agp keygen`, private key encrypted with a passphrase), builds
with `--key` or signs an existing package (`agp sign`), and shares the key fingerprint.

**Independent Test**: `SigningTest`: signing does not change the package hash; re-signing works.

**Acceptance Scenarios**:

1. **Given** a reproducible build, **When** it is signed, **Then** the package hash is unchanged
   and `agp verify` prints the signer's fingerprint.
2. **Given** a signed package where one file is edited and the lock regenerated, **Then** loading
   fails with "signature does not match".

### User Story 2 - The owner sees who made a package (Priority: P1)

At install, the phone shows one of: *Verified developer: <name>*; an unknown key with its
fingerprint (which the owner can trust, with a name, on the spot); a prominent *identity could
not be verified* warning with an extra confirmation (unsigned); or a rejection (invalid
signature).

**Acceptance Scenarios**:

1. **Given** an unsigned package, **Then** install is allowed after the warning.
2. **Given** a signature whose embedded public key was swapped, **Then** it is rejected.
3. **Given** a malformed `PACKAGE.sig`, **Then** it is rejected, never treated as unsigned.

### User Story 3 - Nobody takes over an installed plugin (Priority: P1)

The phone records the signer of each installed plugin. An update from another key, or an
unsigned update of a signed plugin, is blocked. Changing signer requires uninstalling (which
deletes the plugin's secrets).

**Acceptance Scenarios**:

1. **Given** plugin `whatsapp` installed signed by key A, **When** a package signed by key B
   arrives, **Then** it is blocked.
2. **Given** an unsigned installed plugin, **When** a signed update arrives, **Then** it is
   allowed and that key is required from then on.

### User Story 4 - Sensitive plugins need a trusted key (Priority: P1)

Financial and secret-holding plugins (classified by 007) install only when signed by a key in
the owner's trusted list.

## Requirements *(mandatory)*

- **FR-001**: The signed message MUST be `agp-package-v1\n<package hash>`; the signature lives in
  `PACKAGE.sig`, outside the lock, so signing does not change the hash.
- **FR-002**: Algorithm ECDSA P-256 with SHA-256; `PACKAGE.sig` records format version,
  algorithm, public key and signature.
- **FR-003**: A key is identified by its fingerprint: SHA-256 of the public key, uppercase hex in
  16 groups of four.
- **FR-004**: Install states MUST follow User Story 2; an invalid or malformed signature MUST be
  rejected.
- **FR-005**: Signer continuity MUST be enforced on updates and re-checked on every load from
  storage.
- **FR-006**: Trusted keys (name + fingerprint) MUST be managed only in admin mode; removing a
  key reloads plugins and stops those that no longer qualify.
- **FR-007**: `agp keygen` (PBKDF2 + AES-GCM encrypted private key, passphrase from
  `AGP_KEY_PASSWORD` or the terminal), `sign`, `build --key`, `verify`, `fingerprint`; `inspect`
  shows the signature state.
- **FR-008**: Private keys (`*.key`) MUST never be committed.

## Success Criteria *(mandatory)*

- **SC-001**: All `SigningTest` cases pass (hash stable, tampering detected, swapped key rejected,
  malformed rejected, continuity enforced).
- **SC-002**: No path installs a financial plugin without a trusted signer.

## Assumptions

- No CA, no online revocation (the phone is offline). Revocation = removing the key from the
  trusted list. No key rotation in v1.
- A signature proves origin, not safety; the capability review and runtime guard still apply.
