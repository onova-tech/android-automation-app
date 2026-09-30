# SMS Channel, Command Protocol and Security

| Field | Value |
|-------|-------|
| **Status** | Proposal (draft v0.2) — **needs a security review before any financial implementation** |
| **Depends on** | [README.md](README.md), [action-catalog.md](action-catalog.md), [plugins.md](plugins.md) |

## 1. Central premise

**SMS is neither an authenticated nor a confidential channel.**

- The sender number **can be forged** (sender ID spoofing).
- The content travels in clear text through the carrier.
- The dumbphone's number can be **taken over through SIM swap**.
- Messages can arrive late, duplicated or out of order.

Therefore: **the sender number is never a credential.** At most it is a noise filter. Every effectful action needs **application-layer authentication**.

## 2. SMS channel (L1)

| Aspect | Proposed decision |
|--------|-------------------|
| Receive | `BroadcastReceiver` for `SMS_RECEIVED` (`RECEIVE_SMS`); the app does not need to be the default SMS app |
| Send | `SmsManager.sendMultipartTextMessage` (`SEND_SMS`) |
| Sender filter | Allowlist of numbers — **defense in depth, not authentication.** Messages from any number not on the list are dropped **before parsing**, with no reply and no failure counting |
| Length | 160 chars in GSM-7; 70 with accents/emoji (UCS-2). Replies are short **plain text without accents**; paging with `MORE` |
| Cost | Every reply costs an SMS; limit reply length and frequency; daily reply cap |
| Rate limits | Max commands per minute/hour; over the limit → drop silently and log |
| Order and duplicates | Discard by already-seen code index |
| Expiry | A command whose timestamp is too old (short window) is rejected |
| Restricted settings | On Android 13, sideloaded apps may have accessibility, notification-listener (and possibly SMS) access blocked until "Allow restricted settings" is enabled |
| Owner alert | Optional local notification to whoever is near the agent phone on security events |

**No privacy on the wire:** replies must not include complete sensitive data. Balances and keys are masked or rounded; message bodies are truncated.

### 2.1 Transport profiles

The channel has two profiles. The same command grammar, policy and audit sit on top of both.

| | **Profile A: plain SMS + code sheet** | **Profile B: encrypted binary SMS** |
|--|---------------------------------------|-------------------------------------|
| Dumbphone needs | Nothing; any phone works | A small client app on the dumbphone (see 2.2) |
| Confidentiality | **None**: the carrier reads commands and replies | Content is encrypted; the carrier still sees sender, recipient, time and size |
| Authentication | Printed code sheet on each command | Authenticated encryption with a per-message counter, plus a PIN in the client; the sheet stays as a **second factor for level 5** |
| Development cost | None on the dumbphone side | A client per platform, plus a shared frame format and test vectors |
| Status | Baseline; always available | Optional; depends on Spike 8 |

**Profile B frame (sketch):**

```
version (1 B) | counter (8 B) | ciphertext | tag (16 B)      ≤ 140 bytes per data SMS
```

- Sent as a **port-addressed binary SMS** (8-bit data, no Base64 overhead). About 115 bytes are left for the command, so commands stay short; a longer text is split into numbered frames.
- Authenticated encryption: AES-GCM, or AES-CTR/CBC plus HMAC-SHA256 truncated to 16 bytes. The final choice depends on what the chosen dumbphone can run. Use an established library and publish **test vectors**, so the Android side and the dumbphone client can be checked against each other. Do not design a custom cipher.
- **Replay protection:** the agent stores the highest counter seen per client and rejects anything lower or equal. If the dumbphone loses its storage, the counter is resynchronized in admin mode.
- **Key provisioning (physical presence):** the key is created in the agent's Keystore and delivered once, by a personalized installer, by typing about 26 base32 characters, or by Bluetooth/card. It never travels over SMS.
- **Key at rest on the dumbphone:** wrapped with a PIN typed in the client, because Java ME storage is not secure storage. Theft of the dumbphone then needs the PIN as well.
- **Replies:** the agent sends the encrypted reply as a data SMS **and** a short plain-text "reply ready" SMS with no sensitive content, so the user is alerted even when the client is not running. A `RESEND` command fetches the last reply again (the agent keeps it for a few minutes). This matters because a data SMS to a port is invisible to the phone's inbox: if nothing is listening, it is lost.
- **Sender allowlist and rate limits** (section 2) still apply before decryption.

