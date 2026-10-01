# SPEC-PROJ-000: Phase 1 — POC Implementation (Android Automation Runtime)

## Summary

Build a minimal Android Proof of Concept that loads a YAML workflow from a textbox, parses and executes it against Android using Accessibility Services, and reports success/failure. The POC implements the core runtime: YAML editor, YAML parser, execution engine, selector engine, state manager, event bus, and Accessibility Service — demonstrated by automating a Calculator app workflow.

---

## High-Level Objective

### User Story

**As a** developer testing Android automation capabilities  
**I want to** define and execute declarative workflows against any Android app  
**So that** I can validate that Accessibility Services can reliably automate user interfaces without root or screen-coordinates hacks

### Business Context

Phase 0 feasibility study (GO decision) confirmed that Accessibility Services can automate standard Android apps. Phase 1 builds the minimal runtime to answer the core question: *can we automate a complete workflow in a real app?* If successful, all future layers (plugins, AI, SMS, web UI) can be built on top of this runtime.

---

## Acceptance Criteria

### Functional — Core Runtime

- [ ] App compiles and installs on Android 8.0+ (API 26+) device/emulator
- [ ] App requests and runs Accessibility Service with foreground service
- [ ] User can paste YAML workflow into editor screen and trigger execution
- [ ] YAML workflow is parsed into typed step objects (no runtime crashes from malformed YAML)
- [ ] `launch_app` action starts the target app and waits for it to come to foreground
- [ ] `wait` action suspends execution for a specified duration
- [ ] `wait_for` action polls for an element matching a selector within a timeout
- [ ] `click` action finds an element by selector and taps it (with configurable retries)
- [ ] `type` action injects text into the currently focused editable field
- [ ] `back` action triggers system back navigation
- [ ] `home` action triggers system home navigation
- [ ] Workflow execution reports per-step success/failure with timestamps
- [ ] On failure, execution stops and shows the failure reason and last screenshot

### Functional — Calculator Demo

- [ ] Provided `calculator.yaml` workflow executes end-to-end without manual intervention
- [ ] Workflow launches Calculator, enters an expression, and clicks the equals button
- [ ] Output is visible in the app's log/results screen

### Non-Functional

- [ ] No network calls during workflow execution (local-first verified)
- [ ] YAML parsing completes in under 100ms for workflows up to 100 steps
- [ ] Selector lookup completes in under 2 seconds per lookup on a typical screen
- [ ] Foreground service notification is visible and dismissible

---

## Technical Architecture

### Revised Architecture

```
┌─────────────────────────────────────────────────────┐
│  Android App (Kotlin, AGP, Compose/XML)              │
│                                                      │
│  ┌───────────┐   ┌──────────┐   ┌───────────────┐   │
│  │  YAML     │──▶│  YAML    │──▶│  Execution    │   │
│  │  Editor   │   │  Parser  │   │  Engine       │   │
│  └───────────┘   └──────────┘   └───────┬───────┘   │
│                                          │           │
│  ┌───────────┐   ┌──────────┐           ▼           │
│  │  State    │◀──│  Event   │   ┌───────────────┐   │
│  │  Manager  │   │  Bus     │──▶│  Selector     │   │
│  └───────────┘   └──────────┘   │  Engine       │   │
│                    ▲             └───────┬───────┘   │
│  ┌───────────┐  ┌──┴──────────┐  ┌──────┴───────┐   │
│  │  Error    │  │  Retry /    │  │  Accessibility│   │
│  │  Handler  │  │  Timeout    │◀─│  Service      │   │
│  └───────────┘  └─────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────┘
                                    │
                                    ▼
                            ┌──────────────┐
                            │  Android OS  │
                            │  (API 26+)   │
                            └──────────────┘
```

### Component Responsibilities

| Component | Responsibility | Phase |
|-----------|---------------|-------|
| **YAML Editor** | Display workflow YAML, allow editing, provide run/stop buttons | P0 |
| **YAML Parser** | Parse YAML string into typed `Workflow` object with `Step` list | P0 |
| **Execution Engine** | Iterate over steps, dispatch to action handlers, manage flow control | P0 |
| **State Manager** | Maintain `AppState` snapshot (current package, activity, node tree) | P0 |
| **Event Bus** | Publish/subscribe for UI events (dialogs, navigation changes, toasts) | P0 |
| **Selector Engine** | Resolve selectors using weighted multi-strategy fallback | P0 |
| **Error Handler** | Apply retry/timeout/on_failure policies per action | P0 |
| **Accessibility Service** | Bridge to Android's `AccessibilityNodeInfo` API | P0 |
| OCR Fallback | OCR-based element detection | P1 (placeholder) |
| ADB Helper | System-level operations via ADB | P1 (placeholder) |

---

## Component Breakdown

### 1. YAML Editor

**Purpose**: Provide a screen for composing and running workflows.

**UI** (Jetpack Compose or XML):
- `TextField` for YAML input (monospace font, line numbers)
- `Button` to run workflow
- `Button` to stop/cancel workflow
- `TextView` for execution log output (scrollable)
- `Switch` for Accessibility Service permission status

