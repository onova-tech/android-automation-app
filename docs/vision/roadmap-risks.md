# Roadmap, Spikes, Risks and Open Decisions

| Field | Value |
|-------|-------|
| **Status** | Proposal (draft v0.2) |
| **Depends on** | All documents in [docs/vision](README.md) |

Time estimates are left out on purpose: they depend on the decisions below and on spike results.

## 1. Validation spikes (before investing in the phases)

The spikes answer the questions that could invalidate parts of the plan. **They should run first.**

| # | Question | How to validate | Decision criterion |
|---|----------|-----------------|--------------------|
| **1** | Does a 6 GB Android 13 phone handle the model (RAM, disk, battery, heat)? The S20 FE (Snapdragon 865, confirmed) can serve as a proxy until the agent phone is chosen | Run the int8 `laya-multilingual` through ONNX Runtime on the real handset while WhatsApp and the bank app are in the background. Record the chipset variant (Exynos 4G or Snapdragon 5G) | Latency per decision, peak RAM, drain. If unfeasible ⇒ heuristic baseline only |
| **2** | Does Laya really improve resolution? Does it generalize to new intent wording? | Golden set from real WhatsApp/Telegram screens + small fine-tune; compare with the heuristic ranker; test unseen plugin intents; compare tree vs. OCR candidates | Measurable gain over the baseline. If none ⇒ Laya becomes optional |
| **3** | Can we receive and send SMS and keep the service alive for days on One UI (Android 13)? | Minimal app with `SMS_RECEIVED`, `SmsManager`, resilient service, "Allow restricted settings", Samsung battery settings from [README.md](README.md) section 8 | Reliability over ≥ 72 h; behavior under Doze and Samsung's background limits |
| **4** | **Does the Nubank app work with an accessibility service enabled?** — **partial: read gate passed (2026-10-01)**, see [spike-04-nubank.md](spike-04-nubank.md) | Done: tree dumps on the owner's phone. Remaining, on the agent phone: our service enabled, PIN typed into the system credential prompt, re-authentication frequency, device registration | If Nubank refuses our service or the PIN cannot be typed ⇒ the banking case is out of scope (unless the notification-ledger fallback proves enough) |
| **5** | Is `RemoteInput` on WhatsApp/Telegram notifications stable? | Proof of concept: reply through the notification | If yes, it is surface #2 (more robust than UI) |
| **6** | Does the PIN-locked agent phone run unattended, and how does it behave after a reboot? | Repeated screen-off → wake → PIN unlock → act cycles; power-cut and reboot tests; a `directBootAware` receiver replying before first unlock; charging 24x7 with the battery cap | Confirms the PIN lock setup (D9) and the recovery procedure |
| **7** | Can a declarative YAML plugin express a real WhatsApp flow with only the built-in actions? | Write the `whatsapp` plugin from [plugins.md](plugins.md) and run it in the prototype interpreter | Gaps found become new built-in actions, not plugin code |
| **8** | Can a candidate dumbphone send **and receive** binary SMS from our own program? | Buy or borrow one candidate. Write a minimal MIDlet: send a data SMS to a port, listen on a port, test push registration with the app closed, test the crypto library and speed, test loading the app without a store | Send, receive (also with the app closed), and crypto all work ⇒ Profile B is available on that model. Otherwise stay on Profile A or use a locked-down Android phone |

## 2. Phases

### Phase 2 — Engine foundation (no ML, no SMS)

- DSL v2: variables, `if`, `first_that_works`, `sequence`, `try`, `return`, target with `intent` + hints (the P0 part of the catalog).
- P0 read and verify actions: `read_text`, `read_list`, `exists`, `assert`, `scroll_until`.
- Structured results and error codes.
- Local observability: a trace per run, including resolver decisions.
- Screen recorder (data for later training).
- **Plugin host, first version:** manifest schema, validator, capability checks, interpreter over the existing engine.
- **Profile B protocol first, on Android:** frame format, test vectors and a reference client on a spare Android phone. The dumbphone client is a port done only after Spike 8 picks hardware.
- Reduce POC debt: real line numbers in parse errors, node recycling where applicable, `INTERNET` removed from the manifest.

