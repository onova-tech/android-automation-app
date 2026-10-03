# Feature Specification: Execution engine and workflow language

**Feature Branch**: `feature/engine-foundation` (merged, PR #2)

**Created**: 2026-09-30

**Status**: Implemented (JVM); not validated on a phone

**Input**: Replace the POC's linear YAML workflows and structural selectors with a bounded
workflow language that can drive real apps (WhatsApp, Telegram, a bank) and verify its effects.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Drive an app with verified steps (Priority: P1)

A plugin author describes how to do something in an app (open a chat, type, send) as steps
over built-in actions. The engine runs them on the phone and confirms each effect happened.

**Why this priority**: Every command the agent executes is a run of this engine.

**Independent Test**: Run a skill against a simulated device (`ScreenDevice`) and check the
interaction log, the returned value and the error code.

**Acceptance Scenarios**:

1. **Given** a chat screen with a send button, **When** a step clicks `send_button` with
   `expect: { exists: { text: "${text}" } }`, **Then** the step succeeds only if the sent text
   is visible afterwards; otherwise the run fails with `E_VERIFY_FAILED`.
2. **Given** a skill with `params`, **When** it runs with arguments, **Then** `${name|filter}`
   templates resolve them, and an undefined variable fails with `E_EXPR`.

### User Story 2 - Survive small UI changes without guessing (Priority: P1)

Targets are described by intent, role, exact hints and region. When an id or label changes,
the engine still finds the element if it is clearly the best match — and refuses otherwise.

**Why this priority**: Apps change constantly; a wrong click can be irreversible.

**Independent Test**: Resolver tests over `uiautomator` XML fixtures with renamed ids.

**Acceptance Scenarios**:

1. **Given** a target whose exact hint matches one node of the expected role, **Then** that
   node is used without ranking.
2. **Given** no exact match and two similarly scored candidates, **Then** the step fails with
   `E_LOW_CONFIDENCE` rather than clicking either.

### User Story 3 - Handle alternatives and errors inside a skill (Priority: P2)

Skills try alternatives (`first_that_works`), branch (`if`), recover (`try`/`on_error`), reuse
flows (`call`) and return a short result for the reply.

**Independent Test**: Interpreter tests per construct.

**Acceptance Scenarios**:

1. **Given** `first_that_works: [A, B]` where A fails, **Then** B runs and the run continues.
2. **Given** `try` with `on_error`, **When** a step fails, **Then** `${error.code}` is available
   in the handler.

### Edge Cases

- Infinite or very long runs: global limits stop the run with `E_BUDGET`.
- `STOP` or the Stop button during a run: the run ends with `E_CANCELLED`.
- Accessibility service off or screen locked: `E_DEVICE`.
- Recursion in flows: rejected at load; call depth bounded at run time.
- The old `selector:` key from the POC is rejected with a clear message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST run actions only through a `DevicePort`, so the same engine runs on
  Android and against recorded screens.
- **FR-002**: The engine MUST provide the actions `launch_app`, `open_url`, `click`, `type`,
  `read_text`, `read_list`, `scroll`, `scroll_until`, `wait`, `wait_for`, `back`, `home`, `log`.
  Every action MUST accept `retries`, `retry_delay`, `timeout`, `on_failure` (`abort`,
  `continue`, `retry(n, ms)`) and an optional `expect` post-condition.
- **FR-003**: The language MUST support `params`, `set`, templates `${var|filter}` with the fixed
  filters `urlencode`, `upper`, `lower`, `trim`, `mask`, and the constructs `sequence`, `if`/
  `else`, `first_that_works`, `try`/`on_error`, `call` (flows, `with`, `into`), `assert`,
  `return`.
- **FR-004**: Conditions MUST support `exists`, `not_exists`, `screen_is`, `equals`, `contains`,
  `is_set`, `not`, `all`, `any`.
- **FR-005**: Targets MUST support `intent`, `role`, `hints` (`resource_id`, `text`,
  `content_description`, `class_name`), `region` and `min_confidence`; exact hints filtered by
  role are tried first, then a heuristic ranker that refuses ambiguous matches.
- **FR-006**: Every run MUST be bounded by `RunLimits` (max actions 500, max nodes 5 000, max call
  depth 8, max duration 10 min) regardless of plugin content.
- **FR-007**: Runs MUST end with a structured result: status (`SUCCEEDED`, `FAILED`,
  `CANCELLED`), an `ErrorCode`, a message, the returned value and a step trace.
- **FR-008**: The engine MUST be cancellable at any step.
- **FR-009**: There MUST be no arbitrary expression evaluation; templates only read variables.

### Key Entities

- **Program / Skill / Flow**: parsed AST of steps with parameters and local scope.
- **Target**: description of a UI element (intent, role, hints, region, threshold).
- **UiNode**: immutable snapshot of an accessibility node (also parsed from `uiautomator` XML).
- **RunResult / ErrorCode**: outcome of a run, see [contracts/workflow-language.md](contracts/workflow-language.md).

## Success Criteria *(mandatory)*

- **SC-001**: All engine, DSL and resolver tests pass on the JVM (`./gradlew :core:test`).
- **SC-002**: No test fixture leads the resolver to click a node other than the expected one;
  ambiguous fixtures fail with `E_LOW_CONFIDENCE`.
- **SC-003**: A run can never exceed its limits (covered by tests per limit).
- **SC-004** *(device, open)*: A WhatsApp send skill runs on the agent phone with its
  post-condition verified.

## Assumptions

- The accessibility tree is the primary source of screen information; OCR is out of scope here
  (see 010).
- Structured surfaces (deeplinks, notification replies) are preferred over UI steps where an
  app offers them; notification actions are specified in 008.
- Actions still to come (`wake_screen`, `unlock`, `type_secret`, `reply_notification`, …) are
  specified in 008; Laya-based resolution in 010.
