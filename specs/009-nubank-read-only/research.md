# Research: Nubank read-only banking

## Spike 4 — partial results (2026-10-01)

**Device**: owner's Galaxy S20 FE 4G (SM-G780G), Android 13, One UI 5.1, Snapdragon 865, patch
2024-09-01. **App**: `com.nu.production` 10.6.59. **Method**: `uiautomator dump` over wireless
adb (Tailscale) while the owner navigated; dumps redacted on the server before reading (digits →
`9`, letters → `a`, except short labels on clickable nodes); raw XML deleted; no screenshots;
wireless debugging turned off afterwards.

`uiautomator` uses the system `UiAutomation`, not a third-party accessibility service, so a pass
here does not prove our service is accepted.

| Question | Result |
|----------|--------|
| Balance exposed in the tree? | ✅ In `content-desc` of buttons (`Saldo no … de R$ 99,99`) |
| Statement exposed? | ✅ One node per entry: description · `HH:MM · type` · amount (`+` on credits); day headers separate |
| Controls labeled? | ✅ `Voltar`, `Ajuda`, `Mais opções`, `Menu`, `Meus cartões`, `Esconder saldo.` |
| Identifiers | ⚠️ 3 of 42 nodes have `resource-id`; `text` empty; Flutter semantics in `content-desc` |
| Dump refused? | ✅ No |
| Login | ⚠️ System `BiometricPrompt` (Samsung `com.samsung.android.biometrics.app.setting`) |
| Pattern entry | ❌ Grid is one node with no cells |
| Our service accepted? | ❓ Needs the agent phone |
| PIN typed in the prompt? | ❓ Needs the agent phone |

**Consequences**: read path is possible from the tree alone; a second phone with a PIN lock is
required (008); plugins need the narrow `device_credential_prompt` capability; server-driven UI
makes screen-signal checks mandatory.

**Still to test on the agent phone**: our service enabled (warning, refusal, logout, hidden
content?); labeled PIN keys in the prompt; re-authentication frequency and device
registration/face check; same tree through our service; statement scrolling.

## Known about Nubank (public sources)

- Flutter + server-driven UI: screens and labels change without an app release; few ids.
- 4-digit transaction password; two-factor for sensitive access; face check with liveness for
  high-risk operations (not automatable — stop with `E_NEEDS_OWNER`).
- **OCR is not possible** (owner report: screens cannot be captured) ⇒ tree-only, no fallback.
- Pix day/night limits set by the customer (useful for a later transfer phase).
- Possible fallback to evaluate: a notification ledger (purchases, received Pix) answering
  `STATEMENT`; no live balance.
- Automation may violate bank terms and trigger anti-fraud; test with low volume.

## Entry criteria for a later transfer spec (not in v1)

Enforced locally, so they hold even if SMS authentication is defeated:

1. Beneficiary allowlist set in admin mode; new beneficiaries wait (e.g. 24 h).
2. Limits per operation, per day, count per day; allowed hours.
3. Two-step confirmation on data re-read from the bank's confirmation screen.
4. Literal comparison of amount and recipient on screen; mismatch aborts before the final button.
5. Deterministic anchor on the final click (no model decides alone).
6. Validated app version and screen signals.
7. No automatic retry; `E_VERIFY_FAILED` ⇒ report and wait.
8. Receipt reply and audit record.
9. Bank alerts on a channel the agent phone does not control.
10. Strongly recommended: a separate operational account with a limited balance.
11. An independent security review before enabling.

## Decision: Read-only first (D7)

- **Rationale**: Limits damage while trust is built; money cannot move even if everything else
  fails.
