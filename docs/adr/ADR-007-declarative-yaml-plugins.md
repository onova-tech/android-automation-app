# ADR-007: Declarative YAML Plugins, Intelligence in the Base App

| Field | Value |
|-------|-------|
| **ADR** | ADR-007 |
| **Status** | **Proposed** (conditional on Spike 7) |
| **Date** | 2026-09-30 |
| **Context** | Target vision in `docs/vision/` — adding support for new apps (WhatsApp, Telegram, a bank) |
| **Deciders** | TBD |

## Context

The runtime must control many different apps, and the owner wants to add support for a new app by adding a file. Secrets (such as a bank password) are entered on the agent device when a plugin is installed. The runtime is controlled over SMS and may perform financial actions, so anything that extends it is part of the security boundary.

## Decision

1. **A plugin is one declarative YAML file**, validated against a versioned schema. It contains no code, no scripts and no unbounded loops.
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
| Plugins as code (Kotlin/JS/Lua modules) | Larger attack surface; a bad plugin could read secrets or bypass policy; harder to audit |
| Everything hard-coded in the base app | No extensibility; every app change needs a release |
| Plugins with their own risk limits and beneficiaries | A tampered plugin could raise its own limits |
| Remote plugin store / auto-update | Violates self-contained operation and widens the supply-chain risk |

## Consequences

**Positive:** small, auditable plugins; the trust boundary is one interpreter; a plugin can be reviewed by reading it; offline replay tests are possible; security controls cannot be overridden by data.

**Negative / costs:** the action catalog must grow to cover real apps (every gap is a base-app change); a YAML DSL with control flow needs careful design to stay bounded; plugin authors depend on the resolver's quality rather than custom code.

**Risks:** the DSL creeping toward a general-purpose language (mitigated by the fixed expression filters and mandatory bounds); an owner approving a plugin without reading it (mitigated by a short, explicit install summary and an extra confirmation for financial plugins).

## Validation

- **Spike 7:** write a real WhatsApp plugin using only built-in actions; every gap is logged as a candidate action.
- Reject this ADR if the DSL cannot express the target flows without embedding code.

See `docs/vision/plugins.md` for the manifest, lifecycle and threat model.
