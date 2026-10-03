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
| **Selector Engine** | `selector/SelectorEngine.kt` | Resolves `text` / `resource_id` / `content_description` / `class_name` selectors, with explicit `fallback:` chains |
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
| Read text | `read_text` | Reads an element's text (or content description) into a variable with `into` |
| Read list | `read_list` | Collects item labels in reading order, scrolling for more (`match`, `role`, `max`, `max_scrolls`, `into`) |
| Scroll until | `scroll_until` | Scrolls until the selector or target is on screen; stops when the list stops moving |

## DSL v2 (control flow and variables)

Every v1 workflow above still runs unchanged. v2 adds:

| Construct | Example |
|-----------|---------|
| Parameters and variables | `params: { to: {}, text: { default: "hi" } }`, `set: { url: "https://wa.me/${to}" }` |
| Templates with filters | `"${text|urlencode}"`, filters: `urlencode`, `upper`, `lower`, `trim`, `mask` |
| Conditions | `if: { exists: { text: "OK" }, then: [...], else: [...] }`; also `not_exists`, `equals`, `contains`, `is_set`, `not`, `all`, `any` |
| Fallbacks | `first_that_works: [ ...alternatives... ]` |
| Error handling | `try: { do: [...], on_error: [...] }` (exposes `${error.code}`, `${error.message}`) |
| Reusable flows | `flows: { press: { params: { key: {} }, steps: [...] } }` and `call: { flow: press, with: { key: "2" }, into: var }` |
| Verification | `expect:` on any action (a post-condition), `assert:` as a step |
| Reading values | `read_text: { selector: {...}, into: balance }`, `read_list: { match: "R\\$", max: 5, into: entries }` (`${entries}` renders one per line; `${entries.size}`, `${entries.0}`) |
| Targets (self-healing) | `click: { target: { intent: "send the message", role: button, hints: { content_description: "Send" }, region: bottom-right } }` — exact hints first, then ranking; refuses to guess (`E_LOW_CONFIDENCE`) |
| Result | `return: "Balance ${balance|mask}"` |

The engine enforces global limits (actions, steps, call depth, duration) that a workflow cannot override, and returns structured error codes (`E_NOT_FOUND`, `E_TIMEOUT`, `E_VERIFY_FAILED`, …). Recursive flows and bad templates are rejected before the run starts. See [`examples/calculator_flows_v2.yaml`](examples/calculator_flows_v2.yaml).

## Plugins and the `agp` tool

Apps are supported through **plugin packages** (`.agp`: a zip of YAML files, no code). See
[`plugins/whatsapp`](plugins/whatsapp) (an unvalidated example) and
[docs/vision/plugins.md](docs/vision/plugins.md).

```bash
./gradlew :agp:installDist                      # builds tools/agp/build/install/agp/bin/agp
AGP=tools/agp/build/install/agp/bin/agp
$AGP validate plugins/whatsapp --libs plugins/libraries
$AGP build    plugins/whatsapp --libs plugins/libraries -o build/whatsapp.agp
$AGP inspect  build/whatsapp.agp
$AGP targets  plugins/whatsapp my_screen_dump.xml --libs plugins/libraries
```

`agp targets` is the quickest way to check a plugin against a real screen: dump it with
`adb shell uiautomator dump /sdcard/s.xml && adb pull /sdcard/s.xml` (the file stays on your computer).

## Selector Types

| Selector | YAML | Description |
|----------|------|-------------|
| By text | `text: "Button"` | Matches element text (case-insensitive) |
| By resource ID | `resource_id: "com.app:id/btn"` | Matches Android resource ID |
| By content description | `content_description: "submit"` | Matches accessibility label |
| By class name | `class_name: "android.widget.Button"` | Matches Java class name |
| Composite fallback | `fallback: [...]` | Tries each sub-selector in order (the only way to fall back across selector types) |

## Retries, Timeouts and Failure Policy

Every step accepts these keys:

