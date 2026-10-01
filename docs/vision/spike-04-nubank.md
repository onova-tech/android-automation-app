# Spike 4 — Nubank feasibility (partial results)

| Field | Value |
|-------|-------|
| **Date** | 2026-10-01 |
| **Status** | **Partial — read gate passed; the decisive automation checks are still open** |
| **Device** | Owner's daily phone: Galaxy S20 FE 4G, SM-G780G, Android 13, One UI 5.1, Snapdragon 865 (SM8250), ~5.5 GB RAM usable, security patch **2024-09-01** |
| **App** | Nubank `com.nu.production` 10.6.59 (targetSdk 36) |
| **Plan** | [sms-security.md](sms-security.md) section 6.1, [roadmap-risks.md](roadmap-risks.md) Spike 4 |

## Method

- `adb` over Wi-Fi (Android 13 wireless debugging, paired), reached over Tailscale.
- `uiautomator dump` of the accessibility tree on each screen while the owner navigated: login prompt, home, statement.
- Each dump was **redacted on the server before anyone looked at it**: digits replaced with `9`, letters replaced with `a`, except short labels on clickable nodes. Raw XML was deleted at the end. No screenshots were taken.
- Wireless debugging was turned off on the phone afterwards.

`uiautomator` uses the system's `UiAutomation` (instrumentation), **not** a third-party `AccessibilityService`. Banks can treat the two differently, so a pass here does not prove our service will be accepted.

## Results

| Question | Result | Evidence |
|----------|--------|----------|
| Does the tree expose the **balance**? | ✅ Yes | In `content-desc` of buttons, e.g. `Saldo no … de R$ 99,99` (shape) |
| Does it expose the **statement**? | ✅ Yes | One node per entry, 3 lines in `content-desc`: description · `HH:MM · type` · amount (`+` prefix on credits). Day headers (`99 aaa`) and a search field (`EditText`) are separate nodes |
| Are controls labeled? | ✅ Yes | `Voltar`, `Ajuda`, `Mais opções`, `Menu`, `Meus cartões`, `Esconder saldo.` |
| Identifiers | ⚠️ Almost none | 3 of 42 nodes have `resource-id`; `text` is empty; everything is in `content-desc` (Flutter semantics) |
| Did Nubank refuse the dump? | ✅ No | Home and statement dumped normally |
| How does login work? | ⚠️ **System device-credential prompt** | Nubank calls `BiometricPrompt`; the prompt is drawn by Samsung (`com.samsung.android.biometrics.app.setting`). Title, description and a `Usar padrão` button are readable |
| Can a **pattern** be entered? | ❌ Not practical | The grid is one node (`lockPattern`) with no child cells. Only coordinate gestures would work |
| Does our `AccessibilityService` get accepted? | ❓ Not tested | Needs the agent phone (we will not enable a third-party accessibility service on the owner's daily phone) |
| Can the service enter a **PIN** in the system prompt? | ❓ Not tested | Same |
| Behavior without a secure lock screen | ⚠️ Moot | Login requires a device credential or biometrics, so the agent phone **must** have a secure lock |

## Consequences

1. **The banking read path is technically possible** from the tree alone, which matters because OCR is not usable on Nubank.
2. **The agent must be a second phone.** The S20 FE is the owner's daily phone and will not hold automation secrets. It stays a read-only test device.
3. **Lock screen: secure PIN on the agent phone.** The provisional "None/Swipe" decision (D9) is overturned. Use a **PIN, not a pattern**, because a PIN pad is far more likely to expose labeled keys.
4. **The device PIN becomes a stored secret.** To log in to Nubank unattended, the agent must type the device PIN into the system prompt. That PIN is the same one that protects the phone. Whoever can use the unlocked phone can do everything; physical security is the real boundary.
5. **After every reboot the agent is deaf until someone unlocks it** (credential-encrypted storage stays locked). Mitigation to design: a `directBootAware` SMS receiver that can at least reply "agent restarted, needs unlock" before the first unlock, plus *Auto restart* off and a UPS/charger setup that avoids power cuts.
6. **Plugins need a scoped exception for the system prompt.** The Nubank plugin must be allowed to act on the system credential prompt **only** for the device-credential step. That is a new, narrow capability (`device_credential_prompt`), and the base app types the PIN with `type_secret` in `keypad` mode.
7. **Flutter + server-driven UI:** resolution relies on `content-desc` text and structure, which the server can change without an app release. The screen-signal check in [plugins.md](plugins.md) section 10 is required, and Laya's value is highest here.

## Still to test (on the agent phone)

1. Install the base app, enable our accessibility service ("Allow restricted settings" first), and check whether Nubank warns, refuses, logs out, or hides content.
2. With a **PIN** lock, check whether the system prompt exposes labeled keys and whether our service can click them.
3. Check how often Nubank asks for re-authentication, and whether moving the login to a new phone triggers device registration or a face check.
4. Repeat the read path through our service (not `uiautomator`) to confirm the tree is identical.
5. Scroll the statement and confirm older entries load and stay structured.