### 2.2 Choosing the dumbphone (none bought yet)

Requirement: it must **send and receive** binary SMS from a program we control.

| Platform | Verdict | Why |
|----------|---------|-----|
| **Java ME (MIDP 2.0 / CLDC 1.1, with WMA)** | **Preferred, must be tested on real hardware** | MIDlets can send and listen for port SMS with the Wireless Messaging API. Receiving while the app is closed needs a push registration, which some phones restrict for unsigned apps |
| **KaiOS** | **Not recommended** | The SMS permission (`sms`) needs a *certified* app in the KaiOS 2.5 documentation, which ordinary developers cannot install. KaiOS 3.x and 4.0 cannot sideload apps, and no 3.x device is known to have DevTools. Only select KaiOS 2.5 models allow debugging |
| Nokia S30+ and similar closed platforms | Not viable | No third-party app runtime, as far as I know |
| **A locked-down Android phone** | Fallback | Easiest to develop (same language, real crypto libraries, adb), at the cost of a smartphone's battery life and temptation. It can run the same Profile B protocol |

**I could not find a reliable list of Java ME dumbphones that are currently sold.** The only sources found were reseller guides, whose claims do not hold up (for example, they describe KaiOS phones as running `.jar` files). Do not choose by a model listing. Pick a candidate that advertises MIDP 2.0/CLDC 1.1, WMA, 4G with VoLTE, and a way to load apps (memory card, USB or Bluetooth), and validate it with Spike 8 before committing.

## 3. Command grammar

Designed for **typing on a numeric keypad (T9)**: short, no hard-to-type symbols, case- and accent-insensitive. Commands are English keywords, but the verb set per app comes from each plugin's `commands:` list.

```
<PLUGIN> <VERB> <arguments> #<index>-<code>
```

| Command | Example | Risk level |
|---------|---------|-----------|
| Help | `HELP` | 0 (allowed sender only) |
| Device status | `STATUS #17-48291360` | 1 |
| Read unread | `WA READ #18-90417725` | 2 |
| Send message | `WA SEND maria: on my way #19-31658804` | 3 |
| Balance | `ITAU BALANCE #20-77120956` | 4 |
| Transfer *(not in v1)* | `ITAU TRANSFER 50 maria #21-…` → two-step confirmation | 5 |
| Stop everything | `STOP` | Always allowed (fails toward the safe side) |

- **Aliases, not numbers:** `maria` is resolved from the local alias table. Ambiguity ⇒ the agent asks back (`Maria Silva or Maria Lima? Reply 1 or 2`).
- **Deterministic parser first.** Laya (Choice over ≤ 20 intents) is only a typo-tolerance fallback when the grammar fails, and **never** for risk levels 4–5: there, malformed text is rejected.
- **No detailed reply to invalid commands** (so an attacker gets no hints).

## 4. Authentication: printed one-time-code sheet (D3)

The dumbphone runs no apps, and you chose a printed sheet over a hardware token.

### 4.1 Design

- **Pairing (admin mode, physical presence):** the base app generates a random key `K` inside the Android Keystore. It **never leaves** the device. The app derives a sheet of **N one-time codes** (suggested N = 100), each an 8-digit number computed as `truncate(HMAC(K, sheet_id ‖ index))`.
- **Display once.** The sheet is shown a single time in admin mode for the owner to transcribe or print through a path the owner controls. The app keeps no export file and no screenshot-friendly copy afterwards.
- **Use:** each command ends with `#<index>-<code>`, for example `#17-48291360`. The agent recomputes the code for that index and checks that the index is **unused**.
- **Burn on use:** a valid index is marked used **before** the command executes, so it can never run twice (this also gives the command its idempotency ID; see the action catalog). Any unused index is accepted, in any order.
- **Two-step commands consume two codes** (level 5 uses two).
- **Low-stock warning:** the agent tells the owner by SMS when fewer than about 15 codes remain. Regenerating the sheet happens in admin mode and invalidates the old one.
- **Lockout:** 5 consecutive failures ⇒ escalating lock (for example 15 min, then hours). After a total failure budget is exhausted, only admin mode on the device unlocks it. **Only failures from allowlisted senders count**, so strangers cannot lock the agent by guessing codes.
- **Lost sheet or lost dumbphone:** send `STOP` (needs no code; it can only disable the agent, so forging it is at worst a nuisance). Re-enabling and regenerating the sheet require physical presence.

