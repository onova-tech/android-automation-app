# Research: Execution engine and workflow language

Former ADR-001 to ADR-005 and the "DSL v2" part of the action catalog.

## Decision: Kotlin for all code (former ADR-001)

- **Rationale**: Android standard; null safety, data classes and coroutines suit an async
  accessibility service and structured cancellation; full Java interop.
- **Alternatives**: Java (verbose, no coroutines).

## Decision: SnakeYAML 2.x as the YAML parser (former ADR-002)

- **Rationale**: Mature, small (~250 KB), works on Android; 2.x fixes CVE-2017-18640. Loaded with
  safe options: no custom tags, no duplicate keys, single document.
- **Alternatives**: Jackson YAML (larger); a custom parser (cost, edge-case bugs).
- **Note**: YAML 1.1 reads a bare `on:` key as boolean `true`; formats avoid it (replay uses
  `after:`).

## Decision: Native `AccessibilityService` for runtime interaction (former ADR-003)

- **Rationale**: Designed for runtime use inside an app; full tree access; `performAction` for
  click, set text and scroll; global actions for back and home; gestures available.
- **Alternatives**: UI Automator — a test instrumentation framework, not a runtime library.
  It is still used on a PC (`uiautomator dump`) to capture fixtures.

## Decision: Jetpack Compose for on-device UI (former ADR-004)

- **Rationale**: The admin screen is small and state-driven; Compose with Material 3 keeps it in
  Kotlin. Hilt and Navigation from the original ADR were not needed and are not used.

## Decision: Device-agnostic engine behind `DevicePort`

- **Rationale**: Moving the engine into the pure-JVM `:core` module makes every behavior testable
  without a phone and enables replay in virtual time (004). The app supplies
  `AndroidDevicePort`; tests and `agp test` supply `ScreenDevice`.
- **Alternatives**: Keep the engine in `:app` with mocks — slower tests, no replay.

## Decision: Targets with intent + hints; a ranker that refuses to guess

- **Rationale**: Exact hints are deterministic and fast; when they fail, a cheap ranker (role,
  text similarity, region) handles small drifts. It requires a clear margin over the runner-up
  and otherwise returns `E_LOW_CONFIDENCE`. This ranker is the **baseline** any learned model
  must beat (010). A unique hint match must also have the expected role (a bug found by tests).
- **Alternatives**: The POC's structural selectors only (break on any change); clicking the
  top-ranked node without a margin (unsafe).

## Decision: Bounded control flow instead of a general language

- **Rationale**: Plugins are data (constitution II). Control flow covers the needs of real apps
  (alternatives, branches, recovery, reuse), while global limits make every run terminate.
  `foreach` and `repeat_until` are deferred until a plugin needs them.
- **Alternatives**: Embedding a scripting language (Lua/JS) — larger attack surface; plugins could
  read secrets or bypass policy.

## Decision: Remove the POC paths (former ADR-005, superseded)

- **Rationale**: The POC (plain YAML workflows, editor screen, v1 selectors, StateManager) was a
  feasibility test. The owner approved removing it without backward compatibility; only `.agp`
  plugins are processed.

## Decision: Structured error codes

- **Rationale**: Channels need short, stable reasons; `E_VERIFY_FAILED` on an effectful action
  means "unknown whether it happened" and must be reported that way. Codes defined so far are
  in [contracts/workflow-language.md](contracts/workflow-language.md); `E_AUTH`,
  `E_RISK_DENIED` and `E_APP_VERSION` are produced outside the engine (003, 008).
