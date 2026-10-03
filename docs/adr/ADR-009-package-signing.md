# ADR-009: Signing Plugin Packages

| Field | Value |
|-------|-------|
| **ADR** | ADR-009 |
| **Status** | **Accepted** (2026-10-01). Amended the same day: §9 classification by the base app |
| **Date** | 2026-10-01 |
| **Context** | Plugin packages (ADR-007) are installed on the agent phone, which can hold a bank password |
| **Deciders** | Owner |

## Context

A `.agp` package is integrity-checked by its `PACKAGE.lock` (a hash per file; the lock's hash is the package hash the owner approves). Nothing says *who* produced it, and nothing stops a different package with the same plugin id from replacing an installed one. The owner wants developers to sign packages before publishing them, as with Android APKs, while still allowing unsigned packages with a clear warning.

The agent phone has no Internet access in our design, so there is no certificate authority or online revocation.

## Decision

1. **What is signed:** the package hash, i.e. the SHA-256 of `PACKAGE.lock`, which already covers every file. The signed message is `agp-package-v1\n<package hash>` (domain-separated). The signature lives in `PACKAGE.sig` at the package root. That file is not listed in the lock, so a package can be signed after a reproducible build without changing its hash.
2. **Algorithm:** ECDSA on P-256 with SHA-256 (`SHA256withECDSA`). It is supported natively on Android and the JVM without extra libraries. `PACKAGE.sig` records the algorithm, so others can be added later.
3. **Identity = key, not person.** A signature proves that the package comes from whoever holds the key. A key is shown as its **fingerprint**, the SHA-256 of the public key.
4. **Install states:**

| State | Result |
|-------|--------|
| Signed by a key the owner trusts | Installs; shows "Verified developer: <name>" |
| Signed by an unknown key | Installs after a warning that shows the fingerprint; the owner can trust the key, with a name, at that moment |
| Unsigned | Installs after a prominent warning that **the package's identity could not be verified** and an extra confirmation |
| Signature present but invalid | **Rejected** (the package was altered or the signature is broken) |

5. **Financial plugins must be signed by a trusted key.** An unsigned or unknown-signer financial plugin is blocked.
6. **Signer continuity on updates (as on Android):** the phone records the key that signed the installed version of each plugin id. An update signed by a different key, or unsigned when the installed one was signed, is **blocked**. Replacing the signer requires uninstalling, which deletes the plugin's secrets. An unsigned installed plugin may be updated by a signed package; from then on that key is required.
7. **Trusted keys** are managed only in admin mode on the phone (name + fingerprint). There is no revocation beyond removing a key from that list.
8. **Tooling:** `agp keygen` creates a key pair. The private key is encrypted with a passphrase (PBKDF2 + AES-GCM) and never leaves the developer's machine. `agp sign` adds `PACKAGE.sig`, `agp build --key` builds and signs in one step, and `agp verify` and `agp inspect` show the signature state and fingerprint.

9. **Classification belongs to the base app, not the plugin** (amendment). A plugin's own `category` cannot be trusted, since an author could declare `utility` to avoid the financial rules. Two separate rules decide:
   - **Rule 1 — financial:** a plugin is financial if it declares so **or** if any app in its `ui_automation`/`read_screen` is on the phone's list of financial apps. The list ships with confirmed package names only (`com.nu.production`) and is edited by the owner in admin mode. A financial plugin needs a trusted signer, cannot have interrupt rules, and gets the financial risk floor on every command: 5, or 4 only if it *declared* itself financial and read-only. A plugin caught by the list gets 5 because its own claims are not trusted.
   - **Rule 2 — secrets:** a plugin that stores secrets or types the device PIN needs a trusted signer, without becoming financial.
   - Checking the declared apps is enough because the runtime guard makes a plugin blind to every app it did not declare.
   - Android has no reliable "finance" attribute: `ApplicationInfo.category` has none, and the store category needs the Internet. So the list is local.
   - Installed plugins are reclassified whenever the list or the trusted keys change. One that no longer qualifies stops loading and is reported in the admin screen.

## Alternatives Considered

| Alternative | Why not |
|-------------|---------|
| No signatures (hash approval only) | No origin, and no protection against a different package taking over an installed plugin's id |
| X.509 certificates / a CA | Needs infrastructure and online checks; the phone is offline |
| Ed25519 | Simpler and modern, but native support on the Android baseline is uncertain; it would need an extra library |
| Reject all unsigned packages | The owner wants to allow them with a warning (useful for personal plugins) |
| Trusting the plugin's declared category | A plugin could lie to bypass the financial rules (see §9) |
| Detecting financial apps from Android attributes | No reliable attribute exists offline |
| Signing each file instead of the lock | The lock already binds every file; one signature over its hash is equivalent and simpler |

## Consequences

**Positive:**
- Origin and integrity are checked together.
- Takeover of an installed plugin by another developer's package is blocked.
- Financial plugins require a key the owner trusts.
- Builds stay reproducible: signing does not change the package hash.

**Negative / costs:**
- Developers must keep their private key safe. Losing it means users must uninstall and reinstall to change signers, and that deletes the plugin's secrets.
- Trust decisions are manual.
- There is no key rotation in v1.

**Not a safety guarantee:** a signature does not make a plugin safe. The capability review at install and the runtime guard still limit what any plugin can do.

See `docs/vision/plugins.md` (sections 10–11).
