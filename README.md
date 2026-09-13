# Android Automation App POC

A minimal Android proof-of-concept that loads a YAML workflow from a textbox, parses and executes it against Android using Accessibility Services, and reports success/failure.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Android App (Kotlin, AGP, Compose/XML)             │
│                                                     │
│  ┌───────────┐   ┌──────────┐   ┌───────────────┐   │
│  │  YAML     │──▶│  YAML    │──▶│  Execution    │   │
│  │  Editor   │   │  Parser  │   │  Engine       │   │
│  └───────────┘   └──────────┘   └───────┬───────┘   │
│                                         │           │
│  ┌───────────┐   ┌──────────┐           ▼           │
│  │  State    │◀──│  Event   │   ┌───────────────┐   │
│  │  Manager  │   │  Bus     │──▶│  Selector     │   │
│  └───────────┘   └──────────┘   │  Engine       │   │
│                    ▲            └───────┬───────┘   │
│  ┌───────────┐  ┌──┴──────────┐  ┌──────┴───────┐   │
│  │  Error    │  │  Retry /    │◀─│ Accessibility│   │
│  │  Handler  │  │  Timeout    │  │ Service      │   │
│  └───────────┘  └─────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────┘
```

## Components

| Component | Location | Description |
|-----------|----------|-------------|
| **YAML Editor** | `editor/WorkflowEditorScreen.kt` | Compose UI with YAML input, run/stop, and log |
| **YAML Parser** | `parser/YamlParser.kt` | SnakeYAML-based parser with typed AST |
| **Execution Engine** | `engine/ExecutionEngine.kt` | Step loop orchestrator with retry/timeout |
| **Action Handlers** | `engine/actions/` | LaunchApp, Wait, Click, Type, Back, Home, WaitFor, Log |
| **Selector Engine** | `selector/SelectorEngine.kt` | Multi-strategy fallback (resource_id → text → content_description → class_name) |
| **Automation Service** | `accessibility/AutomationService.kt` | AccessibilityService bridge to Android API |
| **State Manager** | `service/StateManager.kt` | Current UI state snapshot |
| **Event Bus** | `service/EventBus.kt` | Typed pub/sub for events |

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK with API 34 platform
- Android device or emulator (API 26+, Pixel recommended)

### Build & Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Enable Accessibility Service
1. Open Settings → Accessibility
2. Find "Android Automation" in the list
3. Toggle it ON

### Run a Workflow
1. Launch the app
2. Paste YAML into the editor (a calculator demo is pre-loaded)
3. Tap "▶ Run"
4. Watch the execution log

## Demo: Calculator 2 + 3 = 5

The app ships with a pre-loaded calculator workflow that automates:
```
Launch Calculator → Click "2" → Click "+" → Click "3" → Click "="
```

Expected result: Calculator displays "5"

## Supported Actions

| Action | YAML Key | Description |
|--------|----------|-------------|
| Launch app | `launch_app` | Starts target app by package name |
| Wait | `wait` | Pauses for specified seconds |
| Wait for | `wait_for` | Polls for element until found or timeout |
| Click | `click` | Taps element by selector (with retry) |
| Type | `type` | Injects text into focused editable field |
| Back | `back` | System back navigation |
| Home | `home` | System home navigation |
| Scroll | `scroll` | Scrolls a scrollable node |
| Log | `log` | Emits log message to execution log |

## Selector Types

| Selector | YAML | Description |
|----------|------|-------------|
| By text | `text: "Button"` | Matches element text (case-insensitive) |
| By resource ID | `resource_id: "com.app:id/btn"` | Matches Android resource ID |
| By content description | `content_description: "submit"` | Matches accessibility label |
| By class name | `class_name: "android.widget.Button"` | Matches Java class name |
| Composite fallback | `fallback: [...]` | Tries each sub-selector in order |

## Testing

### Unit Tests (JVM, no device needed)
```bash
./gradlew :app:testDebugUnitTest
```

Tests cover:
- YAML parser (valid/invalid YAML, all action types, selectors, policies)
- Selector engine (strategy matching, null safety, fallback chain)
- Execution engine (sequential execution, ABORT/CONTINUE, cancellation)
- Error handler (retry, timeout, failure after exhaustion)

## Project Structure

```
app/src/main/java/com/proj/automation/
├── App.kt                           — Application class
├── MainActivity.kt                  — Compose UI host
├── accessibility/
│   ├── AutomationBridge.kt          — Bridge interface
│   └── AutomationService.kt         — AccessibilityService implementation
├── editor/
│   └── WorkflowEditorScreen.kt      — YAML editor Compose screen
├── engine/
│   ├── actions/                     — Individual action handlers
│   ├── ActionContext.kt             — Handler context
│   ├── ActionDispatcher.kt          — Handler router
│   ├── CancellationToken.kt        — Cancellation support
│   ├── ErrorHandler.kt             — Retry/timeout logic
│   ├── ExecutionEngine.kt          — Core step loop
│   └── models.kt                   — Result data classes
├── parser/
│   ├── ActionType.kt               — Action type enum
│   ├── OnFailurePolicy.kt          — Failure policy sealed class
│   ├── Selector.kt                 — Selector sealed hierarchy
│   ├── WorkflowAst.kt              — Workflow/Step data classes
│   ├── YamlParser.kt               — SnakeYAML parser
│   └── exceptions.kt               — Parse/validation exceptions
├── selector/
│   ├── ByClassNameStrategy.kt       — Class name matching
│   ├── ByContentDescriptionStrategy.kt — Content desc matching
│   ├── ByResourceIdStrategy.kt      — Resource ID matching
│   ├── ByTextStrategy.kt            — Text matching
│   ├── ResolveResult.kt             — Resolution result model
│   ├── SelectorEngine.kt            — Multi-strategy orchestrator
│   └── SelectorStrategy.kt          — Strategy interface
└── service/
    ├── EventBus.kt                  — Typed pub/sub events
    └── StateManager.kt              — UI state snapshots

