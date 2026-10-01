# Android Emulator Setup & Runbook

> Operational guide for installing and running the Android emulator used to test
> the POC app (`com.proj.automation`). All installs live under `~/` — **no sudo required**.

## TL;DR (reusable helper)

A helper script wraps the common operations:

```bash
source ~/dev-env.sh                 # set JAVA_HOME, ANDROID_HOME, adb on PATH
~/boot-poc-emulator.sh boot                 # boot the AVD (waits until ready)
~/boot-poc-emulator.sh enable-service       # grant + enable the Accessibility Service
~/boot-poc-emulator.sh status                # adb devices
~/boot-poc-emulator.sh stop                   # shut down the emulator
```

Everything below documents what the helper does, so you can adjust flags as needed.

---

## 1. Prerequisites

| Tool | Purpose |
|---|---|
| `curl`, `wget`, `unzip`, `tar` | Download / unpack packages |
| JDK 17 | Gradle requires JDK 17 (system default is 21, which AGP 8.2.2 rejects) |
| Android SDK | `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`, `emulator`, a system image |
| KVM (`/dev/kvm`) | Hardware acceleration — fast boot. Optional but strongly recommended |

This project was verified on: **x86_64 Linux**, **Temurin JDK 17.0.20.1**, **emulator 37.1.11.0**, **Android 34 google_apis x86_64**.

### Point Gradle at JDK 17

The `~/dev-env.sh` file pins Gradle to JDK 17 (required — the machine default is JDK 21):

```bash
source ~/dev-env.sh
```

---

## 2. Install the Android SDK

Set the SDK root and locate `sdkmanager`:

```bash
export ANDROID_SDK_ROOT="$HOME/android-sdk"
SM="$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager"
```

### Accept licenses

```bash
yes | $SM --licenses
```

### Install the packages

```bash
$SM "platform-tools" "platforms;android-34" "build-tools;34.0.0"
$SM "emulator"
$SM "system-images;android-34;google_apis;x86_64"
```

> The `google_apis` variant is used so target apps (e.g. Calculator) resolve.
> This downloads ~700 MB.

### Record the SDK path

Create `local.properties` at the project root so Gradle finds the SDK:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

`local.properties` is git-ignored — never commit it.

---

## 3. Create the AVD

The intended AVD is **`poc_34`**: **Pixel 3a**, Android 34, `google_apis`, **x86_64**.

```bash
AVD="$HOME/android-sdk/avd/poc_34.avd"
yes | "$HOME/android-sdk/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "poc_34" \
  --path "$HOME/android-sdk/avd" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_3a" \
  --abi "x86_64" \
  --tag "google_apis" \
  --force
```

### ⚠️ Known issue: `avdmanager` writes unresolved placeholders

The bundled `avdmanager` sometimes writes a template `config.ini` containing
placeholders (`avd.id=<build>`, `disk.dataPartition.path=<temp>`) instead of
resolving them, which leaves a non-bootable AVD. If you see this, fix it manually:

```bash
mkdir -p "$AVD"
dd if=/dev/zero of="$AVD/userdata.img" bs=1M count=1024 status=none   # fresh userdata disk
sed -e 's#avd.id=<build>#avd.id=poc_34#' \
    -e 's#avd.name=<build>#avd.name=poc_34#' \
    -e "s#disk.dataPartition.path=<temp>#disk.dataPartition.path=$AVD/userdata.img#" \
    "$HOME/android-sdk/avd/config.ini" > "$AVD/config.ini"
```

The system image is referenced by `image.sysdir.1`, so no further wiring is needed.
Verify it resolves:

```bash
ls "$HOME/android-sdk/system-images/android-34/google_apis/x86_64/system.img"
```

---

## 4. Boot the emulator

```bash
source ~/dev-env.sh
"$HOME/android-sdk/emulator/emulator" -avd poc_34 \
  -no-snapshot -no-window -gpu swiftshader_egl -no-audio
```