**Data flow**:
```
User input (YAML string) → YAML Parser → Workflow object → Execution Engine
Execution Engine → Event Bus → UI updates (log lines, status)
```

**Interface Contract**:
```kotlin
interface WorkflowEditorUi {
    val yamlInput: String
    val isRunning: Boolean
    val logLines: List<String>
    val permissionStatus: PermissionStatus

    fun onRunClicked()
    fun onStopClicked()
    fun onPermissionGranted()
}
```

---

### 2. YAML Parser

**Purpose**: Transform raw YAML text into a typed `Workflow` object.

**Data Model**:
```kotlin
data class Workflow(
    val name: String?,
    val steps: List<Step>,
    val variables: Map<String, String> = emptyMap()
)

data class Step(
    val action: ActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    val selector: Selector? = null,
    val retries: Int = 1,
    val retryDelayMs: Long = 1000,
    val timeoutMs: Long = 30000,
    val onFailure: OnFailurePolicy = OnFailurePolicy.ABORT
)

enum class ActionType {
    LAUNCH_APP, WAIT, WAIT_FOR, CLICK, TYPE, BACK, HOME, SCROLL,
    // P1+
    LONG_CLICK, SCRENSHOT, IF, LOG, SCROLL_TO
}
```

**Parsing approach**: Use Jackson YAML (`jackson-dataformat-yaml`) which maps YAML to Kotlin data classes. Custom deserializer for the polymorphic `action` field.

**Error handling**:
- Malformed YAML → `YamlParseException` with line/column
- Unknown action → `UnknownActionException` listing valid actions
- Invalid parameter types → `ValidationException` with field names

**Interface Contract**:
```kotlin
interface YamlParser {
    fun parse(yaml: String): Workflow
}
```

**Example YAML accepted**:
```yaml
name: Calculator Demo
steps:
  - launch_app:
      package: com.android.calculator2

  - wait:
      seconds: 2

  - wait_for:
      selector:
        text: "2"
      timeout: 5000

  - click:
      selector:
        text: "2"
      retries: 3
      retry_delay: 500

  - click:
      selector:
        text: "+"

  - click:
      selector:
        text: "3"

  - click:
      selector:
        text: "="
      retries: 5
      retry_delay: 1000

  - wait:
      seconds: 1

  - log:
      message: "Calculator workflow completed"
```

---

### 3. Execution Engine

**Purpose**: Drive workflow execution step-by-step, coordinating all other components.

**Core Loop**:
```kotlin
class ExecutionEngine(
    private val actionDispatcher: ActionDispatcher,
    private val eventBus: EventBus,
    private val errorHandler: ErrorHandler,
    private val stateManager: StateManager
) {
    fun execute(workflow: Workflow): ExecutionResult {
        val result = ExecutionResult(workflow.name)
        for ((index, step) in workflow.steps.withIndex()) {
            result.currentStep = index
            result.currentAction = step.action
            try {
                val stepResult = actionDispatcher.dispatch(step)
                result.addStepResult(index, stepResult)
            } catch (e: CancelledException) {
                result.cancelled = true
                return result
            } catch (e: FatalException) {
                result.addFailure(index, e.message ?: "Unknown error")
                return result
            }
        }
        result.completedSuccessfully = true
        return result
    }
}
```

**Flow Control**:
- Sequential execution (default)
- `on_failure` policy per step: `ABORT`, `CONTINUE`, `RETRY`
- Cancellation token for user-initiated stop

**Interface Contract**:
```kotlin
interface ExecutionEngine {
    fun execute(workflow: Workflow): ExecutionResult
    fun cancel()
}

data class ExecutionResult(
    val workflowName: String?,
    val steps: List<StepResult>,
    val completedSuccessfully: Boolean = false,
    val cancelled: Boolean = false
)

data class StepResult(
    val stepIndex: Int,
    val action: ActionType,
    val success: Boolean,
    val durationMs: Long,
    val errorMessage: String? = null,
    val details: Map<String, Any?> = emptyMap()
)
```

---

### 4. Action Dispatcher

**Purpose**: Route each step to the correct action handler.

```kotlin
interface ActionDispatcher {
    fun dispatch(step: Step): StepResult
}
```

**Action Handlers** (one per action type):

| Handler | Selector Required | Retry Supported | Description |
|---------|-------------------|-----------------|-------------|
| `LaunchAppHandler` | No | No | Starts target app via `Intent` |
| `WaitHandler` | No | No | Sleeps for `seconds` |
| `WaitForHandler` | Yes | Yes | Polls selector until found or timeout |
| `ClickHandler` | Yes | Yes | Finds element, taps it |
| `TypeHandler` | No* | No | Injects text into focused field |
| `BackHandler` | No | No | `performGlobalAction(BACK)` |
| `HomeHandler` | No | No | `performGlobalAction(HOME)` |
| `LogHandler` | No | No | Emits log message |

*Type uses the element currently in focus (set by previous action or explicit).

---

### 5. Selector Engine

**Purpose**: Resolve a `Selector` definition into an `AccessibilityNodeInfo` using weighted multi-strategy fallback.

