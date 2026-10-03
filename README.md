# Android Automation Agent

A dedicated Android phone that operates apps for you (WhatsApp, a bank app, …) on commands sent
from a dumbphone by SMS — and later through other channels. Everything runs on the phone: no
cloud service in the execution path.

**Status:** the engine, plugin system and command channel are implemented and tested on the JVM
(`./gradlew :core:test :app:testDebugUnitTest`). **Nothing has been validated on a phone yet**;
that starts when the agent phone is set up. Start with the
[decision report](docs/vision/decision-report.md) and the [vision documents](docs/vision/README.md).

## How it works

```
 SMS "WHATSAPP SEND 5511999: on my way #17-48291360"
   │
   ▼
 Channel adapter (SMS)  ─►  Command router  ─►  Plugin skill  ─►  Interpreter  ─►  Accessibility service
   sender allowlist         grammar, one-time      (.agp package)     targets, limits,      clicks, types,
                            code, policy, audit                       capability guard      reads the screen
   ▲                                                                                              │
   └──────────────────────────────── short reply ("Sent to *******99") ◄──────────────────────────┘
```

| Piece | What it does | Where |
|-------|--------------|-------|
| **Plugins** (`.agp`) | A zip of YAML files per app: commands, skills, reusable flows, named targets and screens. No code; installed only on the phone after the owner approves its permissions | [plugins.md](docs/vision/plugins.md), `core/.../plugin`, [`plugins/`](plugins) |
| **Workflow language** | Actions plus `if`, `first_that_works`, `try`, `call`, `set`, `assert`, `return`, `expect`, templates `${var\|filter}`, global run limits, structured error codes | `core/.../dsl`, `app/.../dsl/Interpreter.kt` |
| **Targets** | Elements described by intent, role, exact hints and region; exact hints first, then a ranker that refuses to guess (`E_LOW_CONFIDENCE`) | `core/.../resolve` |
| **Capability guard** | A plugin only sees and operates the apps, and opens the links, the owner approved | `core/.../plugin/CapabilityGuard.kt` |
| **Command router** | Channel-neutral: sender allowlist, T9-friendly grammar, printed one-time-code sheet, risk × channel-trust policy, two-step confirmation for risk 5, paged replies, hash-chained audit | `core/.../channel`, `core/.../security` |
| **SMS channel** | Receives commands and sends replies | `app/.../channel/sms` |
| **Admin screen** | On the phone only: install plugins, allowed senders, code sheet, local console, audit | `app/.../admin/AdminScreen.kt` |

## Commands

```
HELP                                    list commands (allowed senders only)
<KEYWORD> <VERB> <arguments> #<n>-<code>  run a plugin command, e.g. WHATSAPP SEND 5511999: hi #17-48291360
STATUS #<n>-<code>                      codes left and installed plugins
MORE | RESEND | CANCEL                  paging, last reply, drop a pending confirmation
OK <word> #<n>-<code>                   confirm a risk-5 command
STOP                                    disable remote commands (no code needed; re-enable on the phone)
```

## Workflow language (used inside plugins)

| Construct | Example |
|-----------|---------|
| Parameters and variables | `params: { phone: {}, text: { default: "" } }`, `set: { url: "https://wa.me/${phone}" }` |
| Templates and filters | `"${text\|urlencode}"`; filters `urlencode`, `upper`, `lower`, `trim`, `mask`; lists render one item per line, `${items.size}`, `${items.0}` |
| Targets | `click: { target: { intent: "send the message", role: button, hints: { content_description: "Send" }, region: bottom-right } }` or a named target: `click: { target: send_button }` |
| Conditions | `if: { exists: send_button, then: [...], else: [...] }`; `exists`/`not_exists` (a target name, a target, or hints like `{ text: "OK" }`), `screen_is`, `equals`, `contains`, `is_set`, `not`, `all`, `any` |
| Fallbacks and errors | `first_that_works: [...]`, `try: { do: [...], on_error: [...] }` (`${error.code}`) |
| Reuse | `call: { flow: open_chat, with: { phone: "${phone}" }, into: result }` |
| Verification | `expect:` on any action, `assert:` as a step |
| Result | `return: "Sent to ${phone\|mask}"` |
| Unexpected dialogs | `interrupts.yaml`: rules (`when` + limited `do`) checked before every screen action |