| Key | Default | Description |
|-----|---------|-------------|
| `retries` | `1` | Total attempts for the step |
| `retry_delay` | `1000` | Milliseconds between attempts |
| `timeout` | `30000` | Milliseconds allowed for all attempts together (not applied to `wait`) |
| `on_failure` | `abort` | `abort`, `continue`, or `retry(n, ms)` — `retry` overrides `retries`/`retry_delay` |

## Testing

### Unit Tests (JVM, no device needed)
```bash
./gradlew :core:test :app:testDebugUnitTest
```

Tests cover:
- YAML parser (valid/invalid YAML, all action types, selectors, policies)
- Selector engine (strategy matching, null safety, fallback chain)
- Execution engine (sequential execution, ABORT/CONTINUE, cancellation)
- Error handler (retry, timeout, failure after exhaustion)
- DSL v2: templates and filters, parser (control flow, flows, recursion and template checks), interpreter (branches, fallbacks, try, flow scopes, `expect`, limits, cancellation)

## Project Structure

Three Gradle modules:

| Module | Contents |
|--------|----------|
| `:core` (Kotlin/JVM, no Android) | Plugin packages (`plugin/`: unpacker, lock, manifest, loader, capability guard, builder), workflow language (`dsl/`: syntax tree, parser, templates), v1 parser (`parser/`), element targets and the ranking resolver (`resolve/`), screen snapshots and `uiautomator` XML reading (`ui/`), error codes. Shared with future command-line tools |
| `:app` (Android) | Interpreter (`dsl/Interpreter.kt`), execution engine and action handlers (`engine/`), accessibility service (`accessibility/`), live snapshots (`ui/UiSnapshots.kt`), v1 selector engine (`selector/`), event bus and state (`service/`), Compose editor (`editor/`) |

```
core/src/main/kotlin/com/proj/automation/
├── dsl/        Ast.kt, DslParser.kt, Templates.kt
├── parser/     ActionType, Selector, WorkflowAst (Step), YamlParser, OnFailurePolicy, exceptions
├── resolve/    Target.kt, TargetResolver.kt
├── ui/         UiNode.kt, UiXml.kt
└── engine/     ErrorCode.kt

app/src/main/java/com/proj/automation/
├── App.kt, MainActivity.kt
├── accessibility/   AutomationBridge.kt, AutomationService.kt
├── dsl/             Interpreter.kt
├── editor/          WorkflowEditorScreen.kt
├── engine/          ExecutionEngine, ErrorHandler, ActionContext, ActionDispatcher,
│                    HandlerRegistry, CancellationToken, models, actions/
├── selector/        SelectorEngine + strategies (v1 selectors)
├── service/         EventBus.kt, StateManager.kt
└── ui/              UiSnapshots.kt
```

| `:agp` (`tools/agp`, Kotlin/JVM) | Command-line tool for plugin authors |

Tests: `./gradlew :core:test :app:testDebugUnitTest` (JVM, no device needed).

## Known Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|------------|
| Requires API 26+ (Android 8.0+) | Cannot run on older devices | minSdk 26 in build config |
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
| [ADR-006](docs/adr/ADR-006-laya-decision-layer.md) | Laya as local decision layer for element resolution | Accepted |
| [ADR-007](docs/adr/ADR-007-declarative-yaml-plugins.md) | Declarative plugin packages (zip of YAML files), intelligence in the base app | Accepted |
| [ADR-008](docs/adr/ADR-008-channel-abstraction.md) | Channel abstraction, SMS as the first contact channel | Accepted |

## Vision and Target Architecture

Where this project is heading — a resilient, fully offline automation layer reachable through several contact channels, starting with SMS from a dumbphone — is documented in [`docs/vision/`](docs/vision/README.md). Start with the [decision report](docs/vision/decision-report.md): target architecture, YAML plugin system, Laya-based element resolution, action catalog, SMS/security model and roadmap.

## Next Steps (Phase 2)

- [ ] OCR fallback for FLAG_SECURE apps
- [ ] ADB helper for system-level operations
- [ ] Variable interpolation (`${var}` syntax)
- [ ] More complex error recovery policies
- [ ] Plugin architecture for custom actions
- [ ] Web-based workflow editor

---

*Built as a Phase 1 POC for PROJ-000 — Android Automation Runtime*
