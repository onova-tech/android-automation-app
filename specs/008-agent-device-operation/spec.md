# Feature Specification: Unattended agent phone operation

**Feature Branch**: `feature/agent-device-operation` (not started)

**Created**: 2026-10-03 (from the vision documents of 2026-09-30/10-01)

**Status**: Draft — needs the agent phone (owner decision O1)

**Input**: The agent phone runs 24x7 with its screen off between jobs and a PIN lock (required
by Nubank). For each job it must wake, unlock with its stored PIN, act, and lock again; it must
type secrets without exposing them, use notifications instead of the UI where possible, and tell
the owner when a reboot left it locked.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a job on a locked, dark phone (Priority: P1)

A command arrives while the screen is off and locked. The agent wakes the screen, types the
device PIN on the keyguard, runs the skill, and locks the phone again.

**Why this priority**: Without it, no command works unattended.

**Independent Test**: Spike 6 on the agent phone: repeated off → wake → unlock → act → lock
cycles over days.

**Acceptance Scenarios**:

1. **Given** the phone locked with a PIN, **When** a job starts, **Then** it unlocks by clicking
   the labeled PIN keys and the job runs.
2. **Given** the keypad keys are not labeled in the tree, **Then** the job fails closed with
   `E_DEVICE` and the owner is told why.
3. **Given** a job ends (success or failure), **Then** the phone is locked again.

### User Story 2 - Type a secret without exposing it (Priority: P1)

A plugin declares a secret slot; the owner types the value on the phone at install. Skills use
it only through `type_secret` into a field, or on a keypad.

**Acceptance Scenarios**:

1. **Given** a skill that tries to put a secret into a variable, template, log, reply or URL,
   **Then** the plugin is rejected at load.
2. **Given** `type_secret` in `keypad` mode, **Then** digits are clicked on buttons resolved by
   label, and the value never appears in the trace or audit.
3. **Given** the plugin is uninstalled, **Then** its secrets are deleted.

### User Story 3 - Unlock the device credential prompt for an app (Priority: P1)

Nubank asks for the device credential through the system `BiometricPrompt`. A plugin granted
`device_credential_prompt` may type the device PIN there — and nothing else in system UI.

### User Story 4 - Reply without opening the app (Priority: P2)

Read new messages from notifications and reply through the notification's `RemoteInput`,
falling back to the UI flow.

### User Story 5 - Know when a reboot left the agent deaf (Priority: P2)

After a reboot, before the first unlock, a `directBootAware` receiver replies to allowed senders
"agent restarted, needs unlock".

### Edge Cases

- Power loss mid-job: the outcome is "unknown" and the job is never re-run automatically.
- A face check or biometric-only step: stop with `E_NEEDS_OWNER`; never bypass.
- The service killed by the OEM: watchdog and boot receiver restart it.

## Requirements *(mandatory)*

- **FR-001**: Actions `wake_screen`, `keep_awake`, `unlock` (wake + `type_secret` keypad on the
  keyguard), `lock_screen` (`GLOBAL_ACTION_LOCK_SCREEN`).
- **FR-002**: `type_secret { secret, target | keypad }` MUST be the only consumer of a secret;
  `keypad` fails closed when buttons have no readable labels.
- **FR-003**: Secrets MUST be stored encrypted under a non-exportable Android Keystore key,
  entered only in admin mode, never logged, and wiped at uninstall.
- **FR-004**: `device_credential_prompt` MUST allow acting only on the system credential prompt,
  only to type the device PIN.
- **FR-005**: Actions `read_notifications`, `wait_for_notification`, `reply_notification`
  (needs `notifications` capability).
- **FR-006**: Effectful steps MUST record "started" before acting and "completed"/"unknown"
  after; "unknown" is reported and never re-run.
- **FR-007**: A boot receiver, watchdog and a `directBootAware` "needs unlock" reply.
- **FR-008**: The device PIN is itself a secret owned by the base app (not by any plugin).

## Success Criteria *(mandatory)*

- **SC-001**: Spike 6: ≥ 72 h of wake/unlock/act/lock cycles without manual help.
- **SC-002**: Spike 5: notification replies work on WhatsApp on the agent phone, or are
  documented as unusable.
- **SC-003**: No secret value appears in any log, trace, audit record or reply (tests).
- **SC-004**: After a forced reboot, the owner receives the "needs unlock" SMS.

## Assumptions

- A PIN pad on the chosen phone exposes labeled keys (a pattern grid does not — Spike 4).
- The owner accepts that the PIN protecting the phone is stored on it; physical security and
  read-only banking bound that risk.
- Provisioning steps are in [quickstart.md](quickstart.md).
