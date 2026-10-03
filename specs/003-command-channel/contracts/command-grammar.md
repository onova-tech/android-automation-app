# Contract: command grammar (all channels)

```
<KEYWORD> <VERB> <arguments> #<index>-<code>
```

| Command | Example | Risk |
|---------|---------|------|
| Help | `HELP` | 0 (allowed sender only) |
| Status | `STATUS #17-48291360` | 1 |
| Plugin command | `WHATSAPP SEND 5511999: on my way #18-31658804` | plugin's effective risk |
| Next page / last reply | `MORE`, `RESEND` | — |
| Drop a pending confirmation | `CANCEL` | — |
| Confirm a risk-5 command | `OK 7391 #19-80417263` | uses a second code |
| Stop | `STOP` | always allowed; re-enable on the phone |

Rules:

- Keywords and verbs are case- and accent-insensitive; the plugin keyword is its id.
- Arguments follow the command's `args` template (`"<phone>: <text>"`, `"[n]"`); literal
  separators must match, whitespace separators need at least one space, optional arguments
  carry their separator.
- `#<index>-<code>` ends every authenticated command; index 1–999, code 8 digits.
- Invalid or unauthenticated commands get no detailed reply.

## Risk levels

| Level | Examples | Requirements |
|-------|----------|--------------|
| 0 | `HELP`, `STOP` | Allowed sender |
| 1 | `STATUS` | + code |
| 2 | Read messages | + code; truncated replies |
| 3 | Send a message | + code; hourly limit |
| 4 | Balance, statement | + code; masked reply; optional hours |
| 5 | Transfers (not in v1) | + two-step confirmation and the controls in 009 |

## Trust profiles

| Profile | Channel | Max risk | Notes |
|---------|---------|----------|-------|
| `PHYSICAL` | On-device admin UI | 5, no second step | Admin operations only here |
| `STRONG_REMOTE` | Encrypted SMS (011) | 5 with confirmation | Confidential |
| `WEAK_REMOTE` | Plain SMS + code sheet | 5 with confirmation | Replies masked |
| `UNAUTHENTICATED` | Anything else | 0 | `HELP`, `STOP` |

A confirmation must arrive on the channel that started the command.
