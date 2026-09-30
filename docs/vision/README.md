# Vision and Target Architecture — SMS-Controlled Android Automation Layer

| Field | Value |
|-------|-------|
| **Status** | Proposal (draft v0.2) — for review |
| **Date** | 2026-09-30 |
| **Context** | Evolution of the PROJ-000 POC (Phase 1) into a resilient, offline, SMS-controlled automation runtime with a plugin system |

## Document map

| Document | Content |
|----------|---------|
| **README.md** (this file) | Vision, decisions taken, principles, layered architecture, end-to-end flow, agent device provisioning |
| [plugins.md](plugins.md) | Plugin system: declarative YAML plugins, manifest, capabilities, secrets, lifecycle, trust model |
| [laya-resolution.md](laya-resolution.md) | How Laya fits into element resolution, including the OCR path and the model's real limits |
| [action-catalog.md](action-catalog.md) | New actions, DSL v2 |
| [sms-security.md](sms-security.md) | SMS channel, command protocol, printed one-time-code sheet, threat model, bank transfers |
| [roadmap-risks.md](roadmap-risks.md) | Phases, validation spikes, risks, open decisions |
| [ADR-006](../adr/ADR-006-laya-decision-layer.md) | Proposed decision: Laya as local decision layer |
| [ADR-007](../adr/ADR-007-declarative-yaml-plugins.md) | Proposed decision: declarative YAML plugins, intelligence in the base app |

---

## 1. Vision

A dedicated **agent Android phone**, powered on 24x7 in a safe place, receives **SMS commands** from a **dumbphone** and carries out the matching actions in the installed apps (WhatsApp, Telegram, a bank app, …) on its own. The result comes back by SMS.

The user carries only the dumbphone (calls and SMS). Everything that needs a smartphone happens on the agent device.

```
 User ── SMS ──► Dumbphone ──► cellular network ──► Agent phone (Android)
   ▲                                                    │ drives the apps
   └────────────────── reply SMS ◄──────────────────────┘
```

**Target properties**

1. **Resilient:** keeps working when an app changes layout, wording, version or language.
2. **Self-contained:** no external service in the execution path (no cloud LLM, no backend, no telemetry).
3. **Secure by default:** an SMS can be forged; no sensitive action depends on the sender number alone.
4. **Predictable:** ML models *classify among known options*; they never generate free-form actions.
5. **Extensible by data:** new apps are added as **plugins**, which are plain YAML files. All the intelligence lives in the base app.

### What "self-contained" means here

| Dependency | Status |
|------------|--------|
| AI inference (Laya), element resolution, command parsing, policy, plugin interpretation | **100% on-device** |
| Cellular network for SMS | Unavoidable — it is the control channel |
| Internet used by the target apps (WhatsApp, bank) | Unavoidable — the apps need it; our layer makes no network calls of its own |
| Model training / fine-tuning | **Off-device, at development time**; the device only receives signed artifacts |
| Updating plugins and models | Manual (USB/adb/file), with owner approval on the device; no auto-update |

### Out of scope (for now)

- Bypassing biometrics or any security mechanism of a target app.
- Play Store distribution (accessibility-based automation is sideloaded).
- Multi-user / multi-device.
- Anything that requires a cloud service.
- Installing or uninstalling apps on the agent device by SMS (a plugin *uses* an app; installing the app is an owner task).

---

## 2. Decisions taken

| # | Decision | Value | Status |
|---|----------|-------|--------|
| D1 | Agent device | **Samsung Galaxy S20 FE 4G, model SM-G780G**, Android 13 (API 33), 6 GB RAM, running 24x7. As far as I know this model number uses the Snapdragon 865; to confirm in Spike 1 | Confirmed by owner |
| D2 | First bank | **Itaú** | Feasibility unknown — validated in Spike 4 |
| D3 | One-time codes | **Printed sheet** (no token) | Confirmed; design in [sms-security.md](sms-security.md) |
| D4 | Secrets | Bank password and any other secret are **stored on the agent device**, entered on the device when a plugin is installed | Confirmed; see the risk controls that bound the damage in [sms-security.md](sms-security.md) |
| D5 | Extensibility | **Plugins = declarative YAML files**; all logic (interpreter, resolver, policy, auth, ML) is in the base app | Confirmed; see [plugins.md](plugins.md) and ADR-007 |
| D6 | Documentation language | English | Done |
| D7 | First banking scope | **Read-only: balance and statement.** No transfer skill in the first version | Confirmed by owner |
| D8 | WhatsApp account | A **dedicated number** used only for this automation, on a device where the main account never ran. It is the **same number** that receives the SMS commands, so the command number is known to everyone the WhatsApp account talks to (see T17 in [sms-security.md](sms-security.md)) | Confirmed by owner |
| D9 | Lock screen | **Provisional: no secure lock (None or Swipe)**, plus a safe location. Pending Spike 4 (does the bank app require a secure lock?) | Provisional; rationale in the provisioning section and in [sms-security.md](sms-security.md) |

