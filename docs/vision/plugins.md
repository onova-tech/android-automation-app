# Plugin System — Declarative YAML Plugins

| Field | Value |
|-------|-------|
| **Status** | Proposal (draft v0.2) |
| **Depends on** | [README.md](README.md), [action-catalog.md](action-catalog.md), [sms-security.md](sms-security.md) |
| **Decision record** | [ADR-007](../adr/ADR-007-declarative-yaml-plugins.md) |

## 1. Goal

Add support for a new app by dropping in **one YAML file**, with no change to the base app. The plugin says *what* the app offers (commands, steps, targets, secrets it needs). The base app knows *how* to do everything (interpret steps, find elements, use Laya, authenticate, enforce policy, verify results, reply).

## 2. What a plugin is — and is not

| A plugin **is** | A plugin **is not** |
|-----------------|--------------------|
| A YAML file validated against a schema | Code (no scripts, no expressions with side effects, no loops without a bound) |
| A list of commands mapped to skills built from the built-in actions | A way to add new low-level capabilities (that needs a new base-app release) |
| A set of element intents and hints for one app | The place where risk limits, beneficiaries or authentication are defined |
| A declaration of the capabilities and secrets it needs | Able to read a secret into a variable, a log or an SMS |
| Bound to a specific app package and tested versions | Able to touch any other app |

## 3. Responsibility split

| Concern | Base app | Plugin |
|---------|----------|--------|
| YAML/DSL interpretation, expressions, limits (max steps, max runtime) | ✅ | |
| Element resolution (cache, hints, ranker, Laya, OCR) | ✅ | Supplies `intent` text and hints |
| SMS parsing, authentication, replay protection | ✅ | Declares verbs and parameters |
| Risk level, limits, beneficiaries, allowed hours | ✅ (owner config) | Suggests a category; **cannot lower the floor** |
| Secret storage and typing | ✅ (Keystore) | Declares slots; uses `type_secret` |
| Post-condition verification, idempotency, audit | ✅ | Declares `expect` conditions |
| App-specific flow | | ✅ (steps, screens) |

## 4. File layout

```
plugins/
  whatsapp/
    plugin.yaml          # manifest + commands + skills
    fixtures/            # optional: recorded UI snapshots for offline replay tests
  itau/
    plugin.yaml
```

A plugin is a single `plugin.yaml` (fixtures are optional and only used by tooling).

## 5. Manifest

```yaml
schema: 1                       # plugin schema version; the base app rejects unknown majors
plugin:
  id: whatsapp
  name: WhatsApp
  version: 1.0.0
  category: messaging           # messaging | financial | utility  (see risk floor, section 9)

app:
  package: com.whatsapp
  tested_versions: ["2.26.x"]   # base app refuses financial skills outside this list

capabilities:                   # what this plugin may do; approved by the owner at install
  ui_automation: [com.whatsapp]
  read_screen:   [com.whatsapp]
  deeplinks:     ["https://wa.me/*"]
  notifications: { read: [com.whatsapp], reply: [com.whatsapp] }
  contacts_lookup: true
  sms_reply: true

secrets: []                     # none for this plugin

commands:                       # SMS verbs handled by this plugin
  - verb: SEND
    skill: send
    args: "<contact>: <text>"
  - verb: READ
    skill: read_unread

targets:                        # named elements, reused by skills
  send_button:
    intent: "button that sends the message"
    role: button
    hints: { resource_id: "com.whatsapp:id/send", content_description: "Send" }
  message_box:
    intent: "text field to type the message"
    role: edit_text

screens:                        # optional: known screens, used for classification and verification
  chat_open:
    signals: [{ exists: message_box }]

skills:
  send:
    params:
      to:   { type: contact, required: true }
      text: { type: string, max_length: 500, required: true }
    steps:
      - first_that_works:
          - reply_notification: { from: "${to.display_name}", text: "${text}" }
          - sequence:
              - open_url: "https://wa.me/${to.phone}?text=${text|urlencode}"
              - wait_for_screen: chat_open
              - click:
                  target: send_button
                  expect: { exists: { text: "${text}" } }
    return: "Sent to ${to.alias}"
```