**Exit:** a real WhatsApp flow runs from a YAML plugin through a **local interface** (no SMS), with post-condition verification.

### Phase 3 — Channel gateway, SMS channel and base security

- L1: channel gateway + **SMS adapter** + on-device admin UI ([channels.md](channels.md)); L2 (code sheet, policy with channel trust profiles, audit, vault); L3 (channel-neutral grammar, dialogue, aliases).
- Admin mode (device lock + app PIN) and the plugin install/approve flow.
- `NotificationListener`, `reply_notification`, deeplinks.
- WhatsApp plugin with skills up to risk level 3.
- Device guard: `keep_awake`, watchdog, queue; 24x7 provisioning checklist ([README.md](README.md) section 8).

**Exit:** from the dumbphone, send and read WhatsApp messages authenticated with the printed sheet.

### Phase 4 — Semantic resolution (Laya)

- Full `ResolverPipeline`: cache, ranker, Laya, verification.
- Fine-tuning + calibration + golden set + automated regression.
- Screen classifier and interrupt rules.
- OCR candidate source, if Spike 2 supports it.
- Enters only if Spike 2 confirms a gain.

**Exit:** survives app version changes without manually editing the plugin, on the test set.

### Phase 5 — Breadth

- Telegram and other plugins; plugin tooling (recorder, replay, per-version validation).
- Local OCR / UI-grounding for icon-only screens, if decided.
- Signed packaging for models.

### Phase 6 — Banking (conditional)

**6a — read-only (v1 banking).** Starts if Spike 4 is positive. Plugin with `scope: read_only`, `balance` and `statement`, masked replies, level 4, allowed hours. Uses `type_secret` for login.

**6b — transfers (later, separate decision).** Starts only after 6a has run reliably for a while, and an **independent security review** is complete. Adds `transfer` with every control from [sms-security.md](sms-security.md) section 6.2.

## 3. Risks

| Risk | Prob. | Impact | Mitigation |
|------|-------|--------|-----------|
| Laya (new, third-party port) does not reach the needed accuracy | Medium | High | Spike 2; heuristic baseline; replaceable artifact |
| The Nubank app blocks accessibility, or exposes too little of its screens (no OCR fallback, since its windows cannot be captured) | Unknown | High (for the banking case) | Spike 4 early with the tree-only gate; read-only scope; notification-ledger fallback to evaluate; operational account |
| Bank anti-fraud reacts to automated access | Medium | High | Low-volume tests; bank alerts; ask the bank |
| WhatsApp account banned | Medium | Medium | Conscious decision; human-like pacing; limits; secondary number |
| Target-app updates break plugins | High | Medium | Cache/self-healing; per-version validation; turn off auto-update of target apps |
| No suitable Java ME dumbphone exists (or KaiOS-only phones are all that is available) | Medium | Medium | Profile A works on any phone; a locked-down Android phone can run Profile B; Spike 8 before buying in bulk |
| Growing Android restrictions on accessibility (sensitive data, restricted settings) | Medium | High | Fixed Android 13 baseline; freeze OS updates on the agent |
| Theft of the agent phone (Nubank password and its own PIN stored on it) | Low | Critical (banking) | Safe location; read-only v1 (no money can move); bank-side alerts; later, local limits and beneficiary allowlist; optional operational account |
| The agent phone is out of security updates | Medium | Medium–High | Choose a model still receiving updates; dedicated phone, no browser or other apps, read-only v1 |
| A reboot leaves the agent unresponsive until a human unlocks it (secure lock is required by Nubank) | **High** | Medium | *Auto restart* off; stable power; `directBootAware` receiver that tells the owner by SMS; Spike 6 |
| The PIN cannot be typed into the system credential prompt by our service | Unknown | High (for banking) | Test first on the agent phone; no workaround that bypasses the prompt |
| AMOLED burn-in if the screen stays on | Medium | Low | Screen off between jobs; Always On Display off |
| 24x7 charging: battery swelling, heat, OEM background kills | Medium | Medium | Charge limit, battery whitelist, watchdog; test on the chosen handset (Spike 6) |
| Malicious or buggy plugin | Low | High | Capability scoping, use-only secrets, base-app risk floor, admin-mode install |
| Power or network failure mid-action | Medium | Medium | "Started/unknown" states, no automatic retry |
| SMS costs and carrier limits | Medium | Low | Short replies, limits |
| Accessibility policy / Play Protect | Certain | Low | Sideloading (already assumed) |
| Legal or terms-of-service changes | Low | High | Review terms; explicit owner choice |

