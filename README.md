# Android Automation Agent

A dedicated Android phone that operates apps for you (WhatsApp, a bank app, …) on commands sent
from a dumbphone by SMS — and later through other channels. Everything runs on the phone: no
cloud service in the execution path.

**Status:** the engine, plugin system and command channel are implemented and tested on the JVM
(`./gradlew :core:test :app:testDebugUnitTest`). **Nothing has been validated on a phone yet**;
that starts when the agent phone is set up. The project is specified with
[Spec Kit](https://github.com/github/spec-kit): start with the [specs index](specs/README.md) and the
[constitution](.specify/memory/constitution.md).

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
| **Plugins** (`.agp`) | A zip of YAML files per app: commands, skills, reusable flows, named targets and screens. No code; installed only on the phone after the owner approves its permissions | [spec 002](specs/002-plugin-packages/spec.md), `core/.../plugin`, [`plugins/`](plugins) |
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
| Languages | `i18n/pt.yaml`, `i18n/en.yaml` with `key: text`; use `${t.key}` anywhere; the phone's language picks the texts |

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
$AGP targets  plugins/whatsapp my_screen_dump.xml --libs plugins/libraries --lang pt
```

Signing ([spec 006](specs/006-package-signing/spec.md)): developers sign packages like APKs. Unsigned packages still install, with a
warning that their identity could not be verified; financial plugins need a trusted signer.

```bash
$AGP keygen -o ~/keys/ana.key --name "Ana"            # passphrase from AGP_KEY_PASSWORD or the terminal
$AGP build  plugins/whatsapp --libs plugins/libraries -o build/whatsapp.agp --key ~/keys/ana.key
$AGP verify build/whatsapp.agp                        # prints the signer's key fingerprint
$AGP fingerprint ~/keys/ana.pub                       # share this with users so they can trust the key
```

`agp test` runs each skill test with the same engine as the phone, against recorded screens, in
virtual time. The WhatsApp example has six (sent, not confirmed, chat does not open, a dialog in
the way, read chat, and a phone set to English with a changed button id).

`agp targets` checks a plugin against a real screen: dump it with
`adb shell uiautomator dump /sdcard/s.xml && adb pull /sdcard/s.xml` (the file stays on your computer).

## Downloads

Every merge to `main` publishes a [release](https://github.com/onova-tech/android-automation-app-poc/releases)
with the signed APK (`android-automation-<version>.apk`), the plugin tool (`agp-<version>.zip`, needs
Java 17: unzip and run `bin/agp`) and `SHA256SUMS`. Pull requests run the tests
(`.github/workflows/ci.yml`); `main` only changes through pull requests.

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
| Not validated on a device | Next step once the agent phone is ready (spikes in the [specs index](specs/README.md#validation-spikes)) |
| The phone must be unlocked with the screen on to automate apps | Waking and unlocking (`wake_screen`, `unlock`) are not implemented yet |
| Secrets (`type_secret`) not implemented | Needed for bank logins; comes with the agent phone |
| Screens marked `FLAG_SECURE` cannot be captured | Targets work on the accessibility tree, which is unaffected |
| Sideload only | Accessibility-based automation is not allowed on the Play Store |
| The example WhatsApp plugin is not validated | Its hints are guesses until checked with `agp targets` on a real dump |

## Specifications (spec-driven development)

Specs live in [`specs/`](specs/README.md), one folder per feature (`spec.md`, `research.md` with the
decisions that used to be ADRs, `plan.md`, `tasks.md`). Project principles are in
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

New work starts with the Spec Kit skills in Claude Code:

```
/speckit-specify <what and why>   →  specs/NNN-name/spec.md
/speckit-clarify                   (optional) resolve ambiguities
/speckit-plan                      →  plan.md, research.md, data-model.md, contracts/
/speckit-tasks                     →  tasks.md
/speckit-implement
```
