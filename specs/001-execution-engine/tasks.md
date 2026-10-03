# Tasks: Execution engine and workflow language

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/workflow-language.md](contracts/workflow-language.md)

## Phase 1: Foundation

- [X] T001 Pure-JVM `:core` module; move engine behind `DevicePort` in `core/.../engine/DevicePort.kt`
- [X] T002 `UiNode` snapshots and `UiXml` parser for `uiautomator` dumps in `core/.../ui/`
- [X] T003 Structured `ErrorCode`, `RunResult`, `RunLimits` in `core/.../engine/ErrorCode.kt`, `core/.../dsl/RunResult.kt`
- [X] T004 Remove the POC workflows, editor, v1 selectors and StateManager; reject `selector:`

## Phase 2: User Story 1 — verified steps (P1)

- [X] T005 [US1] DSL parser and AST (`params`, `set`, templates, filters) in `core/.../dsl/`
- [X] T006 [US1] Interpreter with `expect` post-conditions and `assert`
- [X] T007 [US1] Actions `launch_app`, `open_url`, `click`, `type`, `read_text`, `read_list`, `scroll`, `scroll_until`, `wait`, `wait_for`, `back`, `home`, `log` in `core/.../engine/actions/`
- [X] T008 [US1] Global run limits and cancellation
- [X] T009 [US1] Tests: `ExecutionEngineTest`, `InterpreterTest`, `TemplatesTest`, `ListActionsTest`

## Phase 3: User Story 2 — targets (P1)

- [X] T010 [US2] `Target` (intent, role, hints, region, min_confidence) and `TargetResolver`
- [X] T011 [US2] Exact hints filtered by role; ranker with margin that returns `E_LOW_CONFIDENCE`
- [X] T012 [US2] Tests: `TargetResolverTest`, `TargetActionsTest`

## Phase 4: User Story 3 — control flow (P2)

- [X] T013 [US3] `sequence`, `if`, `first_that_works`, `try`/`on_error`, `call` with flows, `return`
- [X] T014 [US3] Conditions `exists`, `not_exists`, `screen_is`, `equals`, `contains`, `is_set`, `not`, `all`, `any`
- [X] T015 [US3] Tests: `DslParserTest`, `ErrorHandlerTest`

## Phase 5: Device validation (needs the agent phone)

- [ ] T016 Run the engine through `AndroidDevicePort` on the agent phone; compare live snapshots with `uiautomator` dumps
- [ ] T017 Measure SMS-to-reply time for a simple command (SC target < 30 s)
- [ ] T018 Add `foreach` and `repeat_until` when a plugin needs them (mandatory limits)