### 4.2 What this protects against — and what it does not

| Scenario | Effect |
|----------|--------|
| Attacker forges your number, or takes over the SIM (SIM swap) | Cannot produce a valid code without the sheet ⇒ commands rejected |
| Replay of a captured SMS | Index already burned ⇒ rejected |
| Brute force | 8 digits, lockout after a few failures ⇒ impractical remotely |
| Attacker who **captures a fresh code in transit and blocks the original delivery** | Can use that one code once. Codes are **not bound to command content**, because a human cannot compute a content-bound code. Mitigation: two-step confirmation for level 5 (the second code is unknown to the attacker), agent-side echo of what it will do, allowlisted beneficiaries, and limits (section 6) |
| Someone steals the paper sheet | Has codes, but still needs to send SMS from an allowed number (spoofing or SIM swap) and hits every limit. Treat the sheet like a card; `STOP` and regenerate |
| Someone steals the agent phone | The sheet is irrelevant; see section 6 and the lock-screen decision in section 8 |

The device stores only `K` (in the Keystore) and the used-index set, not the codes themselves.

### 4.3 Session option

If burning a code on every low-risk command is too costly, an **optional** `LOGIN #<index>-<code>` can open a short session (for example 10 minutes) for levels ≤ 3. It is off by default: within a session, a forged sender can issue commands, so a longer window widens exposure. Levels 4–5 never use sessions.

## 5. Risk levels and policy (`PolicyEngine`)

| Level | Examples | Requirements |
|-------|----------|--------------|
| 0 | `HELP`, `STOP` | Allowed sender only (`STOP` even less) |
| 1 | `STATUS` | + code |
| 2 | Read messages | + code; truncated replies |
| 3 | Send message | + code; aliased contacts only; hourly limit |
| 4 | Balance / statement | + code; masked reply; allowed hours |
| 5 | **Transfer** *(not in v1)* | Everything in section 6.2 |

Policy is local data. **It cannot be changed by SMS or by a plugin.** Changing policy, aliases, beneficiaries or limits requires **admin mode on the device**. The plugin's declared category can only raise a skill's level above these floors ([plugins.md](plugins.md) section 9).

## 6. Banking (first bank: Itaú)

### 6.0 Scope of the first version (D7)

The first version is **read-only: balance and statement.** There is no transfer skill. The transfer controls in 6.2 are the **entry criteria for a later phase**, not part of v1.

Controls for v1:

| Control | Detail |
|---------|--------|
| Risk level | 4 for every banking command |
| Reply detail | Balance and statement replies are **masked or rounded by default** (for example `R$ 1.2xx`); the owner may opt into more detail in admin mode. SMS is clear text on the carrier network |
| Statement size | Last N entries only, descriptions truncated, no account numbers |
| No payment path | The plugin is marked `scope: read_only`; the base app then refuses to click any target whose text or description looks like a payment or transfer entry (Pix, transfer, pay, and equivalents in configured languages). This is **defense in depth**, not a guarantee, and it is not a substitute for testing |
| Secret use | Login uses `type_secret`; nothing else touches the password |
| Allowed hours | Optional window for banking commands |
| Version gate | Unvalidated app version ⇒ refuse |

### 6.1 Technical and legal reality

- **Feasibility is unknown.** We have not tested the Itaú app. Bank apps commonly detect accessibility services that are not assistive tools, mark screens as sensitive (`FLAG_SECURE`, and on Android 14+ sensitive accessibility data), or refuse to run. Spike 4 answers this **before** we design more.
- **Things to check in the Itaú app (Spike 4):**
  1. Does it launch and stay usable with our accessibility service enabled?
  2. Does it show a warning, refuse, or log out?
  3. Is its tree readable, or blank / `FLAG_SECURE`?
  4. Which factors does it demand at login and **for each transfer** (password, device token, biometrics, face)? If a biometric or on-device token is required every time, that step is **not automatable** and we will not bypass it.
  5. Does it bind the customer to a registered device, and would moving the login to the agent phone need a new registration?
  6. What limits does the bank enforce on its side (per-transfer, daily, night-time)? (Relevant later, for transfers.)
  7. **Does it refuse to run, or lose features, when the device has no secure lock screen?** (See the lock-screen decision in section 8.)