app/src/test/java/com/proj/automation/
├── engine/
│   ├── ErrorHandlerTest.kt
│   └── ExecutionEngineTest.kt
├── parser/
│   └── YamlParserTest.kt
└── selector/
    ├── ByTextStrategyTest.kt
    └── SelectorEngineTest.kt
```

## Known Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|------------|
| Requires API 26+ (Android 8.0+) | Cannot run on older devices | Foreground service mandatory on API 26+ |
| Cannot automate FLAG_SECURE apps | Banking, VPN, DRM apps blocked | POC avoids FLAG_SECURE apps |
| Accessibility Service must be manually enabled | No programmatic enable | Document setup steps for users |
| No Play Store distribution | Sideload only for POC | Acceptable for POC phase |
| No network calls during execution | Fully local runtime | Verified — no external dependencies |
| ACTION_SET_TEXT unreliable on custom EditText | Text input may fail on custom views | POC uses Calculator (standard views) |
| Tree recycling required | Forgetting to recycle causes memory leaks | Wrap tree access in try/finally |

## Architecture Decision Records

| ADR | Topic | Status |
|-----|-------|--------|
| [ADR-001](docs/adr/ADR-001-kotlin-language.md) | Kotlin as development language | Accepted |
| [ADR-002](docs/adr/ADR-002-snakeyaml-parser.md) | SnakeYAML 2.x as YAML parser | Accepted |
| [ADR-003](docs/adr/ADR-003-native-accessibility-service.md) | Native AccessibilityService over UI Automator | Accepted |
| [ADR-004](docs/adr/ADR-004-compose-over-xml.md) | Jetpack Compose over XML layouts | Accepted |
| [ADR-005](docs/adr/ADR-005-poc-scope-trims.md) | POC scope trims — deferred to Phase 2 | Accepted |

## Next Steps (Phase 2)

- [ ] OCR fallback for FLAG_SECURE apps
- [ ] ADB helper for system-level operations
- [ ] Variable interpolation (`${var}` syntax)
- [ ] More complex error recovery policies
- [ ] Plugin architecture for custom actions
- [ ] Web-based workflow editor

---

*Built as a Phase 1 POC for PROJ-000 — Android Automation Runtime*