---

## 3. Design principles

1. **Structured surface before UI.** Prefer deeplinks/intents, notification actions and system providers; drive the screen only when nothing else works (section 5).
2. **Deterministic first, ML second.** Exact selectors and a fingerprint cache handle most cases; Laya only runs when they fail.
3. **ML never authorizes an irreversible action alone.** Risky actions require a deterministic anchor and exact-match verification of amount and recipient.
4. **Fail closed.** Doubt means abort and report by SMS, never "try anyway".
5. **Every step is verified.** A step declares what must be true after it, and the runtime checks.
6. **Plugins are data, not code.** A plugin can only ask the base app to do things it already knows how to do, inside the permissions the owner approved.
7. **The base app owns policy.** Risk floors, limits, beneficiaries and authentication are configured on the device by the owner; a plugin can never lower them.
8. **Explicit threat model.** The channel (SMS) and the device are treated as hostile.
9. **Locally observable.** Every resolver decision and every command is recorded on the device (with redaction) for debugging and audit.
10. **ML must earn its place.** Any model gain is measured against a heuristic baseline on a golden test set.

---

## 4. Layered architecture

```mermaid
flowchart TB
  subgraph L1[L1 · Channel]
    SMSIn[SmsReceiver] --- SMSOut[SmsSender + segmentation]
  end
  subgraph L2[L2 · Security]
    Auth[Code-sheet authenticator] --- Pol[PolicyEngine<br/>risk levels, limits] --- Vault[Keystore vault] --- Audit[Hash-chained audit]
  end
  subgraph L3[L3 · Commands]
    Parser[Command grammar] --- Dlg[Dialogue<br/>confirm, paging] --- Alias[Contacts and aliases]
  end
  subgraph L4[L4 · Plugin host and workflows]
    Reg[Plugin registry<br/>per app/version] --- Q[Job queue] --- Guard[Device guard<br/>screen, foreground app]
  end
  subgraph L5[L5 · Execution engine]
    Eng[ExecutionEngine] --- Act[Actions v2] --- Err[ErrorHandler] --- Int[Interrupt rules]
  end
  subgraph L6[L6 · Perception and resolution]
    Snap[ScreenSnapshot<br/>tree or OCR] --- Cand[Candidates + ranking] --- Res[ResolverPipeline] --- Cache[Fingerprint cache]
  end
  subgraph L7[L7 · Local ML]
    Ort[ONNX Runtime] --- Laya[Laya multilingual int8] --- Cal[Calibration]
  end
  subgraph L0[L0 · Android]
    Acc[AccessibilityService] --- Notif[NotificationListener] --- Prov[Intents/Providers]
  end
  L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
  L5 --> L0
  L6 --> L0
```

### Mapping to the current code

| Layer | Exists today (POC) | New / to evolve |
|-------|--------------------|-----------------|
| L0 | `AutomationService`, `AutomationBridge` | `NotificationListenerService`, `dispatchGesture`, screenshot (API 30+) |
| L1 | — | SMS receive/send, segmentation, rate limits |
| L2 | — | Everything (see [sms-security.md](sms-security.md)) |
| L3 | — | Command grammar, dialogue, aliases |
| L4 | `YamlParser`, `Workflow` | **Plugin host** (manifest, capabilities, secrets, lifecycle), DSL v2, job queue |
| L5 | `ExecutionEngine`, `ErrorHandler`, handlers, `CancellationToken` | Extended catalog, interrupt rules, structured results |
| L6 | `SelectorEngine` + 4 strategies | `ResolverPipeline`, contextual candidates, cache, screen classifier, OCR source |
| L7 | — | ONNX Runtime + Laya + calibration |

