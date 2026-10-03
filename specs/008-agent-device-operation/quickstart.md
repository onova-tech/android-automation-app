# Quickstart: provisioning the agent phone (24x7)

Baseline: a **dedicated second phone**, Android 13+, ≥ 6 GB RAM, still receiving security
updates (owner decision O1). Notes assume a Samsung with One UI.

## Install

1. Factory reset; install only the apps the plugins need (no browser, no personal accounts).
2. Build and install: `./gradlew :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   Play Protect may warn about a sideloaded app; accept explicitly.
3. **App info → ⋮ → Allow restricted settings** (Android 13 blocks accessibility, notification
   listener and possibly SMS permissions for sideloaded apps until this is enabled).
4. Enable the accessibility service; grant SMS permission; exclude the app from battery
   optimization.
5. Set a **PIN** screen lock (not a pattern).
6. In the admin screen: add allowed senders, generate the code sheet (shown once — transcribe
   it), install plugins, trust developer keys, review the financial apps list.

## Settings

| Area | Setting |
|------|---------|
| Updates | OS and app auto-updates off; update target apps deliberately, then re-validate plugins |
| Battery | Off: *Put unused apps to sleep*, *Auto-disable unused apps*, *Adaptive battery*. Add the app to *Never sleeping apps*. Device care → *Auto restart* **off**. Enable *Protect battery* (~85 %) if present |
| Screen | AMOLED: keep it **off between jobs**; Always On Display off |
| Network | Wi-Fi always on; SIM with SMS plan; dedicated number with a **carrier SIM PIN** |
| Power | Always on the charger; ideally a small UPS (a reboot needs a human unlock) |
| Physical | A place only the owner can reach; treat the phone like a wallet |

## After a reboot

The phone stays encrypted until a human unlocks it once. Until then it can only reply "needs
unlock" (once 008 is implemented).

## Development access

Wireless debugging over Tailscale for tests; **turn it off** afterwards. Redact any screen dump
before it leaves the phone; never commit raw dumps of personal apps.