Flags:

| Flag | Meaning |
|---|---|
| `-avd poc_34` | Use the AVD created above |
| `-no-snapshot` | Cold boot (fresh state) |
| `-no-window` | Headless — no GUI window (server / CI friendly) |
| `-gpu swiftshader_egl` | Software GL, avoids host-GPU issues |
| `-no-audio` | Skip audio (not needed for testing) |

KVM accelerates boot (~18–58 s to ready). Wait for `adb` to see the device, then
for `sys.boot_completed`:

```bash
adb wait-for-device
adb shell getprop sys.boot_completed    # prints 1 when booted
```

### Keep the emulator alive

This shell kills backgrounded processes when a call ends, so **start the emulator
in your own terminal** (or use the helper script) to keep it running:

```bash
~/boot-poc-emulator.sh boot     # boots + waits until ready
```

---

## 5. Enable the Accessibility Service (required for automation)

The app's whole purpose is the Accessibility Service. It must be **granted the
permission** and **enabled**:

```bash
adb shell pm grant com.proj.automation android.permission.BIND_ACCESSIBILITY_SERVICE
adb shell settings put secure enabled_accessibility_services \
  "com.proj.automation/.accessibility.AutomationService"
adb shell settings put secure accessibility_enabled 1
```

Confirm it is bound:

```bash
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
adb shell dumpsys accessibility          # look for: Enabled services: {{com.proj.automation/...}}
```

When bound, `dumpsys accessibility` shows:

```
Enabled services:{{com.proj.automation/com.proj.automation.accessibility.AutomationService}}
```

---

## 6. Install and launch the app

Build the APK first, then install:

```bash
source ~/dev-env.sh
./gradlew :app:assembleDebug                     # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm list packages | grep com.proj       # confirms installation
adb shell am start -n com.proj.automation/.MainActivity
```

---

## 7. Useful commands

```bash
adb devices                                   # list connected devices/emulators
adb shell pm clear com.proj.automation        # reset app data
adb logcat -s AutomationService               # follow the service's logs
adb shell input text "hello"                  # type text into the focused field
adb shell input tap 540 960                   # tap a coordinate
adb emu avd name list                         # list AVDs
adb emu kill                                  # stop the emulator cleanly
```

Stop the emulator:

```bash
~/boot-poc-emulator.sh stop      # adb emu kill
```

---

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| `adb: command not found` | `source ~/dev-env.sh` (adds `platform-tools` to PATH) |
| `SDK location not found` | Ensure `local.properties` has `sdk.dir=$HOME/android-sdk` |
| Gradle: "Java 21 ... not supported" | `source ~/dev-env.sh` pins JDK 17 via `~/dev-env.sh` |
| AVD won't boot / `avd.id=<build>` | See §3 — rebuild the AVD with the manual placeholder fix |
| No `/dev/kvm` | Emulator still works in software mode (slower boot) |
| `avdmanager` leaves `<temp>`/`<build>` in `config.ini` | Same §3 manual fix |
| Service not listed in `dumpsys accessibility` | Re-run §5 and `sleep 3` before checking; confirm `accessibility_enabled = 1` |

---

## 9. Full end-to-end check

A one-shot script that boots, enables the service, installs the app, and launches
the activity is available at `~/boot-poc-emulator.sh` (via its `boot` and
`enable-service` subcommands) — or reuse the steps in §4 → §6. A verified
successful run produced:

```
BOOTED after 58s
enabled_accessibility_services: com.proj.automation/.accessibility.AutomationService
accessibility_enabled: 1
dumpsys accessibility: Enabled services:{{com.proj.automation/...accessibility.AutomationService}}
adb install: Success
MainActivity launched
```

> Note: the stock Calculator app (`com.android.calculator2`) is not on this image,
> so the `calculator.yaml` demo cannot run end-to-end as written. Everything else
> (runtime + Accessibility Service binding) is validated.