> **Current parser limitation:** `YamlParser` requires exactly one action key per step. DSL v2 needs blocks (`if`, `foreach`, `try`), which means evolving the AST. That change gets its own ADR when implementation starts.

---

## 5. Control surface hierarchy

UI automation is the **most fragile** option. Each task tries the surfaces in this order:

| # | Surface | Example | Robustness |
|---|---------|---------|------------|
| 1 | **Intent / deeplink** | `https://wa.me/<phone>?text=<msg>` opens a chat with the text prefilled | High |
| 2 | **Notification action (`RemoteInput`)** | Reply to a WhatsApp/Telegram message straight from its notification | High |
| 3 | **Content providers and system APIs** | Contacts, SMS send/read, dial by intent | High |
| 4 | **Notification reading** | Detect new messages without opening the app | High |
| 5 | **UI automation, deterministic resolution** | `resource_id`, `content_description` | Medium |
| 6 | **UI automation, semantic resolution (Laya on the accessibility tree)** | When identifiers changed | Medium |
| 7 | **UI automation, semantic resolution on OCR output** | Screens with no usable accessibility tree | Low–medium |

This reduces reliance on ML: sending a WhatsApp message can be "deeplink + click send", and replying can be "notification + `RemoteInput`", with no element search at all.

---

## 6. End-to-end flow

Example: `WA SEND maria: on my way, 10 min #17-48291360`

```mermaid
sequenceDiagram
  participant D as Dumbphone
  participant S as L1 SMS channel
  participant X as L2 Security
  participant C as L3 Commands
  participant W as L4/L5 Plugin + engine
  participant R as L6 Resolver
  D->>S: SMS
  S->>X: text + sender
  X->>X: sender allowed? sheet code valid and unused?
  X->>C: authenticated command
  C->>C: parse (WA.SEND, to=maria, text=...)
  C->>X: risk of this command? (medium)
  X-->>C: allowed (limit ok)
  C->>W: job: whatsapp plugin, skill send
  W->>W: deeplink -> chat open
  W->>R: resolve "send button"
  R-->>W: node (exact hint or Laya)
  W->>W: click + verify message sent
  W-->>C: result
  C->>S: "OK sent to maria"
  S->>D: reply SMS
```

Decision points:

- **Authentication fails** → no detailed reply (no hints for an attacker); failure counter increments.
- **High risk** → two-step confirmation (see [sms-security.md](sms-security.md)).
- **Low-confidence resolution** → abort and reply with a short reason.
- **Queue:** one job at a time; the engine is not reentrant because the screen is a single shared resource.

---

## 7. Local state

| Data | Storage | Notes |
|------|---------|-------|
| Secrets (bank password, code-sheet key) | Android Keystore + encrypted storage | Non-exportable keys; never in logs |
| Audit log | SQLite, hash-chained | Tamper-evident; never contains secrets |
| Element fingerprint cache | SQLite | Per plugin + app version + intent |
| Job queue and pending dialogues | SQLite | Survives reboot; expires by time |
| Contacts and aliases | SQLite + Contacts provider | Aliases defined by the owner |
| Installed plugins, policy, limits, beneficiaries | Files + SQLite | Changed only in admin mode on the device |
| Models | Signed files | Installed manually |

---

## 8. Agent device provisioning (24x7)

Baseline: **Galaxy S20 FE, Android 13, 6 GB RAM.** 6 GB is enough for the ~650 MB int8 Laya model when it is loaded on demand alongside the target apps (to be measured in Spike 1).

