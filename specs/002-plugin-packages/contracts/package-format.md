# Contract: `.agp` package format (schema 1)

## Layout

```
whatsapp-1.2.0.agp
├── plugin.yaml          # manifest: identity, app binding, capabilities, secrets, libraries
├── commands.yaml        # verbs → skills
├── skills/              # public skills, one per file
├── flows/               # private reusable sub-flows
├── targets/             # named elements, grouped by screen
├── screens/             # screen signals
├── interrupts.yaml      # interrupt rules (004)
├── i18n/                # <lang>.yaml text per language (005)
├── lib/<id>/            # vendored libraries (build output)
├── tests/               # replay tests (004)
├── fixtures/            # redacted recorded screens (.xml / .json)
├── README.md
├── PACKAGE.lock         # generated
└── PACKAGE.sig          # optional signature (006)
```

Only `plugin.yaml`, `commands.yaml` and one skill are required.

## `plugin.yaml`

```yaml
schema: 1
plugin:
  id: whatsapp
  name: WhatsApp
  version: 1.0.0
  category: messaging           # messaging | financial | utility — a suggestion (007)
  default_language: pt
app:
  package: com.whatsapp
  tested_versions: ["2.26.x"]
capabilities:
  ui_automation: [com.whatsapp]
  read_screen:   [com.whatsapp]
  deeplinks:     ["https://wa.me/*"]      # URL patterns allowed for open_url
  sms_reply: true
libraries:
  android-common: 0.1.0
secrets: []                     # e.g. - { name: password, prompt: "Bank password" }
```

## `commands.yaml`, skills and flows

```yaml
# commands.yaml
commands:
  - { verb: SEND, skill: send, args: "<phone>: <text>" }

# skills/send.yaml
skill: send
params: { phone: {}, text: {} }
steps:
  - call: { flow: open_chat, with: { phone: "${phone}" } }
  - type:  { target: message_box, text: "${text}" }
  - click: { target: send_button, expect: { exists: { text: "${text}" } } }
return: "Sent to ${phone|mask}"
```

## `PACKAGE.lock`

```
agp-lock 1
file <sha256> <path>
library <id> <version>
```

One entry per line, sorted. Its SHA-256 is the package hash.

## `agp` commands

```bash
./gradlew :agp:installDist
AGP=tools/agp/build/install/agp/bin/agp
$AGP validate plugins/whatsapp --libs plugins/libraries
$AGP build    plugins/whatsapp --libs plugins/libraries -o build/whatsapp.agp [--key dev.key]
$AGP inspect  build/whatsapp.agp
$AGP test     plugins/whatsapp --libs plugins/libraries
$AGP targets  plugins/whatsapp screen.xml --libs plugins/libraries [--lang pt]
$AGP keygen | sign | verify | fingerprint        # 006
```

Dump a screen with `adb shell uiautomator dump /sdcard/s.xml && adb pull /sdcard/s.xml`;
redact it before committing it as a fixture.
