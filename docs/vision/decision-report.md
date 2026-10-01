# Decision Report — Where the Project Stands

| Field | Value |
|-------|-------|
| **Date** | 2026-10-01 |
| **Audience** | The owner, to check the direction quickly and close open points in one pass |
| **Reading time** | About 10 minutes. Section 1 alone is enough to judge the direction |

## 1. In one page

**Goal.** A dedicated Android phone (the *agent*) runs 24x7 and operates apps for you (WhatsApp, Nubank, …). You reach it from a dumbphone by SMS today, and through other channels later. Everything runs on the phone; no cloud service.

**What exists today (2026-10-01).** The POC was replaced by the target design, in two open pull requests (#2 engine and plugins, #3 command channel and admin screen): workflow language with control flow and verification, self-healing targets with a ranker that refuses to guess, `.agp` plugin packages with a capability guard and the `agp` tool, the SMS command router with the printed code sheet, policy, two-step confirmation and audit, and an on-device admin screen. 102 JVM tests pass. **None of it has run on a phone yet.**

**What we learned from real tests.** On your S20 FE, the Nubank app exposes the **balance and the statement** to the accessibility layer, which is what the banking read path needs. Its login uses the **phone's own lock screen credential**, which forced two decisions: a separate agent phone, and a PIN lock on it.

**The biggest open question.** Whether Nubank accepts **our** accessibility service and whether that service can type the PIN into the system prompt. Only the agent phone can answer this. If the answer is no, banking drops out and the project continues with messaging.

**Direction check.** The architecture favors safety over convenience: plugins are data, ML only chooses between known options, money cannot move in v1, and nothing secret travels by SMS. If that trade-off is what you want, the direction is right.

## 2. Decisions that shape the project

Status legend: ✅ decided · 🟡 proposed (needs your approval) · 🔬 depends on a test · 🔁 changed during the discussion

### 2.1 Product scope

| # | Decision | Why | Cost / trade-off | Status |
|---|----------|-----|------------------|--------|
| P1 | The agent is a **dedicated phone**, not your daily phone | It stores the Nubank password and its own PIN | Buying and maintaining a second phone | ✅ 🔁 (was the S20 FE until Spike 4) |
| P2 | **Contact through channels; SMS first**, others later | Your requirement; a dumbphone can only do SMS today | A small extra layer (channel gateway) | ✅ (ADR-008 ✅) |
| P3 | **Banking v1 is read-only**: balance and statement | Limits damage while trust is built; no money can move | No transfers until a later phase with extra controls | ✅ |
| P4 | First bank: **Nubank** | Your choice | Flutter + server-driven UI: screens can change without an app update | ✅ 🔁 (was Itaú) |
| P5 | WhatsApp on a **dedicated number**, same SIM that receives commands | Protects your main WhatsApp from a ban | Anyone in that WhatsApp knows the command number (handled by allowlist + codes) | ✅ |
| P6 | Installing apps by SMS is **out of scope** | Too much power for a remote command | You install apps in person | ✅ |

### 2.2 Architecture

| # | Decision | Why | Cost / trade-off | Status |
|---|----------|-----|------------------|--------|
| A1 | **Plugins are packages: a zip of small YAML files** with reusable flows and shared libraries; still no code; all intelligence in the base app | Your requirement; a single YAML file grows too big; reuse without copy-paste; a plugin cannot steal secrets or bypass limits | A build tool and a safe unpacker; every missing capability needs a base-app release | ✅ 🔁 (was one YAML file; ADR-007 🟡) |
| A1a | **Libraries are vendored** into each package at build time (v1) | The hash you approve covers everything that runs; a library update cannot change an approved plugin | A library fix means rebuilding the plugins that use it | 🟡 |
| A2 | **Structured surfaces before screen automation** (deeplinks, notification replies, then UI) | Far more robust than finding buttons | Each plugin must be designed per app | ✅ |
| A3 | **Self-healing element resolution**: cache → exact hints → heuristic ranking → Laya → verify after acting | Survives app redesigns without editing plugins | More components; needs training data | 🟡 |
| A4 | **Laya** as the local decision model (text only, picks among known candidates) | Runs offline, cannot invent actions | ~650 MB; needs fine-tuning; very new project. Kept only if it beats a simple baseline | ✅ 🔬 (ADR-006 accepted; reversible by Spikes 1–2) |
| A5 | **Laya cannot see images.** OCR can feed it text, but **not for Nubank** (screens cannot be captured) | Model capability; Nubank protection | Nubank depends entirely on the accessibility tree | ✅ |
| A6 | The base app has **no Internet permission**; network channels, if ever, go in a separate companion app | Makes "self-contained" verifiable | A second app for network channels | ✅ (ADR-008) |
| A7 | Development stays on **Android 13** behavior as the baseline | Accessibility rules tighten in newer versions | Conflicts with "keep the agent phone patched" (see D1 in section 3) | 🔬 |

### 2.3 Security

| # | Decision | Why | Cost / trade-off | Status |
|---|----------|-----|------------------|--------|
| S1 | **The SMS sender number is never a credential** | Numbers can be spoofed, SIMs can be swapped | Every command needs a code | ✅ |
| S2 | **Printed one-time-code sheet**, one code per command | A dumbphone runs no authenticator; paper is separate from the phone | You carry a sheet; regenerate when it runs out | ✅ |
| S3 | **Encrypted SMS (profile B)** as an option, on a Java ME dumbphone | Hides content from the carrier | Needs a client app and a compatible dumbphone; KaiOS appears blocked | 🟡 🔬 (Spike 8) |
| S4 | **Secrets stored on the agent phone**, typed only by the base app, never readable by plugins or sent anywhere | Needed for unattended login | Whoever controls the unlocked phone controls what it can do | ✅ |
| S5 | **Agent phone uses a PIN lock** (not a pattern); the agent types it | Nubank requires the device credential; a pattern grid cannot be automated reliably | After every reboot a human must unlock once; the phone stores its own PIN | ✅ 🔁 (was "no lock") 🔬 typing still untested |
| S6 | **Admin operations only in person** (install plugins, change limits, beneficiaries) | Remote compromise cannot change the rules | You must be at the phone to change configuration | ✅ |
| S7 | **Risk levels by command and by channel trust** | A weak channel cannot do what a strong one can | More policy to configure | ✅ |
| S8 | **Fail closed**: when unsure, stop and report | A wrong click can be irreversible | Some commands will fail and need a retry | ✅ |
| S9 | **Plugin packages are signed by their developers** (ECDSA P-256, like APKs). Unsigned ones install only after a warning that their identity could not be verified; **financial plugins need a key you trust**; updates must keep the same signer | Origin and integrity; stops another package from taking over an installed plugin | Developers must keep their key safe; trusting a key is a manual step on the phone | ✅ (ADR-009) |

### 2.4 Things explicitly ruled out

| Ruled out | Why |
|-----------|-----|
| Bypassing biometrics, face checks or any app protection | Not acceptable, and would break bank terms |
| A generative AI agent deciding actions | Unpredictable and vulnerable to manipulation by on-screen text |
| Cloud services in the execution path | Violates self-containment |
| Remote installation of plugins or apps | Supply-chain risk |
| KaiOS dumbphone client | SMS permission requires certified apps; no sideloading on 3.x/4.x |

## 3. What I need from you

Grouped by what blocks what. Answer the **blocking** ones first; the rest can come any time.

### 3.1 Blocking — decisions

| # | Question | What it blocks | My recommendation |
|---|----------|----------------|-------------------|
| D1 | **Which phone will be the agent?** Pick a model, or approve these criteria and I will propose models: still receiving security updates, ≥ 6 GB RAM, Samsung or Pixel. **Trade-off:** a patched phone will run Android 14+, which adds rules that let apps hide screens from automation | Spike 4 completion, Spikes 3 and 6, all device work | A recent mid-range Samsung or Pixel still under updates; accept Android 14+ and test early |
| D2 | ~~Approve ADRs 006, 007, 008~~ **Answered 2026-10-01: accepted.** | — | — |
| D3 | ~~Autonomy~~ **Answered 2026-10-01:** commit and push without asking; branches `feature/<name>`; Phase 2 started; **finished feature branches become pull requests**; keep working without waiting for check-ins. | — | — |

### 3.2 Blocking — actions only you can do

| # | Action | Why only you |
|---|--------|--------------|
| X1 | Buy the agent phone, set a **PIN lock**, enable developer options, install Tailscale, and enable wireless debugging when we test | Physical device and accounts |
| X2 | Get the **dedicated SIM** (WhatsApp + SMS commands) and set a **SIM PIN with the carrier** against SIM swap | Your identity and contract |
| X3 | On the agent phone, **log in to Nubank once** (it may ask for a face check or device registration) | Only the account owner can do it |

### 3.3 Not blocking — preferences (defaults apply if you do not answer)

| # | Question | Default if you do not answer |
|---|----------|------------------------------|
| N1 | Languages: Portuguese only, or also English/Spanish? | Portuguese only |
| N2 | Single user, or several people controlling the agent? | Single user (you) |
| N3 | How much statement detail in an SMS reply? | Masked amounts, last 5 entries, short descriptions |
| N4 | Code sheet: transcribe by hand or print? | Shown once on the phone; you transcribe |
| N5 | How you install plugins: adb, file picker, or a shared folder? | File picker in admin mode |
| N6 | Invest in OCR / icon recognition for apps without labels? | Not now; revisit after Phase 4 |
| N7 | Dumbphone: try a Java ME model for encrypted SMS (Spike 8), or plain SMS only for now? | Plain SMS only for v1; Spike 8 when convenient |
| N8 | Which second channel, if any, after SMS? | None planned yet |
| N9 | Accept the terms-of-service risks: WhatsApp may ban the dedicated number; Nubank may object to automation | Must be your explicit yes before the WhatsApp and Nubank plugins go live |
| N10 | For the later transfer phase: separate account with a small balance? | Decide when transfers are on the table |

## 4. What happens next

Done without the agent phone (PRs #2 and #3): Phase 2 engine foundation and the device-independent part of Phase 3.

Waiting for the agent phone (X1–X3 above):

1. Install the APK, run the admin screen, and validate the WhatsApp plugin with `agp targets` on real screen dumps.
2. Finish Spike 4 (our accessibility service with Nubank, PIN typed into the system prompt), then Spikes 3 (SMS reliability) and 6 (PIN unlock, reboots).
3. Implement what needs the device: `wake_screen`/`unlock`, `type_secret`, notification replies, the "needs unlock" notice after reboots.
4. Spike 1 (Laya speed on the phone) can run on the S20 FE at any time.

I will report progress at milestones, and only interrupt you when a test result changes a decision.

## 5. Risks that could change the direction

| Risk | Effect if it happens | Early signal |
|------|----------------------|--------------|
| Nubank rejects our accessibility service, or the PIN cannot be typed into the system prompt | Banking drops out; messaging continues | Spike 4 on the agent phone |
| Android 14+ on a patched phone hides app content from automation | Some apps become unreadable | Spike 4 on the agent phone |
| Laya does not beat the simple baseline | Laya is dropped; the heuristic resolver stays | Spike 2 |
| WhatsApp bans the dedicated number | Messaging channel through WhatsApp is lost; SMS still works | Low volume and pacing from day one |
| A reboot leaves the agent unreachable until you unlock it | Downtime until you are physically there | Spike 6; stable power |
| No suitable Java ME dumbphone | No encrypted SMS; plain SMS + code sheet still works | Spike 8 |

## 6. Where to find the details

| Topic | Document |
|-------|----------|
| Architecture and agent phone setup | [README.md](README.md) |
| Contact channels | [channels.md](channels.md) |
| Plugins | [plugins.md](plugins.md) |
| Element resolution and Laya | [laya-resolution.md](laya-resolution.md) |
| Actions and DSL | [action-catalog.md](action-catalog.md) |
| SMS, authentication, banking, threats | [sms-security.md](sms-security.md) |
| Nubank test results | [spike-04-nubank.md](spike-04-nubank.md) |
| Phases, spikes, risks | [roadmap-risks.md](roadmap-risks.md) |