- **Bank terms and liability:** automating the app may violate its terms and could affect fraud-liability coverage. Repeated automated access might also trigger anti-fraud measures that lock the account. Check with the bank.
- **Agent-device risk (D4):** to act on its own, the agent stores the bank password. **Whoever gets the phone, or controls it remotely, gets the account access it holds.** This is the owner's accepted trade-off; the controls below bound the damage.

### 6.2 Mandatory controls before a `transfer` skill exists (not in v1)

Controls 1–4 are **enforced locally**, so they still hold if SMS authentication is defeated.

1. **Beneficiary allowlist.** Only beneficiaries pre-registered in admin mode. A new beneficiary has a waiting period (for example 24 h) before it can receive funds. A transfer to an unknown destination by SMS is impossible.
2. **Limits:** maximum per operation, per day, and number of transfers per day; allowed hours.
3. **Two-step confirmation ("what you see is what you sign"):**
   ```
   You   → ITAU TRANSFER 50 maria #21-55102938
   Agent → CONFIRM: R$50.00 to MARIA S. (key ***.456-**). Reply: OK 7391 #<index>-<code>
   You   → OK 7391 #22-80417263
   ```
   The confirmation word (`7391`) is random, expires in a few minutes, and ties the confirmation to the data **as re-read by the agent**, not to the original SMS, which may have been altered.
4. **Exact on-screen verification:** amount and recipient read from the accessibility tree on the bank's confirmation screen are compared **literally** with what was confirmed. Any mismatch aborts before the final button.
5. **Deterministic anchor on the final click** (Laya does not decide alone; see [laya-resolution.md](laya-resolution.md) section 5).
6. **Validated app version:** unknown version ⇒ refuse.
7. **No automatic retry:** failure or `E_VERIFY_FAILED` ⇒ never re-run; report and wait for instructions (see idempotency in the catalog).
8. **Receipt:** reply SMS with amount, masked recipient and time; full record in the audit log.
9. **Independent notification:** enable the bank's own transaction alerts on a channel the agent phone does not control (another phone/e-mail).
10. **Strongly recommended, owner's call: a separate operational account with a limited balance,** topped up manually. It is the most effective control because it caps the loss even if everything else fails. If the main account is used, keep bank-side limits as low as practical.

### 6.3 Secret handling (D4)

| Item | Rule |
|------|------|
| Where | Android Keystore key (non-exportable) protecting the stored value; only the base app can decrypt it |
| Entry | Typed **on the device, in admin mode**, when the plugin is installed. Never by SMS |
| Use | Only through `type_secret` into a resolved field. Never in variables, logs, traces, SMS or URLs |
| Removal | Uninstalling the plugin wipes the secret |
| Limit of the protection | The base app must be able to decrypt the value unattended, so the Keystore protects against extraction of the key, **not** against someone who can operate the unlocked device. Physical security and the local limits are what bound that risk |

## 7. Threat model