**Selector Data Model**:
```kotlin
sealed class Selector {
    data class ByResourceId(val resourceId: String) : Selector()
    data class ByText(val text: String) : Selector()
    data class ByContentDescription(val description: String) : Selector()
    data class ByClassName(val className: String, val index: Int? = null) : Selector()
    data class Composite(val fallbackOrder: List<Selector>) : Selector()
}
```

**Fallback Strategy** (weighted):
```
1. resource_id  (weight: 10) — most specific, stable within app version
2. text         (weight: 8)  — readable but i18n-sensitive
3. content_description (weight: 7) — accessible label
4. class_name + index (weight: 5) — structural, fragile
5. xpath        (weight: 3)  — positional, most fragile
```

**Interface Contract**:
```kotlin
interface SelectorEngine {
    fun resolve(selector: Selector, rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo?
    fun resolveWithDetails(selector: Selector, rootNode: AccessibilityNodeInfo?): ResolveResult
}

data class ResolveResult(
    val node: AccessibilityNodeInfo?,
    val strategy: String,
    val matchedIndex: Int?,
    val matchTimeMs: Long,
    val fallbacksAttempted: List<String>
)
```

**Strategy implementations**:
```kotlin
interface SelectorStrategy {
    val name: String
    fun find(node: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo>
    fun matches(node: AccessibilityNodeInfo, text: String): Boolean
}
```

Each strategy implements `find()` to search the tree and `matches()` for element matching. The engine ranks results by strategy priority, not by match confidence.

---

### 6. Accessibility Service

**Purpose**: Bridge between our Kotlin runtime and the Android Accessibility API.

**Service Declaration** (`AndroidManifest.xml`):
```xml
<service
    android:name=".accessibility.AutomationService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false"
    android:foregroundServiceType="dataSync">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**Configuration** (`res/xml/accessibility_service_config.xml`):
```xml
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

**Key methods**:
```kotlin
class AutomationService : AccessibilityService() {
    // Lifecycle
    override fun onServiceConnected()  // Service ready
    override fun onUnbind(Intent)     // Service stopped

    // Event handling
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Publish to EventBus for dialog/toast detection
    }

    // Tree access
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    // Actions
    fun click(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    // Global actions
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    // Screenshot (API 29+)
    fun takeScreenshot(): Bitmap? { /* API 29+ */ }
}
```

**Foreground Service** (required API 26+):
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Android Automation")
        .setContentText("Automation service running")
        .setSmallIcon(R.drawable.ic_notification)
        .build()
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
}
```

---

### 7. State Manager

**Purpose**: Maintain the current UI state snapshot for selectors and debugging.

**State Model**:
```kotlin
data class AppState(
    val timestamp: Long = System.currentTimeMillis(),
    val activePackage: String? = null,
    val activeActivity: String? = null,
    val rootNode: AccessibilityNodeInfo? = null,
    val focusedNode: AccessibilityNodeInfo? = null,
    val dialogDetected: Boolean = false,
    val lastEvent: AccessibilityEvent? = null
)
```

**Snapshot approach**: Capture tree on each `onAccessibilityEvent` and `onWindowStateChanged`. The manager holds the latest snapshot.

**Interface Contract**:
```kotlin
interface StateManager {
    fun takeSnapshot(): AppState
    fun waitForCondition(predicate: (AppState) -> Boolean, timeoutMs: Long): Boolean
}
```

---

### 8. Event Bus

**Purpose**: Decouple Accessibility Service events from the Execution Engine.

```kotlin
class EventBus {
    sealed class Event {
        data class WindowChanged(val packageName: String, val activity: String) : Event()
        data class DialogDetected(val dialogText: String) : Event()
        data class ToastDetected(val text: String) : Event()
        data class AccessibilityTreeChanged(val rootNode: AccessibilityNodeInfo?) : Event()
        data class ServiceDisconnected(val reason: String) : Event()
    }

    fun subscribe(handler: (Event) -> Unit): Subscription
    fun publish(event: Event)
}
```

The Accessibility Service publishes events. The Execution Engine subscribes to detect dialogs that need handling (e.g., "Allow notifications?" prompts).

---

### 9. Error Handler / Retry-Timeout

**Purpose**: Apply configurable error policies per action.

```kotlin
sealed class OnFailurePolicy {
    object ABORT : OnFailurePolicy()
    object CONTINUE : OnFailurePolicy()
    data class RETRY(val maxAttempts: Int, val delayMs: Long) : OnFailurePolicy()
}

class ErrorHandler(
    private val selectorEngine: SelectorEngine,
    private val automationService: AutomationService,
    private val errorHandler: ErrorHandler
) {
    suspend fun executeWithRetries(
        action: () -> StepResult,
        retries: Int,
        retryDelayMs: Long,
        timeoutMs: Long
    ): StepResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastException: Exception? = null
        for (attempt in 1..retries) {
            if (System.currentTimeMillis() > deadline) {
                return StepResult(failure = "Timeout after ${timeoutMs}ms")
            }
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                if (attempt < retries) {
                    delay(retryDelayMs)
                }
            }
        }
        return StepResult(failure = "Failed after ${retries} attempts: ${lastException?.message}")
    }
}
```

---

## Data Flow / Execution Flow

### Workflow Execution Sequence

```
User
  │
  ▼
