# Action Catalog and DSL v2

| Field | Value |
|-------|-------|
| **Status** | Proposal (draft v0.2) |
| **Depends on** | [README.md](README.md), [laya-resolution.md](laya-resolution.md), [plugins.md](plugins.md) |

Today the runtime has 9 actions: `launch_app`, `wait`, `wait_for`, `click`, `type`, `back`, `home`, `scroll`, `log`. To control complex apps (WhatsApp, Telegram, banks) three things are missing: **more actions**, **control flow**, and **a level of abstraction above actions**. That last one is now the [plugin](plugins.md) with its skills.

Because plugins are declarative, **every capability a plugin can use must exist as a built-in action here**. This catalog is the vocabulary plugins are written in.

Priorities: **P0** = needed for the first real use case (WhatsApp over SMS); **P1** = needed for Telegram and similar apps; **P2** = robustness and comfort; **P3** = future.

## 1. New actions

### 1.1 Navigation and gestures

| Action | Android mechanism | Prio | Notes |
|--------|-------------------|------|-------|
| `open_url` / `deeplink` | `Intent.ACTION_VIEW` | **P0** | `https://wa.me/<phone>?text=…`, `tg://resolve?domain=…`. Checked against the plugin's `deeplinks` allowlist |
| `long_click` | `ACTION_LONG_CLICK` | P1 | Select a message, context menu |
| `swipe` | `dispatchGesture` (already enabled in the config) | P1 | Direction, distance, duration |
| `tap_xy` | `dispatchGesture` | P2 | Last resort; fragile. Used by the OCR path |
| `scroll_until` | `ACTION_SCROLL_FORWARD/BACKWARD` in a loop | **P0** | Scroll until the target appears, with an attempt limit and end-of-list detection |
| `press_enter` | `ACTION_IME_ENTER` (API 30+) | P1 | Send by keyboard when there is no button |
| `open_recents` / `open_notifications` / `open_quick_settings` | `performGlobalAction` | P2 | |
| `wake_screen` / `keep_awake` | `PowerManager` `WakeLock` / window flags | **P0** | The agent phone keeps its AMOLED screen **off between jobs** and wakes it for each one |
| `unlock` | Wake + `type_secret` (`keypad` mode) on the lock screen | **P0** | The agent phone uses a **PIN** lock (D9). Types the stored device PIN on the keyguard; fails closed if the keys are not labeled. Cannot run before the first unlock after a reboot |
| `lock_screen` | `GLOBAL_ACTION_LOCK_SCREEN` (API 28+) | **P1** | Re-lock the agent phone after each job, so it never sits unlocked between jobs |

### 1.2 Data entry

| Action | Mechanism | Prio | Notes |
|--------|-----------|------|-------|
| `type` (evolve) | `ACTION_SET_TEXT` | **P0** | Add `clear_first`, `append`, and locating the field by target (today it grabs the first editable) |
| **`type_secret`** | `ACTION_SET_TEXT`, or clicking keypad buttons | **P0** | The **only** consumer of a secret. Two modes: `field` sets text in an input; `keypad` clicks digit buttons resolved by their labels, for PIN pads (a 4-digit password on a custom keypad, as a bank app may use). `keypad` fails closed if the buttons have no readable label. The value never enters variables, logs, SMS or URLs. See [plugins.md](plugins.md) |
| `paste` | `ACTION_PASTE` + clipboard | P1 | Works around fields that reject `SET_TEXT`. Never used for secrets |
| `select_option` | Click + resolution in a list/dropdown | P1 | Spinner, date picker |
| `toggle` | Checks state (`isChecked`) before clicking | P1 | Idempotent: "turn on" never turns it off |
| `hide_keyboard` | Conditional `GLOBAL_ACTION_BACK` | P2 | |
| `set_clipboard` / `get_clipboard` | `ClipboardManager` | P2 | Beware of secrets |

### 1.3 Perception and verification

