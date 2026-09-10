# Android Automation App POC — Phase 1
#
# Minimal Android automation runtime using Accessibility Services.
# Parses YAML workflows and executes them against Android UI elements.

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK with API 34 platform

### Build & Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Enable Accessibility Service
```
Settings → Accessibility → Android Automation → Enable
```

### Run a Workflow
1. Paste YAML into the editor
2. Tap "Run"
3. Watch the execution log

## Known Limitations
- Requires API 26+ (Android 8.0+)
- Cannot automate FLAG_SECURE apps (banking, DRM)
- Accessibility Service must be manually enabled
- No Play Store distribution (sideload only)
- No network calls during execution