Field notes:

- **`intent`** is natural-language text used by the resolver. Because Laya answers a generic question ("does this element match this description?"), a plugin can introduce new intent wording **without retraining the model**. How well this generalizes must be measured (Spike 2).
- **`hints`** are exact selectors, tried first. Intent-only targets are allowed but slower.
- **`expect`** is a post-condition. A step whose `expect` is false counts as failed.
- **`tested_versions`** ties the plugin to app versions the owner has validated.

### A financial plugin (sketch)

The first version ships **read-only** banking (balance and statement). The `transfer` skill below is a sketch of a later phase and is not part of v1.

Package name and identifiers below are placeholders. The real ones come from inspecting the app (Spike 4).

```yaml
schema: 1
plugin: { id: bank, name: "Bank", version: 0.1.0, category: financial, scope: read_only }
app: { package: com.example.bank, tested_versions: ["1.2.3"], candidates: tree_only }   # secure windows: no screenshot/OCR
capabilities:
  ui_automation: [com.example.bank]
  read_screen:   [com.example.bank]
  screenshot:    false              # never captures this app's screen
  secrets: [password]
  sms_reply: true
secrets:
  - name: password
    prompt: "Bank app password"
    use: type_secret_only        # can only be typed into a field; never read
commands:
  - { verb: BALANCE,   skill: balance }
  - { verb: STATEMENT, skill: statement, args: "[n]" }
  # later phase, not in v1 (and rejected while the plugin says scope: read_only):
  # - { verb: TRANSFER, skill: transfer, args: "<amount> <beneficiary>" }
skills:
  balance:
    steps:
      - launch_app: { package: com.example.bank }
      - click:  { target: { intent: "password field" } }
      - type_secret: { secret: password }
      # …
    return: "Balance ${balance|mask}"
  transfer:
    params:
      amount:      { type: money,       required: true }
      beneficiary: { type: beneficiary, required: true }   # from the owner's local list
    steps:
      # …navigate to the confirmation screen…
      - read_text: { target: confirm_amount,    into: shown_amount }
      - read_text: { target: confirm_recipient, into: shown_recipient }
      - require_confirmation:                 # base app sends the SMS challenge (see sms-security.md)
          amount: "${shown_amount}"
          recipient: "${shown_recipient}"
      - click: { target: final_confirm_button, anchor: exact }   # deterministic anchor required
```

## 6. Steps and expressions

- Steps use the actions in [action-catalog.md](action-catalog.md) plus `first_that_works`, `sequence`, `if`, `foreach`, `try`, `call`.
- Expressions are limited to `${var}`, `${var.field}` and a fixed set of pure filters (`urlencode`, `mask`, `upper`, `trim`, …). There is no arbitrary evaluation.
- **Bounds are mandatory:** the base app enforces a global maximum of steps, loop iterations and total runtime per job, regardless of what the plugin says.

## 7. Secrets

- The plugin only **declares slots** (`name`, `prompt`, `use`). It never holds values.
- The owner types the values **on the device, in admin mode**, when installing the plugin. They are stored under an Android Keystore key.
- The only way to consume a secret is `type_secret`, which types into a resolved field. A secret cannot be assigned to a variable, interpolated into text, written to a log, placed in an SMS, or sent through `open_url`.
- Redaction in logs and traces is enforced by the base app.
- Uninstalling a plugin **deletes its secrets**.

## 8. Capabilities and scoping

Every action is checked against the plugin's approved capabilities:

- `ui_automation` / `read_screen` restrict which packages the plugin may operate on. If the foreground app is not in the list, the step fails.
- `deeplinks` is an allowlist of URL patterns for `open_url`.
- `notifications`, `contacts_lookup` and `sms_reply` are explicit grants.
- `device_credential_prompt` lets a plugin act on the **system** credential prompt (the device PIN screen an app like Nubank opens through `BiometricPrompt`), and only to type the device PIN with `type_secret`. Nothing else in system UI is reachable. Granted per plugin at install, and listed in the install summary.
- `screenshot` (and therefore OCR) is a separate grant, **denied by default**. A plugin with `candidates: tree_only` can never use it, and the base app does not capture windows of an app that declared it.
- Anything not declared is denied. Capabilities are shown to the owner at install (section 10), and an update that adds a capability requires re-approval.