[YAML Editor] ──YAML string──▶ [YAML Parser]
                                     │
                                     ▼
                                [Workflow object]
                                     │
                                     ▼
[UI: "Running..."] ◀── log events ◀── [Execution Engine]
                                     │
                          ┌──────────┼──────────┐
                          ▼          ▼          ▼
                    [Action     [Event    [State
                     Dispatcher] Bus]     Manager]
                          │          │          │
                          ▼          │          │
                    [Selector      │          │
                     Engine]       │          │
                          │         ▼          │
                          │    [Accessibility  │
                          │     Service]       │
                          │         │          │
                          ▼         ▼          ▼
                        [Android OS — UI Elements]
```

### Step Execution Detail (e.g., `click` with retries)

```
Execution Engine
  │
  ├── 1. Read step: click(selector=text:"2", retries=3, retry_delay=500)
  │
  ├── 2. StateManager.takeSnapshot() → AppState { rootNode, activePackage }
  │
  ├── 3. ErrorHandler.executeWithRetries(
  │       action = {
  │         3a. SelectorEngine.resolve(ByText("2"), rootNode)
  │              → attempts: text → content_description → class+index
  │              → returns AccessibilityNodeInfo for "2" button
  │         3b. AutomationService.click(node)
  │              → performAction(ACTION_CLICK)
  │         3c. Verify click succeeded (optional: check state changed)
  │       },
  │       retries = 3,
  │       retryDelayMs = 500
  │     )
  │
  ├── 4. On success → StepResult(success=true, durationMs=45, details={strategy="text"})
  │
  └── 5. Publish StepResult to EventBus → UI renders log line
```

### Event-Driven Dialog Handling

```
Accessibility Service
  │
  ├── onAccessibilityEvent() detects dialog window change
  │     │
  │     └── publish Event.DialogDetected("Terms of Service")
  │           │
  │           └── Execution Engine receives event
  │                 ├── Check current step's on_failure policy
  │                 ├── If policy allows: dismiss dialog automatically
  │                 └── If policy is ABORT: stop workflow, log dialog text
```

---

## API/Interface Contracts

### Core Interfaces Summary

```kotlin
// --- YAML Parser ---
interface YamlParser {
    fun parse(yaml: String): Workflow
}

// --- Execution Engine ---
interface ExecutionEngine {
    fun execute(workflow: Workflow): ExecutionResult
    fun cancel()
}

// --- Action Dispatcher ---
interface ActionDispatcher {
    fun dispatch(step: Step): StepResult
}