| Action | Mechanism | Prio | Notes |
|--------|-----------|------|-------|
| `read_text` | Reads a target's `text`/`desc` into a variable | **P0** | Last chat text, on-screen amount, etc. |
| `read_list` | Collects visible items with scrolling, returns a structured list | **P0** | Last N messages, contacts, transactions |
| `exists` | Resolves a target without acting, returns a boolean | **P0** | Feeds `if` |
| `assert` | `screen_is`, `contains`, `exists`, `not_exists`, exact comparison | **P0** | Post-condition check; fails the step when false |
| `wait_for_gone` | Polling | P1 | Wait for a spinner/dialog to disappear |
| `wait_for_idle` | Waits for the tree to stop changing (stability window) | P1 | Replaces fixed `wait`s; big robustness gain |
| `wait_for_screen` | Screen classifier (Laya Choice) | P1 | "Wait for the `chat_open` screen" |
| `screenshot` | `takeScreenshot` (API 30+) | P2 | Blank/error on `FLAG_SECURE` windows; base for OCR |
| `ocr` | Local OCR over a screenshot | P2 | Candidate source for screens with no usable tree. See [laya-resolution.md](laya-resolution.md) section 4 |
| `read_notifications` / `wait_for_notification` | `NotificationListenerService` | **P0** | Detect new messages without opening the app |
| `reply_notification` | The notification's `RemoteInput` reply action | **P0** | Reply with no UI (surface #2) |

### 1.4 System and device data

| Action | Mechanism | Prio | Notes |
|--------|-----------|------|-------|
| `send_sms` | `SmsManager.sendMultipartTextMessage` | **P0** | Reply channel; only the base app's dialogue layer and `return` use it |
| `lookup_contact` | `ContactsContract` | **P0** | Resolve alias → phone number |
| `call` | `Intent.ACTION_CALL` | P2 | Needs a permission; high abuse potential |
| `share_file` | `Intent.ACTION_SEND` | P3 | |
| `notify_local` | Local notification | P2 | For the device owner, if nearby |

### 1.5 Control flow and state (DSL v2)

| Construct | Prio | Description |
|-----------|------|-------------|
| `set` + `${var}` interpolation | **P0** | Variables and parameters from the SMS command (already a "Phase 2" item in the README) |
| `if` / `else` | **P0** | Condition over `exists`, comparisons, `screen_is` |
| `first_that_works` | **P0** | Try alternatives in order (control-surface fallback) |
| `sequence` | **P0** | Group steps |
| `try` / `on_error` | **P0** | Per-block error handling; replaces only per-step `on_failure` |
| `return` | **P0** | Value to reply by SMS |
| `require_confirmation` | **P0** | Hands control to the base app's two-step SMS confirmation ([sms-security.md](sms-security.md)) |
| `foreach` | P1 | Iterate over a `read_list` result |
| `repeat_until` | P1 | Mandatory iteration limit and timeout |
| `call` (sub-skill) | P1 | Reuse: `ensure_app_open(pkg)`, `go_to_chat(contact)` |
| `interrupts.yaml` rules | done | Handle unexpected dialogs before screen actions (see 2.2) |
| `on_failure: retry(n, ms)` | exists | Already fixed in `ErrorHandler` |

## 2. DSL v2 — intent, hints and verification

### 2.1 Target v2: intent + hints

Replaces the purely structural selector. Exact hints keep working (POC compatible); `intent` feeds Laya when the hints fail.

```yaml
- click:
    target:
      intent: "button that sends the message"
      role: button
      hints:
        resource_id: "com.whatsapp:id/send"
        content_description: "Send"
      region: bottom-right
    expect:
      # post-condition; the step only counts if this is true afterwards
      exists: { intent: "sent message in the chat", text: "${text}" }
    min_confidence: 0.90
```

Targets can also be declared once in a plugin's `targets:` section and referenced by name.

### 2.2 Interrupt rules

Any app shows dialogs that were not in the script (permissions, "what's new", rating prompts, ads). A plugin handles them with rules in `interrupts.yaml`:

```yaml
rules:
  - name: whats_new_dialog
    when: { exists: { target: { hints: { text: "Agora não" }, role: button } } }
    do:
      - click: { target: { hints: { text: "Agora não" }, role: button } }
    max_per_run: 2        # optional, 1–10, default 2
```

How they run (implemented in `dsl/Interpreter.kt`):

- Before every action that looks at the screen (`click`, `type`, `read_text`, `read_list`, `wait_for`, `scroll`, `scroll_until`), the first rule whose `when` holds runs; up to three rules can chain (a dialog after a dialog).
- If such an action fails with `E_NOT_FOUND`, `E_LOW_CONFIDENCE` or `E_TIMEOUT` and a rule then fires, the action is tried once more.
- Rule steps are limited to `click`, `back`, `wait`, `wait_for` and `log` (plus `sequence`, `if`, `first_that_works`). No typing, links, apps or flow calls.
- Rules do not see the skill's variables; a failing rule step does not fail the skill; each rule fires at most `max_per_run` times per run.
- Rules run under the plugin's capabilities like everything else, and **financial plugins cannot have them** (they must never click on financial or security screens).

