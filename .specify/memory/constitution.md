<!--
Sync Impact Report
- Version: 1.0.0 → 1.0.1 (PATCH: stack line updated for the Gradle 9 / AGP 9 / Kotlin 2 upgrade)
- Earlier: (template) → 1.0.0
- Ratified from the accepted vision documents and ADR-001…ADR-009 (2026-09-30 … 2026-10-01),
  migrated to Spec Kit on 2026-10-03. The old docs/ folder was removed; its content lives in
  this constitution and in specs/001…011.
- Templates: plan-template.md "Constitution Check" reads the gates in section "Quality gates".
-->

# Android Automation Agent Constitution

A dedicated Android phone (the **agent**) runs 24x7 and operates apps for its owner (WhatsApp,
Nubank, …) on commands received through contact channels — SMS from a dumbphone first. These
principles bind every spec, plan and change in this repository.

## Core Principles

### I. Self-contained on the device (NON-NEGOTIABLE)

Everything in the execution path runs on the agent phone: command parsing, authentication,
policy, plugin interpretation, element resolution and any ML inference. No cloud service, no
backend, no telemetry. The base app declares **no `INTERNET` permission**; a channel that needs
the network lives in a separate companion app behind local authenticated IPC. Model training
happens off-device; the phone only receives signed artifacts, installed by hand. Nothing
updates itself.

### II. Plugins are data; intelligence lives in the base app

Support for an app is added by installing a `.agp` plugin package: a zip of YAML files with no
code. A plugin composes **built-in actions** only; a missing capability is a base-app release,
never plugin code. The base app owns interpretation, resolution, authentication, policy,
verification, redaction and audit. Expressions are limited to templates and a fixed set of pure
filters, and every run is bounded (actions, steps, call depth, duration).

### III. The base app owns trust and policy

A plugin can **suggest** but never decide: it cannot lower a risk floor, raise a limit, add a
beneficiary, or classify itself out of the financial rules. Classification (financial, secret
holding) is computed by the base app from what the plugin operates. A plugin is blind to every
app it did not declare, and anything undeclared is denied. Capabilities are approved by the
owner at install; secrets are use-only slots consumed solely by `type_secret`.

### IV. Channels are untrusted; the sender is never a credential

Every remote channel is treated as hostile. An SMS sender number is at most a noise filter.
Every effectful command carries application-layer authentication (the printed one-time-code
sheet, burned on use). Policy decides by **command risk × channel trust**; admin operations
(install plugins, change policy, keys, codes, allowed senders) happen **only in person on the
device**. `STOP` works from every channel because it can only disable the agent. Replies never
carry complete sensitive data; masking happens in the core before any adapter sees the reply.

### V. Fail closed; verify every effect

When in doubt, stop and report — never "try anyway". The resolver refuses to guess
(`E_LOW_CONFIDENCE`), steps declare post-conditions (`expect`) that the runtime checks, and an
effect whose outcome is unknown is reported as such and never re-run automatically. ML models
only **choose among options we enumerate**; they never generate actions and never authorize an
irreversible action alone — exact values (amounts, recipients, numbers) are compared literally.

### VI. Deterministic first, ML must earn its place

Prefer structured surfaces (deeplinks, notification replies, providers) over screen automation,
and exact hints over ranking. Any model (Laya, OCR) is added only behind a heuristic baseline
and kept only if it beats that baseline measurably on a golden set.

### VII. Testable on the JVM, provable by replay

Device-independent logic lives in the pure-JVM `:core` module behind `DevicePort`. Every
feature ships with JVM tests; plugins ship with replay tests (`agp test`) that run the real
engine against recorded, **redacted** screens in virtual time. Behavior that can only be proven
on a phone is marked as such in the spec and is not claimed as validated until it has run on
the agent phone.

## Security and privacy constraints

- Secrets and keys live in the Android Keystore (non-exportable). They never reach variables,
  logs, traces, replies or URLs. Uninstalling a plugin wipes its secrets.
- The audit log is local and hash-chained; it never contains secrets or one-time codes.
- Screen dumps and fixtures that leave a device are redacted first; raw dumps of personal apps
  are deleted after use. Private signing keys (`*.key`) are never committed.
- Plugin packages are signed like APKs (ECDSA P-256). Unsigned packages install only after a
  warning; financial or secret-holding plugins need a key the owner trusts; updates keep the
  signer.
- We never bypass biometrics, face checks, or any protection of a target app or of Android.
- Banking starts **read-only** (balance, statement). Money movement needs a separate spec, the
  controls in `specs/009-nubank-read-only/research.md`, and an independent security review.

## Development workflow

- Specs follow Spec Kit: `/speckit-specify` → `/speckit-clarify` (optional) → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`. A behavior change starts by updating the feature's
  `spec.md`; decisions and rejected alternatives go in its `research.md`.
- Documentation and code are written in **English**.
- Work happens on `feature/<name>` branches; a finished branch becomes a pull request against
  `main`. Commits follow Conventional Commits.
- Stack: Kotlin 2 (JVM 17), Gradle 9 with AGP 9 (built-in Kotlin), minSdk 26 / target 34
  (compileSdk follows the libraries), SnakeYAML 2.x (safe loading, no duplicate keys),
  coroutines, Jetpack Compose for on-device UI, JUnit + MockK. Dependabot proposes updates; a
  `targetSdk` change is a behavior change and needs its own spec.
- `./gradlew :core:test` and `./gradlew :app:assembleDebug` must pass before a PR is opened.

## Quality gates

A plan passes the Constitution Check when it can answer yes to each:

1. Does it add no network access to the base app and no off-device dependency at run time? (I)
2. Is every new capability a base-app action, with plugins remaining data only? (II)
3. Can no plugin field lower risk, bypass classification, or reach an undeclared app? (III)
4. Is every remote effect authenticated, risk-rated, and audited; are admin operations local? (IV)
5. Are failure modes closed, with post-conditions and no automatic re-run of unknown effects? (V)
6. Does any ML or heuristic addition have a measured baseline to beat? (VI)
7. Is the logic in `:core` with JVM tests, and are device-only claims marked unvalidated? (VII)

Violations are allowed only with a row in the plan's *Complexity Tracking* table.

## Governance

This constitution supersedes other practice documents. Amendments are made by pull request
that updates this file, bumps the version (MAJOR: a principle removed or redefined; MINOR: a
principle or section added; PATCH: wording), and updates any affected spec. The owner approves
amendments. Reviews check changes against the quality gates above.

**Version**: 1.0.1 | **Ratified**: 2026-10-01 | **Last Amended**: 2026-10-03
