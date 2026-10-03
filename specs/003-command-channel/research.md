# Research: command channel

Former ADR-008, `docs/vision/channels.md` and `docs/vision/sms-security.md` (sections 1–5, 7–10).

## Premise: SMS is neither authenticated nor confidential

The sender can be spoofed, content is clear text on the carrier network, the number can be
taken by SIM swap, and messages arrive late, duplicated or out of order. So the sender number is
never a credential; every effectful command needs application-layer authentication.

## Decision: Channel abstraction, SMS first (former ADR-008)

- **Rationale**: The owner wants SMS as one of several channels. If SMS assumptions (160 chars,
  spoofable sender, plain text) leak into grammar, policy or plugins, each new channel becomes
  a refactor. Adapters are base-app code because they sit on the security boundary.
- **Alternatives**: Hard-wire SMS (cheap now, expensive later); channels as YAML plugins (could
  weaken authentication); network channels in the base app (breaks "no INTERNET").

## Channels considered

| Channel | Status |
|---------|--------|
| SMS profile A (plain + code sheet) | v1 |
| On-device admin UI | v1 |
| SMS profile B (encrypted binary) | 011, after Spike 8 |
| Messenger chat via notifications + `RemoteInput` | Candidate; needs the same code auth; inherits that messenger's risks |
| E-mail / web / bot API | Only through a companion app |
| Voice call with keypad tones | Not viable (no call-audio access) |
| USB / adb | Development only |

## Decision: Printed one-time-code sheet (D3)

- **Rationale**: The dumbphone runs no authenticator; paper is separate from both phones.
  HMAC-derived codes mean the phone stores only the key and used indices. Burn-on-use also gives
  each command an idempotency id.
- **Protects against**: spoofed sender and SIM swap (no codes), replay (index burned), brute
  force (8 digits + lockout).
- **Does not protect against**: a code captured in transit while the original is blocked (usable
  once — bounded by the risk-5 second code and local limits); theft of the paper sheet (still
  needs an allowed number; `STOP` and regenerate).
- **Alternatives**: hardware token (owner declined); static PIN (useless against SIM swap).
- **Optional later**: a `LOGIN` session for levels ≤ 3 (off by default; widens exposure).

## Decision: Failures count only for allow-listed senders (T17)

The command number is also the WhatsApp number, so strangers can text it. Counting their
failures would let anyone lock the agent.

## Decision: Two-step confirmation for risk 5

The confirmation word is random, expires in minutes, and is bound to the data **as re-read by
the agent**, not to the original SMS, which may have been altered.

## Threat model

| # | Threat | Main mitigation |
|---|--------|-----------------|
| T1 | Forged command (spoofed sender) | Code per command |
| T2 | SIM swap of the dumbphone | Printed sheet |
| T3 | Replay of a captured SMS | Burned indices |
| T4 | SMS read or altered in transit | Masked replies; confirmation on re-read data |
| T5 | Theft of the agent phone | Read-only banking, safe location, Keystore, local limits |
| T6 | Theft of the dumbphone | Needs the sheet too; `STOP` |
| T7 | Malicious app on the agent phone | Dedicated device, no other apps or browser |
| T8 | Chat text tries to command the agent | Message bodies are data, never commands |
| T9 | Wrong click after a UI change | Post-conditions, refuse-to-guess resolver |
| T10 | SMS flood | Silent drop, rate limits, bounded queue |
| T11 | Social engineering of the owner | Confirmation shows what will happen |
| T12 | Tampered plugin or policy | Admin-only install, hash, signatures (006) |
| T13 | Leak through logs | Redaction; logs stay local |
| T14 | WhatsApp ban | Dedicated number (D8), low volume |
| T15 | Malicious plugin | Capability guard, risk floor (002, 007) |
| T16 | Bank anti-fraud locks the account | Low volume; read-only first |
| T17 | Command number is public (WhatsApp) | Silent drop; failures from strangers ignored; SIM PIN at the carrier |

## Planned security tests

Grammar and SMS-parser fuzzing; replay, burn, lockout and time-window tests; tampering with the
confirmation; manifest and permission review; recovery after reboot mid-command (unknown state
⇒ never repeat).
