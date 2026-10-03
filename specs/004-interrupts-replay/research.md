# Research: Interrupt rules and replay

## Decision: Declarative rules checked before screen actions

- **Rationale**: Unexpected dialogs are the most common cause of failures in UI automation.
  Checking before each screen action, with a single retry after a rule fires, handles dialogs
  that appear at any time without wrapping every step in `try`.
- **Alternatives**: Per-skill `try` blocks (repetitive, easy to forget); a background watcher
  that clicks at any time (races with the skill; harder to bound).

## Decision: Restricted rule steps, no variables, no rules in financial plugins

- **Rationale**: A rule fires on screen content, which an attacker can influence (a chat message
  with a button-like text). Limiting rules to dismissal actions, hiding skill variables, and
  banning them where money is involved keeps them from becoming an attack path.

## Decision: Replay with the real engine in virtual time

- **Rationale**: Plugins are the part that changes most; testing them must not need a phone.
  Using the real interpreter, resolver and guard (not mocks) is what made replays find real
  bugs. Virtual time keeps tests fast and deterministic.
- **Alternatives**: Mock-based unit tests per plugin (miss integration bugs); emulator tests
  (slow, need real app installs and accounts).