## 9. Risk floor and limits belong to the base app

The plugin may suggest a `category`. The base app computes the **minimum risk level** and takes the higher of the two:

| Trigger | Minimum level |
|---------|---------------|
| `category: financial`, or the plugin declares any secret, or it uses `type_secret` | 5 for state-changing skills; 4 for read-only skills |
| Sends messages | 3 |
| Reads messages | 2 |

A financial plugin may declare `scope: read_only`. The base app then additionally refuses to click any target that looks like a payment or transfer entry (defense in depth, see [sms-security.md](sms-security.md) section 6.0), and rejects any `transfer`-type command.

Limits (per-operation and daily amounts, allowed hours, beneficiary list, rate limits) are stored in the base app's policy, entered in admin mode. A plugin cannot raise a limit, add a beneficiary or change an alias. See [sms-security.md](sms-security.md).

## 10. Lifecycle

**Install (physical presence required)**

1. Put `plugin.yaml` on the device (adb push, file picker, shared folder).
2. Enter **admin mode** (device screen lock, plus an app PIN). SMS can never enter admin mode.
3. The base app validates the schema and shows a summary: package, capabilities, commands, secrets requested, computed risk floor, and the file's SHA-256.
4. The owner approves, then types the secrets.
5. **Homologation:** the base app compares the installed app version to `tested_versions`, then runs an optional read-only dry run.
6. The plugin is enabled.

**Update:** a diff is shown. Added capabilities, added secrets, or a changed `package` require re-approval and disable the plugin until approved.

**Disable / uninstall:** immediate; uninstall wipes secrets, cache entries and pending jobs.

**Version drift:** when the target app's version changes, financial skills are disabled until the owner re-validates the plugin (a recorded dry run passes, or the version is added to `tested_versions`).

**Server-driven UIs (for example Nubank):** the app version does not pin what the screen looks like, because the server can change screens and labels at any time. For such plugins the base app also runs a **screen-signal check** before a financial skill: the plugin's `screens:` signals (expected labels and elements) must match on the screens it reaches, or the skill aborts with `E_APP_VERSION`/`E_NOT_FOUND` and reports that the UI changed. Read-only skills that fail this check stop rather than guess.

## 11. Threats specific to plugins

| Threat | Mitigation |
|--------|-----------|
| Malicious or buggy plugin exfiltrates a secret | Slots are use-only; `type_secret` is the sole consumer; no variable/log/SMS/URL path |
| Plugin operates on an app it did not declare | Package scoping on every action |
| Plugin lowers risk to skip confirmation | Base app enforces the floor; the plugin can only raise it |
| Plugin adds itself a beneficiary or raises limits | Policy is not plugin data |
| Plugin loops forever / floods SMS | Global bounds; reply and rate limits |
| A tampered plugin replaces a trusted one | Install only in admin mode; hash shown; updates re-approved; no remote install |
| Plugin text influences the resolver into a wrong click | Anchor and exact-match verification for irreversible steps |
| Owner approves without reading | Summary is short and explicit about secrets and risk floor; financial plugins add an extra confirmation screen |

## 12. Tooling and testing

| Tool | Purpose |
|------|---------|
| **Validator** (CLI on the developer's PC, and on the device at install) | JSON Schema validation, capability/secret/risk report |
| **Recorder** | Capture redacted UI snapshots and the successful actions from the device |
| **Replay** | Run a plugin against recorded snapshots offline (regression tests, no phone needed) |
| **Golden set** | Per plugin and app version, feeds the resolver evaluation ([laya-resolution.md](laya-resolution.md)) |
| **Dry run** | Read-only pass on the device that confirms screens and targets resolve |

## 13. Versioning

- `schema` is the plugin format version. The base app supports a range of majors and states which.
- `plugin.version` follows semver; changes to commands or capabilities bump the minor or major.
- The DSL and action catalog are versioned with the base app; a plugin can declare `requires_base: ">=x.y"`.
