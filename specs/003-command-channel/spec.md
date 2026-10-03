# Feature Specification: Command channel (SMS first), code sheet, policy and audit

**Feature Branch**: `feature/command-channel` (merged, PRs #3 and #8)

**Created**: 2026-10-01

**Status**: Core implemented and tested on the JVM; SMS receiver/sender, Keystore key and admin
screen written but **not run on a phone**

**Input**: Control the agent from a dumbphone by SMS, without trusting the SMS network, and in
a way that lets other contact channels be added later without changing the command language,
policy, plugins or engine.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a command from the dumbphone (Priority: P1)

The owner texts `WHATSAPP SEND 5511999: on my way #17-48291360` and gets back
`Sent to *******99`.

**Why this priority**: This is the product.

**Independent Test**: `ChannelTest` drives the router with envelopes and a fake plugin runner.

**Acceptance Scenarios**:

1. **Given** an allowed sender and an unused valid code, **When** the command arrives, **Then**
   the code is burned before execution, the skill runs and a short reply is sent.
2. **Given** the same SMS delivered twice, **Then** the second is dropped silently (code already
   used).
3. **Given** a reply longer than one SMS, **Then** it is paged; `MORE` returns the next page and
   `RESEND` the last reply.

### User Story 2 - Strangers and forgers get nothing (Priority: P1)

Anyone can text the number (it is also the WhatsApp number). Only the owner, with the sheet,
can make the agent act.

**Why this priority**: The sender number can be spoofed and the SIM can be swapped.

**Acceptance Scenarios**:

1. **Given** a sender not on the allowlist, **Then** the message is dropped before parsing, with
   no reply, and does not count toward lockout.
2. **Given** an allowed sender with a wrong code five times in a row, **Then** the agent locks for
   15 min, then 30 min, 1 h …; after 20 total failures only the phone can unlock it.
3. **Given** an invalid command, **Then** the reply gives no detail that helps an attacker.

### User Story 3 - Risky commands need a second step (Priority: P1)

Commands at risk level 5 return a challenge with what will be done and run only after
`OK <word> #<n>-<code>` with a second code.

**Acceptance Scenarios**:

1. **Given** a risk-5 command on SMS, **Then** the agent replies with a random confirmation word
   that expires in minutes; `CANCEL` drops it.
2. **Given** the same command in the on-device console (physical trust), **Then** no second step
   is needed.

### User Story 4 - Stop everything (Priority: P1)

`STOP` from any allowed sender disables remote commands immediately, without a code. Only the
phone can re-enable them.

### User Story 5 - Admin on the phone only (Priority: P1)

On the phone, behind the device credential, the owner installs plugins, manages allowed senders,
generates the code sheet (shown once), uses a local console, and reads the audit log.

### Edge Cases

- Phone numbers in different formats (`+55 11 …` vs `11 …`) match the allowlist.
- A multipart SMS is joined before parsing.
- WhatsApp verification SMS arriving on the same number are ignored by the parser.
- Fewer than ~15 codes left: the owner is warned.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Channel adapters MUST convert transport messages into a channel-neutral `Envelope`
  (channel, sender, body, received time, trust profile set by the adapter) and render structured
  replies back.
- **FR-002**: Each adapter MUST declare capabilities (max reply chars, confidential, sender
  authenticated, plain text, max reply parts) and a `TrustProfile` (`PHYSICAL`,
  `STRONG_REMOTE`, `WEAK_REMOTE`, `UNAUTHENTICATED`).
- **FR-003**: The router MUST drop non-allowlisted senders before parsing, silently.
- **FR-004**: The grammar MUST be T9-friendly and case/accent-insensitive:
  see [contracts/command-grammar.md](contracts/command-grammar.md).
- **FR-005**: Code `i` MUST be `HMAC-SHA256(K, sheet_id ‖ i)` truncated to 8 digits; `K` lives in
  the Android Keystore; the device stores only `K` and the used-index set.
- **FR-006**: A valid code MUST be burned before the command executes; reused codes are dropped
  silently; failures lock out with escalating durations and a total budget.
- **FR-007**: Policy MUST decide by risk × trust: deny unauthenticated risk > 0, deny risk above
  the profile maximum, require two-step confirmation for risk 5 outside `PHYSICAL`.
- **FR-008**: Replies MUST be plain text without accents on SMS, paged with `MORE`, and masked
  in the core when the channel is not confidential.
- **FR-009**: Every command MUST be recorded in a hash-chained audit log (channel, sender,
  command without code, auth decision, policy, result); the log never contains secrets.
- **FR-010**: Admin operations MUST be available only in the on-device admin screen.
- **FR-011**: One command runs at a time (the screen is a shared resource).
- **FR-012**: The SMS adapter MUST use `SMS_RECEIVED` and `SmsManager` without being the default
  SMS app.

### Key Entities

- **Envelope, ChannelCapabilities, TrustProfile**: see [data-model.md](data-model.md).
- **CodeSheet / AuthState**: key-derived codes, used indices, failure counters, lock state.
- **AuditLog**: append-only records, each with the previous record's hash.

## Success Criteria *(mandatory)*

- **SC-001**: No test path executes a command without a valid unused code (except `HELP`,
  `STOP`).
- **SC-002**: Non-allowlisted senders never cause a reply or a lockout.
- **SC-003**: Altering or removing any audit record is detected.
- **SC-004** *(device, open)*: SMS receive/send stays reliable for ≥ 72 h on the agent phone
  (Spike 3).
- **SC-005** *(device, open)*: From the dumbphone, send and read WhatsApp messages authenticated
  with the printed sheet.

## Assumptions

- Plain SMS (profile A) gives **no confidentiality**: the carrier can read commands and replies.
  Encryption is spec 011.
- A code captured in transit while the original is blocked can be used once; codes are not
  bound to command content because a human cannot compute that. Risk-5 confirmation and local
  limits bound this.
- Network channels, if ever, live in a companion app (constitution I).
