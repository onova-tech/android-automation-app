# Contract: Workflow language (used inside plugins)

## Steps

| Construct | Example |
|-----------|---------|
| Parameters and variables | `params: { phone: {}, text: { default: "" } }`, `set: { url: "https://wa.me/${phone}" }` |
| Templates and filters | `"${text\|urlencode}"`; filters `urlencode`, `upper`, `lower`, `trim`, `mask`; lists render one item per line, `${items.size}`, `${items.0}` |
| Targets | `click: { target: { intent: "send the message", role: button, hints: { content_description: "Send" }, region: bottom-right } }` or a named target `click: { target: send_button }` |
| Conditions | `if: { exists: send_button, then: [...], else: [...] }`; `exists`/`not_exists` (target name, target, or hints like `{ text: "OK" }`), `screen_is`, `equals`, `contains`, `is_set`, `not`, `all`, `any` |
| Fallbacks and errors | `first_that_works: [...]`, `try: { do: [...], on_error: [...] }` with `${error.code}` |
| Reuse | `call: { flow: open_chat, with: { phone: "${phone}" }, into: result }` |
| Verification | `expect:` on any action; `assert:` as a step |
| Result | `return: "Sent to ${phone\|mask}"` |
| Texts | `${t.key}` from `i18n/` (005); `t` cannot be assigned |

## Actions

`launch_app`, `open_url`, `click`, `type`, `read_text`, `read_list`, `scroll`, `scroll_until`,
`wait`, `wait_for`, `back`, `home`, `log`.

Every action accepts `retries`, `retry_delay`, `timeout`, `on_failure` (`abort`, `continue`,
`retry(n, ms)`) and `expect`. `read_text`/`read_list` take `into:`.

Planned actions are listed in [008](../../008-agent-device-operation/spec.md) (device and
notifications) and [010](../../010-laya-resolution/spec.md) (screen classification, OCR).

## Run limits (`RunLimits`)

| Limit | Default |
|-------|---------|
| Actions per run | 500 |
| Nodes scanned per snapshot | 5 000 |
| Call depth | 8 |
| Duration | 10 min |

## Error codes

| Code | Meaning | User-facing reply |
|------|---------|-------------------|
| `E_NOT_FOUND` | Target not found | "Could not find X" |
| `E_TIMEOUT` | A step or wait exceeded its time limit | "Took too long" |
| `E_LOW_CONFIDENCE` | Candidates exist but none clearly matches; engine refused to guess | "Screen changed; did not execute" |
| `E_VERIFY_FAILED` | `expect` or `assert` false | "Executed but could not confirm" — ambiguous by nature |
| `E_ACTION_FAILED` | The action reported failure or threw | "Step failed" |
| `E_CAPABILITY` | Plugin tried something it was not approved for (cannot be caught) | "Plugin not permitted" |
| `E_EXPR` | Undefined variable, bad template, invalid flow call | "Plugin error" |
| `E_BUDGET` | Run limits exceeded | "Took too long" |
| `E_DEVICE` | Accessibility off, screen locked, … | "Device unavailable" |
| `E_CANCELLED` | Stopped by the user | "Stopped" |

Produced outside the engine: `E_AUTH` (no details), `E_RISK_DENIED` ("Outside limits"),
`E_APP_VERSION` ("App version not validated"), `E_NEEDS_OWNER` (a step needs a human, e.g. a
face check).