Actions: `launch_app`, `open_url`, `click`, `type`, `read_text`, `read_list`, `scroll`, `scroll_until`,
`wait`, `wait_for`, `back`, `home`, `log`. Every action accepts `retries`, `retry_delay`, `timeout`
and `on_failure` (`abort`, `continue`, `retry(n, ms)`).

## Plugins and the `agp` tool

```bash
./gradlew :agp:installDist
AGP=tools/agp/build/install/agp/bin/agp
$AGP validate plugins/whatsapp --libs plugins/libraries
$AGP build    plugins/whatsapp --libs plugins/libraries -o build/whatsapp.agp
$AGP inspect  build/whatsapp.agp
$AGP test     plugins/whatsapp --libs plugins/libraries        # replay tests/ against fixtures/
$AGP targets  plugins/whatsapp my_screen_dump.xml --libs plugins/libraries
```

`agp test` runs each skill test with the same engine as the phone, against recorded screens, in
virtual time. The WhatsApp example has five (sent, not confirmed, chat does not open, a dialog in
the way, read chat).

`agp targets` checks a plugin against a real screen: dump it with
`adb shell uiautomator dump /sdcard/s.xml && adb pull /sdcard/s.xml` (the file stays on your computer).

## Building and testing

Prerequisites: JDK 17, Android SDK (API 34 platform).

```bash
./gradlew :core:test :app:testDebugUnitTest   # JVM tests, no device needed
./gradlew :app:assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
```

On the phone (Android 13+): install the APK, open **App info → ⋮ → Allow restricted settings**,
enable the accessibility service, grant SMS permission, set a screen-lock PIN, then use the admin
screen to add allowed senders, generate a code sheet and install plugins.

## Project structure

| Module | Contents |
|--------|----------|
| `:core` (Kotlin/JVM, no Android) | Engine and actions behind a `DevicePort` (`engine/`), replay device and runner (`replay/`), workflow language and interpreter (`dsl/`), action steps (`parser/`), targets and ranker (`resolve/`), screen snapshots and `uiautomator` XML (`ui/`), plugin packages (`plugin/`), command channel (`channel/`), code sheet and audit (`security/`), error codes and results (`engine/`) |
| `:app` (Android) | `AndroidDevicePort` over the accessibility service, live snapshots, SMS adapter, agent coordinator and storage (`agent/`), Keystore keys, admin screen |
| `:agp` (`tools/agp`, Kotlin/JVM) | Command-line tool for plugin authors |

## Known limitations

| Limitation | Notes |
|-----------|-------|
| Not validated on a device | Next step once the agent phone is ready (spikes in the [roadmap](docs/vision/roadmap-risks.md)) |
| The phone must be unlocked with the screen on to automate apps | Waking and unlocking (`wake_screen`, `unlock`) are not implemented yet |
| Secrets (`type_secret`) not implemented | Needed for bank logins; comes with the agent phone |
| Screens marked `FLAG_SECURE` cannot be captured | Targets work on the accessibility tree, which is unaffected |
| Sideload only | Accessibility-based automation is not allowed on the Play Store |
| The example WhatsApp plugin is not validated | Its hints are guesses until checked with `agp targets` on a real dump |

## Architecture Decision Records

| ADR | Topic | Status |
|-----|-------|--------|
| [ADR-001](docs/adr/ADR-001-kotlin-language.md) | Kotlin as development language | Accepted |
| [ADR-002](docs/adr/ADR-002-snakeyaml-parser.md) | SnakeYAML 2.x as YAML parser | Accepted |
| [ADR-003](docs/adr/ADR-003-native-accessibility-service.md) | Native AccessibilityService over UI Automator | Accepted |
| [ADR-004](docs/adr/ADR-004-compose-over-xml.md) | Jetpack Compose over XML layouts | Accepted |
| [ADR-005](docs/adr/ADR-005-poc-scope-trims.md) | POC scope trims | Superseded by the vision documents |
| [ADR-006](docs/adr/ADR-006-laya-decision-layer.md) | Laya as local decision layer for element resolution | Accepted |
| [ADR-007](docs/adr/ADR-007-declarative-yaml-plugins.md) | Declarative plugin packages (zip of YAML files), intelligence in the base app | Accepted |
| [ADR-008](docs/adr/ADR-008-channel-abstraction.md) | Channel abstraction, SMS as the first contact channel | Accepted |