| Area | Recommendation |
|------|----------------|
| Dedication | Dedicated device, factory reset, only the apps the plugins need. No browser, no personal accounts |
| Sideloading | Android 13 blocks accessibility, notification-listener (and possibly SMS) permissions for sideloaded apps until you open **App info → ⋮ → Allow restricted settings** |
| Updates | Turn off OS and app auto-updates. Stay on Android 13 (later versions tighten accessibility and sensitive-data rules). Update target apps deliberately, then re-validate the plugin |
| Battery (Samsung One UI) | Menu names vary slightly by One UI version. In Battery settings: turn off *Put unused apps to sleep* and *Auto-disable unused apps*, add the app to *Never sleeping apps*, and turn off *Adaptive battery*. In Device care → Auto optimization: **turn off *Auto restart*** (a scheduled reboot matters, see *Unlock and reboots*). Keep the phone on the charger and, if a *Protect battery* option (about 85% cap) exists, enable it to reduce battery swelling |
| Screen | The S20 FE has an **AMOLED** panel, so do not keep the screen on 24x7 (burn-in). Keep it **off between jobs**; the agent wakes it for each job and the screen must be on for UI automation (the tree and screenshots are unavailable while it is off). Turn off Always On Display |
| Network | Wi-Fi always on with sleep policy set to never; SIM with SMS plan; consider mobile data as fallback |
| Recovery | The service must restart itself after crashes and reboots (boot receiver + watchdog). There is no remote reboot without root |
| Physical security | A place only the owner can reach. The device holds a bank password (D4), so treat it like a wallet |
| Unlock and reboots | See below. Provisional choice: lock set to **None or Swipe** |

### Unlock and reboots (why the lock screen matters here)

With a **secure** lock screen (PIN, pattern, password), Android keeps app data encrypted after every reboot until the PIN is typed once. A power cut, crash or system update would leave the agent phone deaf (no SMS handling, no automation) until someone enters the PIN. With **None** or **Swipe**, the phone recovers by itself.

| Option | Autonomous after a reboot? | Can the agent wake and use the screen? | Exposure if the phone is stolen |
|--------|----------------------------|----------------------------------------|---------------------------------|
| **None / Swipe** (provisional choice) | Yes | Yes (a swipe gesture unlocks) | High: anyone can use the phone |
| PIN or password | **No**, needs a human after every reboot | Only if the PIN is stored and typed automatically, which is unreliable on lock screens | Lower |

Two things can overturn the provisional choice:

- **The bank app may require a secure lock screen** (many do). If Itaú refuses to run without one, Spike 4 will show it, and the phone would need a PIN and manual recovery after reboots.
- **Physical exposure.** With no lock, the phone's safety is the room it sits in, which is why the local limits and the read-only first scope matter.

> **Handset support status:** as far as I know, Samsung's official updates for the S20 FE have ended or are near their end, with Android 13 likely its last major version. Check Settings → About phone → Software information → Android security patch level. The owner's phone was at the **2024-05-01** patch level on 2026-09-30 (about 17 months old); see the open item in [roadmap-risks.md](roadmap-risks.md). An unpatched phone that stores a bank password is a real risk. It is mitigated by dedicating the phone (no browser, no other apps, no personal accounts) and by the read-only first scope. The variant is SM-G780G with 6 GB (confirmed by the owner); the chipset changes the Laya latency in Spike 1.

---

## 9. Non-functional requirements (targets to validate)

| Topic | Proposed target | How to validate |
|-------|-----------------|-----------------|
| Baseline device | Android 13 (API 33), 6 GB RAM | Spike 1 |
| Laya decision latency | < 1.5 s per resolution (only on cache miss) | Benchmark on the real device |
| End-to-end latency, simple command | < 30 s from SMS to reply SMS | On-device tests |
| Battery/thermals | Model loaded on demand and unloaded when idle; safe under continuous charging | Power profile, multi-day test |
| Availability | Days without intervention; self-restart | Soak tests |
| Privacy | Nothing leaves the device except SMS and the target apps' own traffic | Manifest review (no `INTERNET` permission for our app, if feasible) |

> **About `INTERNET`:** the automation app does not need its own network permission. Leaving it out of the manifest is a verifiable way to back the "self-contained" claim.

---

## Sources

- [Laya: Technical Overview (Flowtivity)](https://flowtivity.ai/blog/laya-open-source-jev-alternative/)
- [Jev vs Laya (DEV Community)](https://dev.to/jamilxt/jev-vs-laya-the-same-ai-idea-one-closed-and-one-open-3c6e)
- [laya-android (edwardmonteiro)](https://github.com/edwardmonteiro/laya-android)
