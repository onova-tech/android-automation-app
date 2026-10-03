# Feature Specification: Interrupt rules and replay tests

**Feature Branch**: `feature/interrupts-replay` (merged)

**Created**: 2026-10-01

**Status**: Implemented (JVM)

**Input**: Apps show dialogs that were not in the script (permissions, "what's new", rating
prompts). Plugins need a bounded way to dismiss them, and plugin authors need to test skills
against recorded screens without a phone.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A dialog in the way does not break a skill (Priority: P1)

A plugin declares rules in `interrupts.yaml` ("if a button `Agora não` exists, click it").
Before screen actions, the engine checks the rules and clears the dialog.

**Independent Test**: `InterruptRulesTest`; replay `send_with_dialog`.

**Acceptance Scenarios**:

1. **Given** a "what's new" dialog over the chat, **When** the skill clicks `send_button`,
   **Then** the rule dismisses the dialog first and the click succeeds.
2. **Given** a click that fails with `E_NOT_FOUND` and a rule that then fires, **Then** the click
   is retried once.
3. **Given** a rule that fires more than `max_per_run` times, **Then** it stops firing.

### User Story 2 - Test a plugin without a phone (Priority: P1)

A plugin author writes tests in `tests/*.yaml` (skill, arguments, starting screen, screen
transitions, expected result and interaction log) and runs `agp test`. The real engine,
resolver and capability guard run against recorded screens in virtual time.

**Independent Test**: `ReplayTest`; `agp test plugins/whatsapp` (plugins repository) runs six cases.

**Acceptance Scenarios**:

1. **Given** a test where the sent message never appears, **Then** the run is expected to fail
   with `E_VERIFY_FAILED` and the test passes only if it does.
2. **Given** a test with `language: en`, **Then** the plugin's English texts are used.

### Edge Cases

- Rules chaining (a dialog after a dialog): at most three rules per check.
- A failing rule step: does not fail the skill.
- A rule trying to type, open links, launch apps or call flows: rejected at load.
- Financial plugins: interrupt rules are not allowed (they must never click on financial or
  security screens).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Before `click`, `type`, `read_text`, `read_list`, `wait_for`, `scroll`,
  `scroll_until`, the first rule whose `when` holds MUST run; up to three rules may chain.
- **FR-002**: When such an action fails with `E_NOT_FOUND`, `E_LOW_CONFIDENCE` or `E_TIMEOUT`
  and a rule then fires, the action MUST be retried once.
- **FR-003**: Rule steps MUST be limited to `click`, `back`, `wait`, `wait_for`, `log`,
  `sequence`, `if`, `first_that_works`.
- **FR-004**: Rules MUST NOT see skill variables; each fires at most `max_per_run` (1–10,
  default 2) times per run; they run under the plugin's capabilities.
- **FR-005**: Replay MUST use the same interpreter, actions, resolver and guard as the phone, in
  virtual time, with screens from `fixtures/` (`uiautomator` XML) and transitions
  `after: <interaction>, label: …, screen: <fixture>`.
- **FR-006**: A replay test MUST check status, error code, returned value and (optionally) the
  exact interaction log.

## Success Criteria *(mandatory)*

- **SC-001**: All six WhatsApp replay tests pass (sent, not confirmed, chat does not open,
  dialog in the way, read chat, phone in English with a changed id).
- **SC-002**: Replays found and fixed two real bugs (capability leak in conditions; exact hints
  ignoring role) — replay is part of every plugin change.

## Assumptions

- Fixtures are synthetic or redacted dumps; real dumps of personal apps are never committed
  unredacted.
- A Laya-based screen classifier for interruptions belongs to 010.