// --- Selector Engine ---
interface SelectorEngine {
    fun resolve(selector: Selector, rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo?
}

// --- Accessibility Bridge ---
interface AutomationBridge {
    fun getRootNode(): AccessibilityNodeInfo?
    fun click(node: AccessibilityNodeInfo): Boolean
    fun longClick(node: AccessibilityNodeInfo): Boolean
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean
    fun scrollForward(node: AccessibilityNodeInfo): Boolean
    fun goBack(): Boolean
    fun goHome(): Boolean
    fun takeScreenshot(): Bitmap? // API 29+
}

// --- State Manager ---
interface StateManager {
    fun takeSnapshot(): AppState
}

// --- Event Bus ---
class EventBus {
    fun subscribe(handler: (Event) -> Unit): Subscription
    fun publish(event: Event)
}

// --- ErrorHandler ---
class ErrorHandler {
    suspend fun executeWithRetries(
        action: () -> StepResult,
        retries: Int,
        retryDelayMs: Long,
        timeoutMs: Long
    ): StepResult
}
```

### Action Handler Contract

Each action handler implements:
```kotlin
interface ActionHandler {
    val actionType: ActionType
    suspend fun execute(step: Step, context: ActionContext): StepResult
}

data class ActionContext(
    val automation: AutomationBridge,
    val selectorEngine: SelectorEngine,
    val stateManager: StateManager,
    val eventBus: EventBus
)
```

---

## Testing Strategy

### Unit Tests (JVM, JUnit 5 + Kotlin coroutines)

| Component | Test Cases |
|-----------|-----------|
| **YAML Parser** | Valid YAML → correct Workflow object; malformed YAML → parse exception with line number; unknown action → UnknownActionException; nested selectors; variable substitution; empty steps list; missing required fields |
| **Selector Engine** | `ByText` finds matching node; `ByResourceId` finds matching node; fallback chain uses correct order; empty tree returns null; case-insensitive text matching |
| **ErrorHandler** | Successful action returns immediately; exception triggers retry; max retries exhausted returns failure; timeout aborts early |
| **Execution Engine** | Sequential step execution; cancellation stops execution; `on_failure: CONTINUE` skips failed step; `on_failure: ABORT` stops on failure |
| **State Manager** | Snapshot captures current package and activity; snapshot updates on event |

### Integration Tests (Android Instrumentation)

| Test | Description |
|------|-------------|
| **Full workflow on Calculator** | Parse → Execute → Verify Calculator shows result of expression |
| **Selector resolution on real UI** | Launch Calculator, resolve "2" button via text, verify click changes display |
| **Retry on transient failure** | Simulate element not found on first try, verify retry succeeds |
| **Timeout behavior** | Wait for non-existent element, verify timeout triggers after configured duration |
| **Back/Home navigation** | Launch app, navigate back/home, verify app state |
| **Service lifecycle** | Foreground notification appears; service survives configuration change |

### Device Testing (Real Devices / Emulators)

| Device Profile | Purpose |
|----------------|---------|
| **Pixel Emulator (API 26)** | Minimum supported API |
| **Pixel Emulator (API 31)** | Mid-range device |
| **Pixel Emulator (API 34)** | Latest API |
| **Samsung device (if available)** | OEM battery optimization test |

**Test matrix**:
- Calculator workflow on each device
- Verify selector fallback works across different Android skins
- Verify foreground service notification behavior
- Verify back/home navigation on each version

---

## Pattern References

### Primary Patterns

| Pattern | Location | Usage |
|---------|----------|-------|
| **Feasibility Study** | `patterns_library/architectural/feasibility-study.md` | Phase 0 validation approach; informs POC scope |
| **Accessibility Permissions** | `docs/android/security/accessibility-permissions.md` | Security model, permission handling, data access |
| **Technical Analysis** | `patterns_library/research/technical-analysis.md` | Selector design decisions, tool comparison findings |
| **Tool Comparison** | `patterns_library/evaluation/tool-comparison.md` | UI Automator and Auto.js as reference implementations |

### Architecture Patterns

| Pattern | Rationale |
|---------|-----------|
| **Event-Driven Architecture** | Decouples async Accessibility events from synchronous execution |
| **Strategy Pattern** | Selector strategies (text, resource_id, etc.) are interchangeable |
| **Pipeline Pattern** | YAML → Parse → Execute → Report is a linear pipeline |
| **Repository Pattern** | State Manager as single source of truth for UI state |
| **Circuit Breaker** | ErrorHandler with retry/timeout prevents infinite loops on flaky elements |

---

## Risk Mitigation Plan

| Risk | Source | Mitigation in Phase 1 |
|------|--------|----------------------|
| **R1: FLAG_SECURE blocks some apps** | Risk Assessment R1 | Use Calculator (no FLAG_SECURE) for POC. Document limitation. Placeholder for OCR fallback (Phase 2). |
| **R2: Resource IDs change on updates** | Risk Assessment R2 | Multi-strategy selector fallback implemented. Calculator resource IDs are stable for POC. |
| **R3: OEM battery optimization kills service** | Risk Assessment R3 | Foreground service (required API 26+) reduces risk. Document setup steps for users. |
| **R4: Encrypted keyboards** | Risk Assessment R4 | `type` action works on standard editors. Calculator uses number pad (not keyboard), so not applicable for POC. |
| **R5: Custom views not in tree** | Risk Assessment R5 | Calculator uses standard `Button`/`TextView` widgets — fully accessible. Document for Phase 2. |
| **R7: Play Store rejection** | Risk Assessment R7 | Sideloading via APK for Phase 1. No Play Store submission. |
| **R8: User trust / permission fatigue** | Risk Assessment R8 | Transparent notification. Minimal permission set. Open source for audit. |

---

## Demo Script: Calculator Workflow

### Prerequisites

1. Android device or emulator (API 26+, e.g., Pixel 4 API 31)
2. Calculator app installed (pre-installed on most Android devices: `com.android.calculator2` or `com.google.android.calculator`)
3. POC APK installed and Accessibility Service enabled

### Step-by-Step Demo

**Step 1: Enable Accessibility Service**
```
Settings → Accessibility → [App Name] → Enable
```
The app will show an onboarding screen with a link to enable the service.

**Step 2: Open the POC app**
```
Launch the Android Automation POC app
```

**Step 3: Paste the workflow**

Paste the following YAML into the editor:

```yaml
name: Calculator - Add 2 + 3
steps:
  - launch_app:
      package: com.android.calculator2

  - wait:
      seconds: 2

  - wait_for:
      selector:
        text: "2"
      timeout: 5000

  - click:
      selector:
        text: "2"
      retries: 3

  - click:
      selector:
        text: "+"

  - click:
      selector:
        text: "3"

  - click:
      selector:
        text: "="
      retries: 5
      retry_delay: 1000