| # | Threat | Vector | Main mitigation |
|---|--------|--------|-----------------|
| T1 | Forged command (spoofed sender) | SMS showing your number | Code sheet on each command; sender filter only as noise |
| T2 | SIM swap of the dumbphone | Attacker sends/receives as you | Printed sheet; a static PIN would not be enough |
| T3 | Replay of a captured SMS | Interception | One-time indices burned on use; time window |
| T4 | SMS tampered with or read in transit | Carrier network | Two-step confirmation on data re-read by the agent; masked replies; codes not reusable |
| T5 | Theft of the agent phone | Physical access | Local limits, beneficiary allowlist, Keystore, safe location, bank limits, optional operational account |
| T6 | Theft of the dumbphone | Physical access | Needs the sheet as well; `STOP` and physical revocation |
| T7 | Malicious app on the agent device | Install/exploit | Dedicated device, no other apps, no browser, restricted sideloading |
| T8 | Malicious text in received messages | Chat content tries to "command" the automation | Message = data, never command; Laya only sees UI attributes; output is always an enumerated option; OCR is region-filtered |
| T9 | Click on the wrong element after a UI change | UI drift, model error | Post-condition; deterministic anchor; exact comparison; fail closed |
| T10 | Denial of service (SMS flood) | Filling the queue | Rate limits; ignore unauthenticated messages; bounded queue |
| T11 | Social engineering | Tricking the owner into sending a command | Confirmation shows recipient and amount; limits; wait for new beneficiaries |
| T12 | Tampered plugin/model/policy | Installing a fake artifact | Admin-mode install only, hash shown, updates re-approved, no remote install, no auto-update |
| T13 | Leak via logs/debugging | Logs with secrets | Enforced redaction; logs stay local; export only in person |
| T14 | WhatsApp account banned for automation | Terms of service | **Dedicated number on a device where the main account never ran (D8)**, so a ban does not touch the main account. Message only people who saved the number, keep volume low, human-like pacing. A dedicated number reduces the damage; it does not remove the ban risk |
| T15 | Malicious plugin | Plugin abusing capabilities | Capability scoping, use-only secrets, base-app risk floor ([plugins.md](plugins.md) section 11) |
| T16 | Bank anti-fraud locks the account | Repeated automated access | Test with low volume first; bank-side alerts; ask the bank |
| T17 | The command number is not secret (it is the WhatsApp number, D8) | Anyone the account chats with can text it; WhatsApp verification SMS also arrives there | Drop non-allowlisted senders silently; failures from them never count toward lockout; ignore WhatsApp/verification SMS in the command parser; a forged allowlisted sender still needs a valid code (or a valid encrypted message). Also: a SIM swap on this number would hand over the WhatsApp account, so protect the SIM with a carrier PIN |

## 8. Security decisions

1. **Lock screen (provisional, D9): None or Swipe.** A secure lock screen would leave the agent phone unable to recover after any reboot until a human types the PIN (see [README.md](README.md), "Unlock and reboots"), and automating a PIN on the lock screen is unreliable. The price is high physical exposure, so this choice only makes sense together with: a safe location, the read-only first scope (D7), the dedicated phone with no other data, and bank-side alerts on a channel the phone does not control. **Overturned if Spike 4 shows the Itaú app requires a secure lock screen.** Then the fallback is a PIN with manual recovery after reboots, and no automatic unlock.
2. **Revocation:** `STOP` plus physical regeneration of the sheet (section 4.1).
3. **Financial scope:** decided (D7): read-only first. Transfers are a later phase after a review.
4. **Independent review:** external security review before enabling any transfer skill.
5. **Operational account:** relevant when transfers arrive (6.2, item 10). For read-only v1 the account choice matters less, but bank-side alerts still apply.

## 9. Audit and transparency

- **Hash-chained** log: each record includes the previous record's hash, to detect tampering.
- Records: the command (without the code), the authentication decision, the policy applied, every element resolution (stage used and confidence), post-condition verification, the reply sent.
- Local viewer in the app (admin mode only).
- Limited retention; export only in person and redacted.
- **Physical off switch:** the app exposes a "disable automation" control on the device.

## 10. Planned security tests

- Fuzzing the command grammar and the SMS parser.
- Replay, index-burning, time-window and lockout tests.
- Tampering with the two-step confirmation.
- Drift tests: changed screens where the final click must abort.
- Manifest and permission review.
- Recovery after a reboot or power loss mid-transaction (state "unknown" ⇒ do not repeat).
- Plugin tests: capability escape attempts, secret-exfiltration attempts, risk-floor bypass attempts.

## Sources for the dumbphone platforms

- [KaiOS App Permissions](https://developer.kaiostech.com/docs/getting-started/main-concepts/permissions/)
- [KaiOS developer FAQ (kaios.dev)](https://kaios.dev/faq/)
- [What's missing from KaiOS development (kaios.dev)](https://kaios.dev/2024/01/whats-missing-from-kaios-development/)
- [Java ME (Legacy Portable Computing Wiki)](https://lpcwiki.miraheze.org/wiki/Java_ME)
