# Feature Specification: Nubank read-only banking

**Feature Branch**: `feature/nubank-read-only` (not started)

**Created**: 2026-10-03 (from the vision documents and Spike 4 of 2026-10-01)

**Status**: Draft — depends on 008 and on finishing Spike 4 on the agent phone

**Input**: From the dumbphone, ask for the Nubank balance and the last statement entries. No
money can move in this version.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Check the balance (Priority: P1)

`NU BALANCE #20-77120956` → `Saldo R$ 1.2xx` (masked by default).

**Acceptance Scenarios**:

1. **Given** the app logged out, **Then** the skill opens Nubank, types the device PIN into the
   system credential prompt (`device_credential_prompt`), reads the balance from the tree and
   replies masked.
2. **Given** a face check or device re-registration is requested, **Then** the skill stops with
   `E_NEEDS_OWNER`.

### User Story 2 - Read the statement (Priority: P1)

`NU STATEMENT 5 #21-…` → the last N entries: short description, time, signed amount (masked).

### User Story 3 - Refuse anything that moves money (Priority: P1)

The plugin is `category: financial`, `scope: read_only`. The base app refuses to click targets
whose text looks like a payment or transfer (Pix, transferir, pagar, and equivalents), and
rejects transfer-type commands.

### Edge Cases

- Server-driven UI changed: screen signals do not match ⇒ stop (`E_NOT_FOUND`), never guess.
- Nubank refuses to run with our accessibility service: banking is out of scope; report.
- Outside the owner's allowed hours: denied.

## Requirements *(mandatory)*

- **FR-001**: Risk 4 for every command; signed by a trusted key; no interrupt rules (007).
- **FR-002**: Tree-only: no screenshot, no OCR, no coordinate taps (`candidates: tree_only`).
- **FR-003**: Replies masked or rounded by default; statement limited to the last N entries,
  descriptions truncated, no account numbers. More detail only by an admin setting.
- **FR-004**: A screen-signal check before each skill (expected labels/elements) because the
  app version does not pin a server-driven UI.
- **FR-005**: Optional allowed-hours window for banking commands.
- **FR-006**: Payment/transfer-looking targets refused (defense in depth).

## Success Criteria *(mandatory)*

- **SC-001**: Spike 4 completed: our service accepted, PIN typed in the system prompt,
  re-authentication frequency known.
- **SC-002**: Balance and statement replies verified against the app on the agent phone.
- **SC-003**: Zero clicks on payment or transfer elements in tests and on the device.

## Assumptions

- The owner accepts the terms-of-service risk (O10) and logs in once on the agent phone.
- Transfers are a later, separate spec; their entry criteria are in [research.md](research.md).