  - wait:
      seconds: 2
```

**Step 4: Run the workflow**
```
Tap "Run" button
```

**Expected behavior:**
1. Calculator app launches
2. Engine waits 2 seconds for the app to stabilize
3. `wait_for` polls for element with text "2" (found within 5 seconds)
4. Click "2" → display shows "2"
5. Click "+" → display shows "2 +"
6. Click "3" → display shows "2 + 3"
7. Click "=" → display shows "5"
8. Wait 2 seconds for final state
9. Execution completes successfully

**Step 5: Verify results**
- Log screen in POC app shows each step with success/failure status
- Calculator app displays "5" on screen
- All steps show `success: true` in execution results

### Failure Scenarios to Demonstrate

| Scenario | Expected Behavior |
|----------|-------------------|
| Wrong package name | `launch_app` fails, logs "Package not found", workflow aborts |
| Element not found | `click` retries 3 times, then fails with "Element not found" |
| Timeout on `wait_for` | After timeoutMs expires, logs "Timeout waiting for element" |
| User cancels mid-execution | Workflow stops, shows "Cancelled at step N" |

---

## Success Validation Approach

### Automated Checks

```bash
# Build
./gradlew assembleDebug

# Install on device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Enable Accessibility Service (adb)
adb shell settings put secure enabled_accessibility_services \
  com.proj.automation/.accessibility.AutomationService
adb shell settings put secure accessibility_enabled 1

# Run ADB unit tests (if instrumented)
adb shell am instrument -w -r \
  -e class '.TestSuite' \
  com.proj.automation.test/androidx.test.runner.AndroidJUnitRunner
```

### Manual Device Checks

| # | Check | Pass Condition |
|---|-------|---------------|
| 1 | Install APK | App launches without crash |
| 2 | Enable Accessibility Service | Service starts, foreground notification visible |
| 3 | Paste Calculator workflow | YAML parses without errors |
| 4 | Run workflow | Calculator launches, "5" appears on screen |
| 5 | Check log output | All steps show `success: true` |
| 6 | Cancel mid-execution | Workflow stops, app remains usable |
| 7 | Run on API 26 emulator | Same workflow succeeds |
| 8 | Run on API 34 emulator | Same workflow succeeds |

### Success Threshold

Phase 1 POC is **successful** if:
- Calculator workflow completes end-to-end on at least one API level (API 26 minimum)
- All 10 acceptance criteria are met
- No crashes during execution
- Selector fallback works (demonstrate by using `text` selector which is i18n-safe for Calculator)

---

## Logical Commits

### Commit 1: Project Scaffold
```
feat: scaffold Android POC project with Gradle and AGP [PROJ-000]
```
- `build.gradle.kts` (project + app level)
- `AndroidManifest.xml` with service declaration
- Base application class
- Kotlin + AGP configuration
- ProGuard rules (minimal for POC)

### Commit 2: Accessibility Service
```
feat: implement AutomationService with foreground service [PROJ-000]
```
- `AutomationService` extending `AccessibilityService`
- Foreground notification channel (API 26+)
- `onAccessibilityEvent()` event publishing
- `onServiceConnected()` lifecycle
- Resource ID and notification icon

### Commit 3: YAML Parser
```
feat: implement YAML parser with typed workflow model [PROJ-000]
```
- Data classes: `Workflow`, `Step`, `Selector`, `ActionType`
- Jackson YAML dependency and custom deserializer
- Error handling for malformed/unknown input
- Unit tests for parsing edge cases

### Commit 4: Selector Engine
```
feat: implement selector engine with multi-strategy fallback [PROJ-000]
```
- `Selector` sealed class hierarchy
- Strategy implementations (text, resource_id, content_description, class_name)
- `SelectorEngine` orchestrator with fallback chain
- Unit tests for each strategy

### Commit 5: State Manager + Event Bus
```
feat: implement state manager and event bus [PROJ-000]
```
- `AppState` data model
- `StateManager` with snapshot capture
- `EventBus` with typed event sealing
- Integration with `AutomationService` events

### Commit 6: Action Handlers
```
feat: implement action handlers (launch, wait, click, type, back, home) [PROJ-000]
```
- `ActionDispatcher` routing
- Individual handlers: `LaunchAppHandler`, `WaitHandler`, `ClickHandler`, `TypeHandler`, `BackHandler`, `HomeHandler`, `WaitForHandler`, `LogHandler`
- `ActionContext` data class
- Unit tests for handlers without Accessibility Service

### Commit 7: Execution Engine
```
feat: implement execution engine with retry/timeout/error handling [PROJ-000]
```
- `ExecutionEngine` core loop
- `ErrorHandler` with retry/timeout
- `ExecutionResult` and `StepResult` models
- Cancellation support
- Integration tests for execution flow

### Commit 8: UI — YAML Editor Screen
```
feat: implement YAML editor UI with run/stop/log [PROJ-000]
```
- Main screen with YAML input field
- Run/Stop buttons
- Execution log display (scrollable)
- Permission status indicator
- Navigation to Accessibility Service settings

### Commit 9: Calculator Demo Workflow
```
feat: add calculator demo workflow and device tests [PROJ-000]
```
- `calculator.yaml` example workflow
- Device integration tests for Calculator
- Demo documentation
- README with setup instructions

### Commit 10: Polish and Documentation
```
chore: polish POC, add setup docs, and fix edge cases [PROJ-000]
```
- Error messages and logging improvements
- Edge case handling (null trees, permission denial)
- Setup README for sideload installation
- Known limitations documented

---

## Critical Handoff Notes

### #PATH_DECISION

1. **Kotlin over Java**: Kotlin is the modern Android standard with better null safety, coroutines for async, and data classes for domain models.

2. **Jetpack Compose vs XML**: Compose for new code (editor screen), but XML layouts acceptable for legacy components. POC targets simplicity — Compose preferred for editor, XML for service configuration.

3. **Jackson YAML over SnakeYAML**: Jackson provides better Kotlin integration, typed deserialization, and consistent error messages. SnakeYAML is a fallback if Jackson adds too much dependency weight.

4. **Multi-strategy selectors over single-selector**: Phase 0 research showed no single selector type is reliably stable. Fallback chain is essential for production viability.

5. **Sideload over Play Store**: Phase 0 risk R7 (Play Store rejection) makes sideload the only viable distribution for POC.

6. **Snapshot-based state over event-based state**: Snapshots provide a consistent view for selectors. Events drive reactive behavior (dialogs, toasts).

### #PLAN_UNCERTAINTY

1. **Calculator package name**: Varies by device (com.android.calculator2, com.google.android.calculator, com.samsung.android.calculator). The POC should support configuring the package name or auto-detecting installed calculators.

2. **Selector stability on Calculator**: Calculator buttons may use `content_description` for numbers (accessibility) rather than `text`. The fallback chain handles this, but exact element attributes need empirical verification on test devices.

3. **Tree traversal depth**: Calculator's accessibility tree depth is unknown. If excessively deep (>500 nodes), performance may degrade. Configurable depth limit should be added.

4. **Coroutine scope management**: The execution engine uses coroutines for async action handling. Scope lifecycle must be tied to the Application context to prevent leaks.

5. **Accessibility event timing**: The timing between `launch_app` and the first `wait_for` may vary. The `wait` step after `launch_app` provides a buffer, but actual app readiness is non-deterministic.

### #EXPORT_CRITICAL

1. **API 26 minimum**: Foreground service is mandatory since API 26 (Android 8.0). All target devices must run API 26+.

2. **Accessibility Service permission**: Users must manually enable the service. There is no programmatic way to enable it. The app must guide users through Settings.

3. **No root required**: The entire runtime works without root. This is a hard requirement — if any component requires root, the POC scope must change.

4. **No network calls**: Workflow execution must be fully local. No telemetry, no cloud calls, no external APIs during execution.

5. **Data access scope**: The Accessibility Service can read any visible text on screen. The app must not log or transmit this data. Any debug logging must be local-only and disabled in production.

---

## Testing Strategy (Detailed)

### Unit Tests

**YAML Parser**:
- [ ] Valid YAML with all action types parses correctly
- [ ] YAML with unknown action throws `UnknownActionException`
- [ ] Malformed YAML (missing colon, bad indentation) throws `YamlParseException` with location
- [ ] Empty steps list is valid
- [ ] Steps with all optional parameters default correctly
- [ ] Selector with all strategies parses correctly
- [ ] Variable interpolation works (`${var}` → value)

**Selector Engine**:
- [ ] `ByText` finds element with matching text (case-insensitive)
- [ ] `ByResourceId` finds element with matching resource ID
- [ ] `ByContentDescription` finds element with matching description
- [ ] Fallback chain tries strategies in correct order
- [ ] Returns null when no match found
- [ ] Returns first match when multiple matches exist
- [ ] `Composite` selector tries sub-selectors in order

**ErrorHandler**:
- [ ] Success on first attempt returns immediately
- [ ] First attempt fails, second succeeds → returns success with retry info
- [ ] All attempts fail → returns failure with last exception message
- [ ] Timeout expires before max retries → returns timeout failure
- [ ] Cancellation during retry stops execution

**Execution Engine**:
- [ ] Sequential steps execute in order
- [ ] Step failure with `ABORT` stops remaining execution
- [ ] Step failure with `CONTINUE` proceeds to next step
- [ ] Cancellation stops execution and returns cancelled result
- [ ] Empty workflow returns immediately with no steps

### Integration Tests (Instrumentation)

- [ ] Full Calculator workflow: parse → execute → verify display shows "5"
- [ ] Selector resolution: launch Calculator, find "2" button by text, click it
- [ ] Retry: simulate flaky element (remove/re-add), verify retry succeeds
- [ ] Timeout: `wait_for` with non-existent selector, verify timeout
- [ ] Back navigation: launch app, press back, verify app closes
- [ ] Home navigation: launch app, press home, verify returned to launcher
- [ ] Service lifecycle: kill app process, verify service survives (foreground)

### Device Tests

Run on minimum 3 API levels (26, 31, 34):
- [ ] Calculator workflow succeeds on each API level
- [ ] Selector fallback works correctly on each API level
- [ ] Foreground notification is visible on each API level
- [ ] Back/home navigation works on each API level

---

## Security Considerations

### Accessibility Service Data Access

- The service reads the full accessibility tree of the active window
- **No data is stored, transmitted, or logged** during normal execution
- Debug logging (local only) may capture element text — must be disabled in production
- Screenshot capability (API 29+) is not used in Phase 1

### Permissions Required

| Permission | Purpose | Risk Level |
|-----------|---------|-----------|
| `BIND_ACCESSIBILITY_SERVICE` | Required for service declaration | Low (system-level) |
| Foreground service notification | Required API 26+ | Low (transparency) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevents OEM battery kill | Medium (user must approve) |

### Data Protection

- YAML workflows stored in-memory only (no persistent storage in Phase 1)
- No network calls during execution
- No analytics or telemetry
- No account or authentication required
- Open source for auditability

### Input Validation

- YAML input validated before execution (parser throws on malformed input)
- Action parameters validated at dispatch time
- Selector strings validated for null/empty
- Timeout values validated for positive numbers
- Retry counts validated for non-negative integers

---

## Definition of Done

- [ ] All acceptance criteria met (functional + non-functional)
- [ ] Unit tests pass (JVM, JUnit 5)
- [ ] Integration tests pass on emulator (API 26, 31, 34)
- [ ] Calculator demo workflow executes successfully on real device
- [ ] APK builds without warnings (`./gradlew assembleDebug`)
- [ ] No crashes in 10 consecutive runs of Calculator workflow
- [ ] Accessibility Service foreground notification is visible
- [ ] Documentation includes setup guide and demo script
- [ ] Known limitations documented in README
- [ ] Code reviewed for security (no data leaks, no network calls)
- [ ] Logical commits follow convention (see commit list above)

---

## Notes for Execution Agent

### Before Starting

1. Set up Android Studio project with AGP 8.x, Kotlin 1.9+, target SDK 34, min SDK 26
2. Read all Phase 0 reports for context on Accessibility Services capabilities
3. Study AndroidX UI Automator `UiSelector` API as reference for selector design
4. Verify Calculator package name on test device (`adb shell pm list packages | grep calculator`)

### During Implementation

1. Build components in commit order (scaffold → service → parser → selectors → state/event → actions → engine → UI → demo)
2. Run unit tests after each component commit
3. Test on emulator early (API 26 minimum) to catch API differences
4. Use `adb logcat` for debugging Accessibility Service interactions
5. When selector strategies don't match expected elements, inspect the accessibility tree using Android Studio's Layout Inspector

### Key Debugging Commands

```bash
# Inspect accessibility tree of active window
adb shell service call accessibility 3 i32 $(adb shell pidof com.android.calculator2)

# Dump current accessibility tree
adb shell settings put secure enabled_accessibility_services <your-service>
adb shell settings put secure accessibility_enabled 1
# Then use Android Studio → Device File Explorer → /data/local/tmp/accessibility

# Check if FLAG_SECURE is set on an app
adb shell dumpsys window | grep -A 5 "mCurrentFocus"

# Check foreground service status
adb shell dumpsys activity services | grep -A 20 <your-package>
```

### #EXPORT_CRITICAL (Execution Agent)

- Do NOT add any network calls, telemetry, or cloud dependencies
- Do NOT require root
- Do NOT store YAML workflows persistently (in-memory only for Phase 1)
- Do NOT add AI/LLM integration (out of scope)
- Do NOT attempt to publish to Play Store
- Always validate selectors against actual device accessibility tree before assuming element attributes

---

## Appendix A: Calculator Accessibility Tree (Expected)

The exact tree depends on device and Android version. Expected structure:

```
Window: com.android.calculator2/.Calculator
  ├── LinearLayout (root)
  │   ├── LinearLayout (display area)
  │   │   └── TextView (text: "0", content_description: "0")
  │   ├── GridLayout (buttons)
  │   │   ├── Button (text: "7", content_description: "7")
  │   │   ├── Button (text: "8", content_description: "8")
  │   │   ├── Button (text: "9", content_description: "9")
  │   │   ├── Button (text: "/", content_description: "divide")
  │   │   ├── Button (text: "4", content_description: "4")
  │   │   ├── Button (text: "5", content_description: "5")
  │   │   ├── Button (text: "6", content_description: "6")
  │   │   ├── Button (text: "*", content_description: "multiply")
  │   │   ├── Button (text: "1", content_description: "1")
  │   │   ├── Button (text: "2", content_description: "2")
  │   │   ├── Button (text: "3", content_description: "3")
  │   │   ├── Button (text: "-", content_description: "subtract")
  │   │   ├── Button (text: "0", content_description: "0")
  │   │   ├── Button (text: ".", content_description: "decimal point")
  │   │   ├── Button (text: "=", content_description: "equals")
  │   │   └── Button (text: "+", content_description: "add")
  │   └── ImageButton (clear, content_description: "clear")
  └── ImageButton (history, content_description: "history")
```

**Note**: Attributes above are illustrative. The execution agent MUST verify the actual tree on the test device using Layout Inspector before hard-coding any selector attributes.

---

## Appendix B: Dependency Versions

| Dependency | Version | Purpose |
|-----------|---------|---------|
| AGP | 8.2.x | Android Gradle Plugin |
| Kotlin | 1.9.x | Language |
| Coroutines | 1.7.x | Async execution |
| Jackson YAML | 2.15.x | YAML parsing |
| Material Components | 1.11.x | UI components |
| JUnit 5 | 5.10.x | Unit tests |
| AndroidX Test | 1.5.x | Instrumentation tests |
| AndroidX Core KTX | 1.12.x | Kotlin extensions |
| AndroidX Lifecycle | 2.7.x | Lifecycle management |

---

*Spec generated for PROJ-000 Phase 1 POC Implementation*  
*Next step: System Architect Stage 1 review → Implementation*
