# Research: plugin packages

Former ADR-007 and `docs/vision/plugins.md`.

## Decision: Plugins are declarative packages (zip of YAML), intelligence in the base app

- **Rationale**: The owner wants new apps without base-app changes. Anything that extends a
  phone controlled remotely and holding bank credentials is part of the security boundary; data
  can be validated and reviewed, code cannot. One interpreter is the only trust boundary.
- **Alternatives**:
  - A single YAML file per plugin (first version) — too large for real apps, no reuse.
  - Plugins as code (Kotlin/JS/Lua) — could read secrets or bypass policy; hard to audit.
  - Everything hard-coded — no extensibility.
  - Plugins carrying their own limits or beneficiaries — a tampered plugin raises its limits.
  - A remote plugin store or auto-update — violates self-containment, widens supply chain.
- **Costs**: a build tool and a safe unpacker; each missing capability is a base-app release.

## Decision: Responsibility split

| Concern | Base app | Plugin |
|---------|----------|--------|
| Interpretation, expressions, limits | ✅ | |
| Element resolution | ✅ | Supplies intent and hints |
| Channel parsing, authentication, replay protection | ✅ | Declares verbs and arguments |
| Risk, limits, beneficiaries, hours | ✅ (owner config) | Suggests a category; can only raise risk |
| Secret storage and typing | ✅ (Keystore) | Declares slots; uses `type_secret` |
| Verification, idempotency, audit | ✅ | Declares `expect` |
| App-specific flow | | ✅ |

## Decision: Libraries are vendored at build time (v1)

- **Rationale**: The hash the owner approves covers every line that runs; a library update can
  never silently change an approved plugin.
- **Alternatives**: Shared libraries installed once — saves rebuilds, but every update would
  need re-approval of all dependents. Deferred.

## Decision: Safe in-memory unpacker with limits, then lock check, then schema

- **Rationale**: Zip slip, zip bombs and hidden duplicates are classic package attacks; nothing
  is extracted to disk.

## Decision: Blind plugins instead of a foreground check only

- **Rationale**: Replay tests found that conditions could read the screen of unapproved apps.
  The guard now filters snapshots, so a plugin sees an empty screen outside its apps; launches
  and links outside the lists end the run with an uncatchable `E_CAPABILITY`.

## Threats and mitigations

| Threat | Mitigation |
|--------|-----------|
| Plugin exfiltrates a secret | Use-only slots; `type_secret` is the only consumer (008) |
| Plugin operates an undeclared app | Capability guard on every action and condition |
| Plugin lowers its risk | Base-app floor; plugin can only raise it |
| Plugin lies about its category | Base-app classification (007) |
| Tampered package replaces an installed one | Admin-mode install, hash shown, re-approval, signer continuity (006) |
| Malicious zip (traversal, symlinks, bombs, duplicates) | Rejected at unpack |
| File changed after build | Lock check |
| Library escalates privileges | Libraries hold no capabilities; run with caller's grants |
| Infinite loops / SMS floods | Run limits; reply and rate limits (003) |
| Owner approves without reading | Short summary; extra confirmation for financial plugins |

## Spike 7 result

The WhatsApp example (send, read chat; in the plugins repository) is expressible with built-in actions
only; replay tests pass. Its hints are guesses until checked with `agp targets` on real dumps.