## 4. Decisions

### Already taken

| # | Decision | Value |
|---|----------|-------|
| 1 | Agent device | **A second phone, model to be chosen** (Android 13+, ≥ 6 GB). The S20 FE is the owner's daily phone, used only for read-only tests |
| 2 | First bank | Nubank (changed from Itaú on 2026-09-30); read gate passed in Spike 4 |
| 3 | One-time codes | Printed sheet |
| 4 | Secrets | Stored on the device, entered at plugin install |
| 5 | Extensibility | Declarative YAML plugins; intelligence in the base app |
| 6 | Documentation language | English |
| 7 | First banking scope | Read-only: balance and statement |
| 8 | WhatsApp account | Dedicated number, on a device where the main account never ran; **the same number receives the SMS commands** (T17) |

| 9 | Lock screen | **Secure PIN** on the agent phone (Nubank requires the device credential); the agent types it; a human unlocks after reboots |

### Still open

| # | Decision | Impact |
|---|----------|--------|
| 1 | **Which phone will be the agent?** Prefer a model that still gets security updates; Android 13+, ≥ 6 GB RAM, ideally a PIN lock screen with labeled keys | Blocks Spikes 3, 6 and the rest of Spike 4 |
| 2 | **Languages:** Portuguese only, or also English/Spanish? | Laya variant and training data |
| 3 | **Icon-only elements:** invest in local OCR/UI-grounding? | Outside Laya's reach |
| 4 | **Single user or several?** | Complexity of authentication and policy |
| 5 | **Sheet delivery:** how do you want to obtain the printed sheet (transcribe by hand, print through a path you control)? | Admin-mode UX |
| 6 | **Plugin install channel:** adb push, file picker, or a shared folder? | Admin-mode UX and tooling |
| 7 | **Statement reply detail:** how much can be sent over SMS (masked by default)? | Reply format |
| 8 | **Dumbphone choice:** none bought yet. Java ME is the preferred route and KaiOS looks blocked (see [sms-security.md](sms-security.md) 2.2). Validate a candidate with Spike 8 before committing | Whether Profile B (encrypted) is available |

## 5. Product success metrics

| Metric | Target to calibrate |
|--------|--------------------|
| Success rate of low-risk commands | High and stable per app version |
| Recovery after a UI change | % of plugins that keep working after an app update with no manual edit |
| Wrong actions at high risk | Zero tolerated; any occurrence is an incident |
| Authentication false positives | Zero |
| Time from SMS to reply | Simple command under 30 s (to validate) |
| Agent phone availability | Days without intervention |
| Plugin authoring effort | A new simple plugin without any base-app release |

## 6. Immediate next steps

1. Choose the agent phone (open item 1) and the dumbphone (open item 8).
2. On the agent phone, finish Spike 4 (our service + PIN in the system prompt), then Spikes 3 and 6. Spike 1 can run on the S20 FE now (read-only performance test, no secrets).
3. Approve [ADR-006](../adr/ADR-006-laya-decision-layer.md), [ADR-007](../adr/ADR-007-declarative-yaml-plugins.md) and [ADR-008](../adr/ADR-008-channel-abstraction.md), or adjust them with spike results.
4. Only then break Phase 2 into tickets.
