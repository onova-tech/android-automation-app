# ADR-007: Declarative Plugin Packages (YAML in a Zip), Intelligence in the Base App

| Field | Value |
|-------|-------|
| **ADR** | ADR-007 |
| **Status** | **Accepted** (2026-10-01). Revised the same day: single YAML file → zip package of YAML files. Revisit if Spike 7 fails |
| **Date** | 2026-09-30 |
| **Context** | Target vision in `docs/vision/` — adding support for new apps (WhatsApp, Telegram, a bank) |
| **Deciders** | Owner |

## Context

The runtime must control many different apps, and the owner wants to add support for a new app by installing a plugin, without changing the base app. Real apps need large plugins, so the format must stay readable and allow reuse. Secrets (such as a bank password) are entered on the agent device when a plugin is installed. The runtime is controlled over SMS and may perform financial actions, so anything that extends it is part of the security boundary.

## Decision

1. **A plugin is a package**: a zip (`.agp`) of small YAML files (manifest, commands, skills, flows, targets, screens, interrupt rules, per-language text, replay tests), validated against a versioned schema. It contains **no code**: any file type other than YAML, fixture JSON, Markdown and the generated `PACKAGE.lock` is rejected. No scripts and no unbounded loops.
1a. **Reuse:** named, parameterized **flows** inside a plugin, and **libraries** shared across plugins. Libraries hold no capabilities, secrets or commands and run with the calling plugin's grants. In v1 libraries are **vendored** into each package at build time, so the approved package hash covers everything that runs.
1b. **Integrity:** a build tool produces reproducible packages and a `PACKAGE.lock` with every file's hash; the device unpacks in memory with limits (paths, symlinks, size, file count, compression ratio) and checks the lock before validating.
2. **All intelligence lives in the base app:** DSL interpreter, element resolver (cache, ranker, Laya, OCR), SMS parsing, authentication, policy, confirmation dialogue, post-condition verification, idempotency, audit and redaction.
3. A plugin can only compose **built-in actions**. A missing capability means a new base-app release, not plugin code.
4. **Capability declaration:** the plugin lists the packages, deeplinks, notification access, contacts access, secrets and SMS replies it needs. The owner approves them **in admin mode on the device**, and anything undeclared is denied.
5. **Secrets are use-only slots.** The plugin declares a slot; the owner types the value on the device; the only consumer is `type_secret`. A secret cannot reach a variable, log, SMS or URL.
6. **The base app owns policy.** Risk floors, limits, beneficiaries, allowed hours and aliases are local configuration changed only in admin mode. A plugin may suggest a category but can only raise a skill's risk level, never lower it.
7. **Lifecycle:** install, update and uninstall need physical presence. Updates that add capabilities or secrets, or change the package, require re-approval. Uninstall wipes secrets. Financial skills are disabled when the target app version is not in `tested_versions`.
8. **Bounds:** the base app enforces global maximum steps, loop iterations, runtime and reply counts per job.

## Alternatives Considered

| Alternative | Why not |
|-------------|---------|
| A single YAML file per plugin (the first version of this ADR) | Grows too large for real apps; no reuse except copy-paste |
| Shared libraries installed once, resolved at runtime | A library update would change already-approved plugins; deferred until needed |
| Plugins as code (Kotlin/JS/Lua modules) | Larger attack surface; a bad plugin could read secrets or bypass policy; harder to audit |
| Everything hard-coded in the base app | No extensibility; every app change needs a release |
| Plugins with their own risk limits and beneficiaries | A tampered plugin could raise its own limits |
| Remote plugin store / auto-update | Violates self-contained operation and widens the supply-chain risk |

## Consequences

**Positive:** small files that stay readable as plugins grow; reuse of flows and of common Android handling through libraries; reproducible, hash-pinned packages; the trust boundary is one interpreter; a plugin can be reviewed by reading it; offline replay tests are possible; security controls cannot be overridden by data.

**Negative / costs:** a build tool (`agp`) and a safe unpacker are needed; a library fix means rebuilding every plugin that vendors it; the action catalog must grow to cover real apps (every gap is a base-app change); a YAML DSL with control flow needs careful design to stay bounded; plugin authors depend on the resolver's quality rather than custom code.

**Risks:** the DSL creeping toward a general-purpose language (mitigated by the fixed expression filters and mandatory bounds); an owner approving a plugin without reading it (mitigated by a short, explicit install summary and an extra confirmation for financial plugins).

## Validation

- **Spike 7:** write a real WhatsApp plugin using only built-in actions; every gap is logged as a candidate action.
- Reject this ADR if the DSL cannot express the target flows without embedding code.

See `docs/vision/plugins.md` (section 4) for the package format, reuse rules, build tool, lifecycle and threat model.
