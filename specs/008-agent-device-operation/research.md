# Research: Unattended agent phone operation

## Decision: Separate agent phone with a PIN lock (D1, D9)

- **Context**: Spike 4 showed Nubank logs in only with the device credential or biometrics, so
  "no lock" is impossible; the owner's S20 FE is a daily phone and will not hold automation
  secrets.
- **Decision**: A dedicated phone with a **PIN** (the pattern grid is a single node with no
  cells; PIN pads usually expose labeled keys). The agent types the PIN from a stored secret.
- **Consequences**: After every reboot a human must unlock once; the PIN protecting the phone is
  stored on it; physical location and read-only banking are the real limits.

## Decision: Control-surface hierarchy

| # | Surface | Robustness |
|---|---------|------------|
| 1 | Intent / deeplink (`https://wa.me/<phone>?text=…`) | High |
| 2 | Notification action (`RemoteInput`) | High |
| 3 | Content providers / system APIs (contacts, SMS) | High |
| 4 | Notification reading | High |
| 5 | UI automation, exact hints | Medium |
| 6 | UI automation, semantic resolution (010) | Medium |
| 7 | UI automation on OCR (010; never for Nubank) | Low–medium |

## Decision: `type_secret` as the only secret consumer, two modes

- `field`: `ACTION_SET_TEXT` into a resolved field.
- `keypad`: click digit buttons resolved by label (PIN pads, custom bank keypads); fail closed if
  unlabeled.
- **Alternatives**: clipboard paste (leaks to clipboard history); coordinate taps (unverifiable).

## Decision: Screen off between jobs

AMOLED burn-in rules out a 24x7 screen; UI automation needs the screen on, so the agent wakes
it per job and re-locks after.

## Planned actions (from the action catalog)

| Priority | Actions |
|----------|---------|
| P0 | `wake_screen`, `keep_awake`, `unlock`, `type_secret`, `read_notifications`, `wait_for_notification`, `reply_notification`, `lookup_contact`, `require_confirmation` |
| P1 | `lock_screen`, `long_click`, `swipe`, `press_enter`, `paste`, `select_option`, `toggle`, `wait_for_gone`, `wait_for_idle`, `foreach`, `repeat_until` |
| P2 | `tap_xy`, `hide_keyboard`, `screenshot`, `notify_local`, `call` |

## What we never automate

Biometric or face checks; third-party login/2FA outside a plugin that declared it; installing or
uninstalling apps; changing device security settings (accessibility, permissions, Play Protect).

## Non-functional targets (to validate)

| Topic | Target |
|-------|--------|
| SMS to reply, simple command | < 30 s |
| Availability | Days without intervention; self-restart |
| Privacy | No `INTERNET` permission in the base app |
