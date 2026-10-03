# Specifications

This project uses [Spec Kit](https://github.com/github/spec-kit) (spec-driven development).
The project's principles are in [`.specify/memory/constitution.md`](../.specify/memory/constitution.md);
each feature has a folder here with:

| File | Content |
|------|---------|
| `spec.md` | What and why: user stories, functional requirements, success criteria |
| `research.md` | Decisions with rationale and rejected alternatives (the former ADRs live here) |
| `plan.md` | How: technical context, Constitution Check, source layout |
| `data-model.md`, `contracts/` | Entities and external formats (package format, grammar, DSL) when the feature has them |
| `tasks.md` | Work items; `[X]` = done and covered by tests |

New work: run `/speckit-specify <description>` (creates `specs/NNN-name/`), then `/speckit-plan`,
`/speckit-tasks` and `/speckit-implement`. Branches stay `feature/<name>`.

## Product in one paragraph

A dedicated Android phone (the **agent**) runs 24x7 in a safe place and operates apps for its
owner — WhatsApp, Nubank, later Telegram and others — on commands sent from a dumbphone by SMS,
and later through other channels. Everything runs on the phone: no cloud service. It is
resilient to app changes (self-healing targets), extensible by data (`.agp` plugin packages),
and secure by default (one-time codes, risk × trust policy, signed plugins, read-only banking).

```
 SMS "WHATSAPP SEND 5511999: on my way #17-48291360"
   │
   ▼
 Channel adapter ─► Command router ─► Plugin skill ─► Interpreter ─► Accessibility service
 (SMS, admin UI)    allowlist, code,   (.agp package)  targets, limits,  clicks, types,
                    policy, audit                      capability guard  reads the screen
   ▲                                                                           │
   └──────────────────────── short reply ("Sent to *******99") ◄───────────────┘
```

## Features

| # | Feature | Status |
|---|---------|--------|
| [001](001-execution-engine/spec.md) | Execution engine and workflow language | Implemented (JVM) |
| [002](002-plugin-packages/spec.md) | `.agp` plugin packages, capability guard, `agp` tool | Implemented (JVM) |
| [003](003-command-channel/spec.md) | Command channel: SMS, code sheet, policy, audit, admin screen | Implemented; device part unvalidated |
| [004](004-interrupts-replay/spec.md) | Interrupt rules and replay tests | Implemented (JVM) |
| [005](005-plugin-i18n/spec.md) | Per-language plugin texts | Implemented (JVM) |
| [006](006-package-signing/spec.md) | Package signing | Implemented (JVM) |
| [007](007-plugin-classification/spec.md) | Classification by the base app (financial list, secrets) | Implemented (JVM) |
| [008](008-agent-device-operation/spec.md) | Unattended agent phone: wake, PIN unlock, secrets, notifications, reboots | Planned — needs the agent phone |
| [009](009-nubank-read-only/spec.md) | Nubank read-only banking (balance, statement) | Planned — Spike 4 partial |
| [010](010-laya-resolution/spec.md) | Self-healing resolution with Laya | Planned — gated by Spikes 1–2 |
| [011](011-encrypted-sms/spec.md) | Encrypted SMS (profile B) | Planned — gated by Spike 8 |

"Implemented (JVM)" means built and covered by `./gradlew :core:test`; **nothing has run on a
phone yet**.

## Validation spikes

| # | Question | Spec | Status |
|---|----------|------|--------|
| 1 | Does a 6 GB Android 13 phone run int8 `laya-multilingual` (RAM, latency, battery)? | 010 | Open |
| 2 | Does Laya beat the heuristic ranker on a golden set, and generalize to new intent wording? | 010 | Open |
| 3 | Is SMS receive/send reliable for ≥ 72 h on One UI under Doze? | 008 | Open |
| 4 | Does Nubank work with our accessibility service; can the PIN be typed into the system prompt? | 009 | **Partial**: read gate passed |
| 5 | Is `RemoteInput` on WhatsApp/Telegram notifications stable? | 008 | Open |
| 6 | Does the PIN-locked phone run unattended; how does it behave after reboots? | 008 | Open |
| 7 | Can a real WhatsApp flow be written with built-in actions only? | 002 | Done in replay; unvalidated on the real app |
| 8 | Can a candidate dumbphone send and receive binary SMS from our program? | 011 | Open |

## Decisions taken with the owner

| # | Decision |
|---|----------|
| D1 | The agent is a **second phone** (Android 13+, ≥ 6 GB RAM), model to be chosen. The owner's Galaxy S20 FE (SM-G780G) is a daily phone, used only for read-only tests |
| D2 | First bank: **Nubank** (changed from Itaú on 2026-09-30) |
| D3 | Authentication: **printed one-time-code sheet** |
| D4 | Secrets stored **on the agent phone**, entered there at plugin install |
| D5 | Plugins are **declarative packages**; all intelligence in the base app |
| D6 | Documentation in English |
| D7 | Banking v1 is **read-only**: balance and statement |
| D8 | WhatsApp on a **dedicated number**, the same SIM that receives SMS commands |
| D9 | Agent phone uses a **PIN lock** (Nubank requires the device credential); the agent types it; a human unlocks after reboots |
| D10 | **SMS is the first channel, not the only one** |
| S9/S10 | Packages are signed; the phone (not the plugin) decides what is financial |

Ruled out: bypassing biometrics or app protections; a generative agent deciding actions; cloud
services in the execution path; remote installation of plugins or apps; a KaiOS dumbphone client.

## Open owner decisions

| # | Question | Default if unanswered |
|---|----------|-----------------------|
| O1 | **Which phone will be the agent?** (still patched, ≥ 6 GB, Samsung or Pixel; Android 14+ adds rules that let apps hide content from automation) | — blocks all device work |
| O2 | Languages beyond Portuguese? | Portuguese (plugins already support several) |
| O3 | Single user or several? | Single user |
| O4 | Statement detail over SMS | Masked amounts, last 5 entries, short descriptions |
| O5 | Code sheet delivery | Shown once on the phone; owner transcribes |
| O6 | Plugin install path | File picker in admin mode |
| O7 | OCR / icon recognition for unlabeled apps | Not now; revisit after 010 |
| O8 | Dumbphone: Java ME for encrypted SMS, or plain SMS only | Plain SMS for v1 |
| O9 | Second channel after SMS | None planned |
| O10 | Accept terms-of-service risks (WhatsApp ban, Nubank objection) | Explicit yes needed before going live |

Owner actions that block device work: buy the agent phone and set a PIN lock; get the dedicated
SIM with a carrier SIM PIN; log in to Nubank once on the agent phone.

## Risks that could change direction

| Risk | Effect | Early signal |
|------|--------|--------------|
| Nubank rejects our accessibility service, or the PIN cannot be typed | Banking drops out; messaging continues | Spike 4 |
| Android 14+ hides app content from automation | Some apps become unreadable | Spike 4 |
| Laya does not beat the baseline | Laya dropped; heuristic resolver stays | Spike 2 |
| WhatsApp bans the dedicated number | WhatsApp plugin lost; SMS still works | Low volume, pacing |
| A reboot leaves the agent deaf until unlocked | Downtime | Spike 6 |
| Target-app updates break plugins | Commands fail closed until fixed | Replay + `agp targets` on new dumps |
| No suitable Java ME dumbphone | No encrypted SMS; profile A still works | Spike 8 |

## Product success metrics

| Metric | Target |
|--------|--------|
| Wrong actions at high risk | Zero tolerated |
| Authentication false positives | Zero |
| SMS to reply, simple command | < 30 s (to validate) |
| Agent availability | Days without intervention |
| Recovery after an app update | Plugins keep working without edits (with 010) |
| Plugin authoring | A new simple plugin needs no base-app release |