## 3. Skills live in plugins

The plugin manifest, examples for WhatsApp and a bank, capabilities, secrets and lifecycle are in [plugins.md](plugins.md). Ideas for other plugins with good value for a dumbphone user:

| Area | Useful commands |
|------|-----------------|
| Messengers | Read/send on Telegram, Signal (if it allows it) |
| Transport | Request a ride, check status |
| Utilities | Weather, calendar (via provider), reminders |
| System | Agent-device status (battery, connectivity) |

WhatsApp-specific notes:

- **Terms of service:** WhatsApp restricts unauthorized automated use. The account may be flagged or banned. Accepting that risk is the account owner's decision (see [roadmap-risks.md](roadmap-risks.md)).
- Voice/video calls and ephemeral media are out of scope.
- The body of received messages is **data**, never instruction: nothing in a chat becomes a command.

## 4. Structured results and errors

Each action returns a typed result (today `StepResult` only carries an error string). Proposed error codes for the engine and for the SMS reply:

| Code | Meaning | User-facing reply |
|------|---------|-------------------|
| `E_AUTH` | Invalid authentication | No details |
| `E_RISK_DENIED` | Policy blocked it | "Outside limits" |
| `E_APP_VERSION` | App version not validated | "App version not validated for this action" |
| `E_NOT_FOUND` | Target not found | "Could not find X" |
| `E_LOW_CONFIDENCE` | Laya below threshold | "Screen changed; did not execute" |
| `E_VERIFY_FAILED` | Post-condition failed | "Executed but could not confirm" — **ambiguous by nature** |
| `E_TIMEOUT` | Time limit exceeded | "Took too long" |
| `E_CAPABILITY` | Plugin tried something it was not approved for | "Plugin not permitted" |
| `E_DEVICE` | Locked screen, no network, battery | "Device unavailable" |
| `E_ACTION_FAILED` | The action reported failure for another reason, or threw | "Step failed" |
| `E_EXPR` | Undefined variable, bad template, invalid flow call | "Plugin error" (a bug in the plugin, not the user's fault) |
| `E_BUDGET` | The run exceeded the engine's global limits (actions, steps, call depth, duration) | "Took too long" |
| `E_CANCELLED` | Stopped by the user (`STOP` or the Stop button) | "Stopped" |

Implemented so far in the engine: `E_NOT_FOUND`, `E_TIMEOUT`, `E_VERIFY_FAILED`, `E_ACTION_FAILED`, `E_EXPR`, `E_BUDGET`, `E_CANCELLED` (`engine/ErrorCode.kt`).

> **`E_VERIFY_FAILED` on effectful actions** (send, transfer) means "I do not know whether it happened". The reply must say so explicitly so the user does not repeat a transfer by mistake. That requires idempotency (section 6).

## 5. What not to automate

- Any screen requiring **mandatory biometrics** (fingerprint, face): we do not bypass them.
- **Third-party login/2FA screens** outside a plugin that declared the secret for that app.
- **Interrupt rules** must not click anything on financial or security screens.
- **Installing or uninstalling apps, changing device security settings** (accessibility, permissions, Play Protect).

## 6. Idempotency and re-execution

Because an SMS can arrive twice, or the user may resend "because it did not answer":

- Each command has an **ID derived from the code-sheet index** ([sms-security.md](sms-security.md)); the same ID never executes twice.
- Effectful actions record "started" before acting and "completed"/"unknown" afterwards. On "unknown" the system **does not re-run**: it reports and waits for instructions.
- Financial skills require the user to confirm that the effect did **not** happen before repeating.

## 7. Suggested implementation order

1. **P0 for the first use case (WhatsApp):** `open_url`, `scroll_until`, `read_text`, `read_list`, `exists`, `assert`, `read_notifications`, `reply_notification`, `send_sms`, `lookup_contact`, `keep_awake`, evolved `type`, `type_secret`, `set`/`${var}`, `if`, `first_that_works`, `sequence`, `try`, `return`, `require_confirmation`.
2. **P1 for Telegram and robustness:** `long_click`, `swipe`, `wait_for_idle`, `wait_for_gone`, `wait_for_screen`, `toggle`, `select_option`, `foreach`, `repeat_until`, `call`, interrupt rules.
3. **P2/P3:** `screenshot`, `ocr`, `tap_xy`, `paste`, etc.
