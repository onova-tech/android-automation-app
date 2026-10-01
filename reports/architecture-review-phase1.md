# Architecture Review — Phase 1: Proof of Concept

**Date**: 2026-07-06  
**Ticket**: PROJ-000-android-automation-app-poc  
**Reviewer**: System Architect  
**Status**: Approved with Changes  

---

## 1. Executive Summary

The Phase 0 feasibility study conclusively answers the core question: **YES, a reliable, generic, declarative Android automation runtime can be built using Accessibility Services**, with acknowledged caveats around FLAG_SECURE and OEM fragmentation.

The revised architecture from Phase 0 (adding Event Bus, State Manager, Retry/Timeout Engine, Error Handler, OCR Fallback, and ADB Helper) is **sound in principle** but **slightly over-engineered for a POC**. This review recommends trimming the POC scope to the core 5-component subset while keeping the full architecture as the target design for Phase 2.

**Verdict: APPROVED WITH CHANGES** — the architecture is viable, the scope needs trimming for a 15-day POC, and the team needs Android-specific patterns (currently absent from the pattern library).

---

## 2. Architecture Assessment

### 2.1 Assessment: Sound (with modifications)

The revised architecture diagram from the feasibility study (Section 6.1 of the report) is architecturally sound. It correctly identifies the layered separation between:

1. **User-facing layer**: YAML Editor (input/output)
2. **Parsing layer**: YAML Parser (declarative → structured)
3. **Execution layer**: Execution Engine (orchestrates actions)
4. **Intelligence layer**: Selector Engine + State Manager + Event Bus
5. **Reliability layer**: Retry/Timeout Engine + Error Handler
6. **System layer**: Accessibility Service + OCR Fallback + ADB Helper
7. **Platform**: Android OS

### 2.2 Missing Components (for Production, not POC)

The following are **out of scope for Phase 1** but should be noted as future additions:

| Component | Purpose | Priority |
|-----------|---------|----------|
| Plugin loader | Extensibility via plugin YAML files | Phase 3 |
| Workflow versioning | Compatibility tracking across app updates | Phase 3 |
| Workflow sharing | Transport for workflows (SMS, file, web) | Phase 3 |
| Expression engine | Dynamic values (`${variable}` syntax) | Phase 2 |
| Screen definitions | Reusable element maps per app | Phase 2 |

### 2.3 Over-Engineering Risks

| Component | Risk | Recommendation |
|-----------|------|----------------|
| OCR Fallback | Requires CameraX/ImageAnalysis setup, adds 3-5 days | **DEFER to Phase 2**. POC avoids FLAG_SECURE apps entirely |
| ADB Helper | Requires `Runtime.exec()` or ADB library, adds complexity | **DEFER to Phase 2**. POC runs everything in-process |
| Complex Event Bus | Overkill for linear workflow execution | **Simplify**: use a basic callback/event-listener pattern |
| Weighted Multi-Strategy Selectors | Over-engineered for POC | **Simplify**: use linear fallback (resource_id → text → content_description) |

### 2.4 Component Boundary Analysis

The revised architecture has clear boundaries:

```
┌─────────────────────────────────────────────┐
│  YAML Editor (UI)                           │
│  ──── user input / execution results ────    │
├─────────────────────────────────────────────┤
│  YAML Parser                                │
│  ──── serialized YAML → typed workflow AST ─│
├─────────────────────────────────────────────┤
│  Execution Engine                           │
│  ──── step-by-step orchestration ────────── │
│  │  Selector Engine (find)                  │
│  │  Retry/Timeout Engine (reliability)      │
│  │  Error Handler (structured recovery)     │
│  │  Event Bus (async UI events)             │
│  │  State Manager (UI snapshot)             │
│  │                                        │
│  └─── Accessibility Service (actions) ─────│
├─────────────────────────────────────────────┤
│  Android OS                                 │
└─────────────────────────────────────────────┘
```

**Assessment**: Boundaries are clear. The Accessibility Service acts as the single bridge to the Android OS, which is correct. The Event Bus and State Manager are appropriately decoupled.

---

## 3. Technology Recommendations

### 3.1 Language: Kotlin (Recommended) vs Java

| Criteria | Kotlin | Java |
|----------|--------|------|
| Modern Android standard | Yes (Android Studio default) | Legacy |
| Conciseness (POC speed) | 30-40% less code | Verbose |
| Null safety | Built-in (`?`, `!!`, `?.`) | Manual |
| Coroutines (async) | Native (`suspend`, `launch`) | Callbacks / RxJava |
| Interop with Java | Full bidirectional | N/A |
| Community momentum | Dominant (90%+ new apps) | Declining |

**Verdict: Kotlin** — the POC timeline (15-23 days) demands conciseness. Coroutines simplify async Accessibility Service event handling. Java would add unnecessary verbosity for a discovery POC.

### 3.2 YAML Library: SnakeYAML vs Jackson YAML

| Criteria | SnakeYAML | Jackson YAML | Custom Parser |
|----------|-----------|-------------|---------------|
| Maturity | Very high (10+ years) | High | N/A |
| Size | ~250KB | ~500KB | N/A |
| Kotlin support | Works (no native) | Works (no native) | N/A |
| Expression support | None (needs extension) | None | Full control |
| Android compatibility | Excellent | Good | N/A |
| Maintenance | Stable, low activity | Stable | High |

**Verdict: SnakeYAML** — smaller footprint, battle-tested, and sufficient for POC. The expression engine (`${variable}` support) is a Phase 2 concern. For POC, pure YAML parsing is adequate.

**Caveat**: SnakeYAML 2.x removed the auto-tagging feature that caused CVE-2017-18640. Use SnakeYAML 2.0+ with `LoaderOptions` for safety.

### 3.3 Accessibility Service Implementation

**Recommended approach: Native `AccessibilityService` subclass**

Do NOT wrap AndroidX UI Automator (`UiDevice`, `UiSelector`). While UI Automator is excellent for testing, it is designed as an instrumentation test framework, not a runtime automation library. Using it inside a regular app requires:

- Instrumentation test APK setup (complex for POC)
- Separate test infrastructure
- Not designed for runtime use

**Instead, use the native API directly:**

```kotlin
class AutomationService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Handle UI events
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }
    
    fun clickNode(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    
    fun typeText(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
    }
}
```

This gives full control and avoids the instrumentation test dependency.

### 3.4 UI Framework: Jetpack Compose vs XML Layouts

| Criteria | Jetpack Compose | XML Layouts |
|----------|----------------|-------------|
| Modern standard | Yes (Android recommended) | Legacy |
| Development speed | Faster (less boilerplate) | Slower |
| Code sharing with logic | Better (single language) | Separate (Kotlin + XML) |
| Learning curve | Moderate (declarative) | Low (mature) |
| POC suitability | Excellent | Good |

**Verdict: Jetpack Compose** — for a POC, Compose reduces boilerplate significantly. The YAML editor is essentially a `TextField` with a `Button` — both are trivial in Compose. This keeps the POC UI development to 1-2 days rather than 2-3.

### 3.5 Build System: Gradle (Kotlin DSL)

Standard Android Gradle Plugin (AGP) with Kotlin DSL (`build.gradle.kts`). No deviation needed — this is the Android ecosystem standard.

**Minimum SDK**: API 26 (Android 8.0) — required for foreground service mandate.  
**Target SDK**: API 34 (Android 14).

---

## 4. Component Architecture (POC-Scoped)

### 4.1 POC Component Breakdown (Phase 1 Scope)

For the POC, the architecture is **trimmed to 5 core components**:

```
┌─────────────────────────────────────────────┐
│  C1: YAML Editor (Compose UI)               │
│  - Paste/present YAML workflow              │
│  - Run/Stop buttons                         │
│  - Execution log output                     │
├─────────────────────────────────────────────┤
│  C2: YAML Parser                            │
│  - Parse workflow YAML → Workflow AST       │
│  - Validate structure (steps, actions)      │
│  - Extract action params                    │
├─────────────────────────────────────────────┤
│  C3: Execution Engine                       │
│  - Step-by-step orchestrator                │
│  - Action registry (launch_app, click, etc) │
│  - Retry/Timeout logic                      │
│  - Error handling & reporting               │
├─────────────────────────────────────────────┤
│  C4: Selector Engine                        │
│  - Multi-strategy search (linear fallback)  │
│  - resource_id → text → content_description │
│  - Node traversal utilities                 │
├─────────────────────────────────────────────┤
│  C5: Accessibility Service                  │
│  - Native AccessibilityService subclass     │
│  - Element actions (click, type, scroll)    │
│  - Navigation (back, home)                  │
│  - Event forwarding to engine               │
└─────────────────────────────────────────────┘
```

### 4.2 Deferred Components (Phase 2+)

| Component | Rationale for Deferral |
|-----------|----------------------|
| State Manager | POC uses single snapshot per step. No persistent state needed. |
| Event Bus | POC is linear execution. Events handled inline. |
| Error Handler (advanced) | POC uses simple on_failure → abort. No recovery workflows. |
| OCR Fallback | POC avoids FLAG_SECURE apps. Not needed. |
| ADB Helper | All actions run in-process. No system-level ops needed. |

### 4.3 Interface Definitions

#### C2 → C3: Workflow AST

```kotlin
// Core data model for parsed workflows
data class Workflow(
    val steps: List<Step>
)

sealed class Step {
    data class Action(
        val type: ActionType,
        val params: Map<String, Any?>
    ) : Step()

    data class Wait(
        val seconds: Double
    ) : Step()

    data class WaitFor(
        val selector: Selector,
        val timeout: Long,
        val pollInterval: Long = 500
    ) : Step()
}

enum class ActionType {
    LAUNCH_APP, CLICK, TYPE, BACK, HOME, SCROLL
}

data class Selector(
    val resource_id: String? = null,
    val text: String? = null,
    val content_description: String? = null,
    val class_name: String? = null
)
```

#### C3 → C5: Action Dispatch

```kotlin
// Execution Engine communicates with Accessibility Service via this interface
interface AutomationActionExecutor {
    suspend fun launchApp(packageName: String): ActionResult
    suspend fun click(selector: Selector): ActionResult
    suspend fun typeText(value: String, selector: Selector? = null): ActionResult
    suspend fun back(): ActionResult
    suspend fun home(): ActionResult
    suspend fun scroll(selector: Selector, direction: ScrollDirection): ActionResult
}

data class ActionResult(
    val success: Boolean,
    val message: String,
    val element: AccessibilityNodeInfo? = null
)
```

#### C4: Selector Engine Interface

```kotlin
interface SelectorEngine {
    fun findByResource-id(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo?
    fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo?
    fun findByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo?
    
    // Linear fallback: try each strategy in order
    fun resolve(root: AccessibilityNodeInfo, selector: Selector): AccessibilityNodeInfo?
}
```

---

## 5. Pattern Library Gap Analysis

### 5.1 Current Pattern Library Status

The existing pattern library is **entirely web-focused** (Next.js, TypeScript, Clerk, Prisma). It has **zero Android/Kotlin patterns**.

| Existing Pattern | Applicability to Phase 1 |
|-----------------|------------------------|
| `feasibility-study.md` | Used for Phase 0 ✓ |
| `technical-analysis.md` | Used for Phase 0 ✓ |
| `tool-comparison.md` | Used for Phase 0 ✓ |
| API patterns (4 files) | NOT APPLICABLE — web-only |
| UI patterns (3 files) | NOT APPLICABLE — web-only |

### 5.2 New Patterns Required for Phase 1

| Pattern | Category | Priority | Description |
|---------|----------|----------|-------------|
| **Android Project Scaffold** | Architectural | P0 — Must have | Gradle setup, module structure, min SDK 26, required permissions |
| **Accessibility Service Implementation** | Architectural | P0 — Must have | `AccessibilityService` subclass pattern, event handling, action dispatch |
| **YAML Workflow Parsing** | Research | P0 — Must have | SnakeYAML configuration, workflow AST model, validation rules |
| **Selector Engine** | Architectural | P1 — Should have | Multi-strategy search pattern, fallback chain, node traversal |
| **Execution Engine Pattern** | Architectural | P1 — Should have | Step orchestration, action registry, retry/timeout model |
| **Android Unit Testing** | Testing | P1 — Should have | JUnit 5 setup, mocking `AccessibilityNodeInfo`, testing selectors |
| **Android UI Testing** | Testing | P2 — Nice to have | UI Automator test framework for POC self-testing |

### 5.3 Pattern Creation Responsibility

| Pattern | Owner | When |
|---------|-------|------|
| Android Project Scaffold | BSA (with System Architect review) | Before Phase 1 implementation begins |
| Accessibility Service Implementation | BSA (with System Architect review) | Before Phase 1 implementation begins |
| YAML Workflow Parsing | BSA | During Phase 1 planning |
| Selector Engine | BSA | During Phase 1 planning |
| Execution Engine | BSA | During Phase 1 planning |
| Android Unit Testing | BSA | Before Phase 1 implementation begins |

**System Architect action**: Review and approve all new Android patterns before implementation. The existing pattern library has no Android patterns — this is the highest-priority gap.

---

## 6. Risk Validation

### 6.1 Phase 0 Risks — Confirmed or Updated

| ID | Risk | Phase 0 Rating | Updated Rating | Notes |
|----|------|---------------|---------------|-------|
| R1 | FLAG_SECURE blocks | Critical | **Confirmed Critical** | POC avoids FLAG_SECURE apps (Calculator, Clock). No impact on POC. |
| R2 | Resource ID instability | High | **Confirmed High** | Mitigated by multi-strategy selectors. POC tests on stable apps. |
| R3 | OEM battery optimization | High | **Confirmed High** | Mitigation: POC runs in foreground with user actively using the app. |
| R4 | Encrypted keyboards | Medium | **Confirmed Medium** | POC avoids password fields. Not tested. |
| R5 | Custom views in tree | High | **Confirmed High** | POC uses Calculator/Clock (standard Android widgets). Not an issue. |
| R6 | Fragmentation | Medium | **Confirmed Medium** | POC tests on 1-2 emulators. Real device testing deferred. |
| R7 | Play Store rejection | Medium | **Confirmed Medium** | POC is sideload-only. Not relevant. |
| R8 | User trust/permissions | Medium | **Confirmed Medium** | POC requires user to enable Accessibility Service manually. Acceptable. |
| R9 | Deep tree performance | Medium | **Confirmed Medium** | Calculator/Clock have shallow trees. Not an issue for POC. |
| R10 | YAML limits | Low | **Confirmed Low** | POC uses simple linear YAML. No expression engine needed. |

### 6.2 New Implementation Risks (Phase 1)

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| **R11** | No Android dev patterns in library | **Certain** | High | BSA creates Android patterns before implementation. System Architect reviews. |
| **R12** | Agent team has no Android-specific config | **Certain** | High | The `be-developer` and `fe-developer` agents are Next.js-focused. Need Android-aware execution. Use System Architect (Opus) for implementation, or create an Android developer agent config. |
| **R13** | Accessibility Service user setup friction | **High** | Medium | POC target is power users / developers. Acceptable for POC. |
| **R14** | Emulator Accessibility Service limitations | **Medium** | Medium | Some emulators (especially non-Google Play builds) have incomplete Accessibility Service support. Use Google Play emulator images. |
| **R15** | Foreground service notification required (API 26+) | **Certain** | Low | Mandatory — design the notification in POC UI. Not a blocker. |
| **R16** | `ACTION_SET_TEXT` unreliable on some EditText views | **Medium** | Medium | POC uses Calculator app (standard EditText). Acceptable risk. |

### 6.3 Highest-Risk Implementation Tasks

| Rank | Task | Risk Factor | Reason |
|------|------|------------|--------|
| 1 | Accessibility Service implementation | **High** | First integration with Android system API. Lifecycle management is non-trivial. |
| 2 | Selector Engine | **High** | Core reliability mechanism. If selectors don't work, nothing else matters. |
| 3 | Execution Engine + Action dispatch | **Medium** | Orchestrates everything but is straightforward if C2 and C4 work. |
| 4 | YAML Parser | **Low** | SnakeYAML handles this. Validation is the only custom work. |
| 5 | UI (YAML editor) | **Low** | Simple Compose screen. Minimal UI logic. |

---

## 7. Development Setup Guide

### 7.1 Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| **Android Studio** | Hedgehog (2023.1) or Iguana (2023.2) | IDE, emulator, build system |
| **Android SDK** | API 26 minimum, API 34 target | Target SDKs |
| **Gradle** | 8.2+ (bundled with Android Studio) | Build system |
| **JDK** | 17 | Android build requirement |
| **Git** | Latest | Version control |

### 7.2 SDK Components

```
Android SDK Required:
├── Android SDK Platform 34 (API 34)        — Target SDK
├── Android SDK Platform 26 (API 26)        — Minimum SDK
├── Android SDK Build-Tools 34.0.0          — Compilation
├── Android SDK Command-line Tools          — ADB, etc.
└── Android Emulator                        — Testing
```

### 7.3 Emulator Configuration

**Primary emulator (POC testing)**:
- Device: Pixel 6 (or any medium-density device)
- Android version: API 30 (Android 11) — good balance of features
- Google APIs: **Required** (for proper Accessibility Service support)
- RAM: 2048 MB minimum
- Internal storage: 2048 MB

**Secondary emulator (verification)**:
- Device: Pixel 3a
- Android version: API 28 (Android 9) — verifies minimum SDK

**CRITICAL**: Use **Google APIs** system images, NOT plain AOSP. AOSP emulators have incomplete Accessibility Service support.

### 7.4 Real Device (Optional for POC)

A physical Android device (API 26+) accelerates testing but is not required for POC success. If available:

- Enable Developer Options
- Enable USB Debugging
- Enable "Install via USB" (for sideloading)
- Disable battery optimization for the POC app in Settings

### 7.5 Project Structure (Proposed)

```
android-automation-app-poc/
├── app/                          — Android application module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/automation/runtime/
│       │   ├── MainActivity.kt          — Entry point, UI host
│       │   ├── editor/                  — YAML Editor (Compose)
│       │   ├── parser/                  — YAML Parser
│       │   ├── engine/                  — Execution Engine
│       │   ├── selector/                — Selector Engine
│       │   └── service/                 — Accessibility Service
│       └── res/
│           └── layout/
├── core/                         — Shared utilities (optional, Phase 1)
│   └── src/main/
├── examples/                     — Example workflows
│   ├── calculator.yaml
│   ├── clock.yaml
│   └── whatsapp_search.yaml
└── docs/
    └── architecture/             — ADRs, this review
```

### 7.6 Gradle Dependencies (POC)

```kotlin
// build.gradle.kts (app module)
dependencies {
    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")
    
    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
}
```

### 7.7 AndroidManifest.xml Requirements

```xml
<manifest>
    <!-- Required for Accessibility Service -->
    <application>
        <service
            android:name=".service.AutomationService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

```xml
<!-- res/xml/accessibility_service_config.xml -->
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:settingsActivity=".MainActivity" />
```

---

## 8. Testing Strategy

### 8.1 POC Testing Approach

The POC has **no automated test framework** requirement. Testing is manual but structured:

#### 8.1.1 Emulator-Based Testing (Primary)

| Test | Method | Expected Tool |
|------|--------|---------------|
| YAML parsing | Paste YAML, verify no crash | Manual |
| App launching | Run `launch_app` step | Manual + log verification |
| Element click | Run `click` with known selector | Manual + log verification |
| Text input | Run `type` step | Manual + log verification |
| Navigation | Run `back`/`home` steps | Manual + log verification |
| Error reporting | Trigger element-not-found | Verify error in log |
| Complete workflow | Run Calculator workflow end-to-end | Video recording |

#### 8.1.2 Workflow Test Suite (Manual but Scripted)

```yaml
# examples/calculator_add.yaml
# POC test workflow: Calculator 2 + 3 = 5

steps:
  - launch_app:
      package: com.android.calculator2

  - wait:
      seconds: 2

  - click:
      selector:
        text: "2"

  - click:
      selector:
        text: "+"

  - click:
      selector:
        text: "3"

  - click:
      selector:
        text: "="

  # Verify result: should show "5"
  - wait:
      seconds: 1

  - click:
      selector:
        text: "C"  # Clear for next test
```

### 8.2 Unit Testing (Lightweight)

| Component | Framework | Coverage Target |
|-----------|-----------|----------------|
| YAML Parser | JUnit 5 | Parse valid/invalid YAML, extract actions |
| Selector Engine | JUnit 5 + MockK | Search by text, resource_id, content_description |
| Execution Engine | JUnit 5 + MockK | Step ordering, retry logic, timeout behavior |

These unit tests should be created by the BSA as part of Phase 1 planning. They are NOT automated CI/CD tests (no CI/CD for Android POC yet).

### 8.3 UI Automator Self-Testing (Optional, Phase 2)

For the POC, the Automation Runtime could theoretically test itself using UI Automator tests. This is complex and **deferred to Phase 2**. However, AndroidX provides `UiAutomator` for writing instrumentation tests if needed later.

### 8.4 Success Evidence Requirements

For Phase 1 Go/No-Go, capture:

1. **Video recording** of Calculator workflow execution on emulator
2. **Log output** showing step-by-step execution with success/failure
3. **Screenshot** of the YAML editor UI running in the emulator
4. **Error case evidence** — screenshot/log of element-not-found handling

---

## 9. Implementation Sequence

### 9.1 Recommended Development Order

The sequence prioritizes **riskiest components first** (test the hypothesis early):

```
Week 1: Foundation (Days 1-5)
├── Day 1: Project scaffold (Gradle, Compose, AndroidManifest)
├── Day 2: Accessibility Service skeleton ( onBind, onServiceConnected, event types)
├── Day 3: YAML Parser (SnakeYAML config, Workflow AST, validation)
├── Day 4: Selector Engine (findByText, findByResourceId, findByContentDescription)
└── Day 5: Selector Engine integration (resolve with fallback chain)

Week 2: Execution & UI (Days 6-12)
├── Day 6: Execution Engine core (step loop, action dispatch)
├── Day 7: Actions — launch_app, wait
├── Day 8: Actions — click, type (integrated with Selector Engine)
├── Day 9: Actions — back, home, scroll
├── Day 10: Retry/Timeout logic (simple per-action)
├── Day 11: UI — YAML Editor (Compose TextField + Button + Log output)
└── Day 12: UI integration (connect editor to execution engine)

Week 3: Testing & Polish (Days 13-17)
├── Day 13: Calculator workflow end-to-end test
├── Day 14: Clock workflow end-to-end test (optional)
├── Day 15: Error handling verification (element not found, timeout)
├── Day 16: Unit tests (Parser, Selector Engine)
└── Day 17: Documentation, evidence collection, Go/No-Go review
```

**Total: 17 days** (within the 15-23 day estimate, with buffer for emulator issues)

### 9.2 Dependency Graph

```
Project Scaffold ──→ Accessibility Service Skeleton
                            │
                            ▼
                     YAML Parser ──→ Selector Engine ──→ Execution Engine
                            │                           │
                            ▼                           ▼
                     Workflow AST               Actions (launch/click/type)
                                                      │
                                                      ▼
                                                 UI (YAML Editor)
                                                      │
                                                      ▼
                                              End-to-End Testing
```

**Key insight**: The Accessibility Service must be working before the Selector Engine can be tested, and the Selector Engine must work before the Execution Engine can be tested. This is a critical path.

### 9.3 Parallelization Opportunities

For a larger team, these could run in parallel after the scaffold:

| Component | Can Parallelize? | Notes |
|-----------|-----------------|-------|
| YAML Parser | Yes | Independent of Android-specific code |
| Selector Engine (logic only) | Partial | Search algorithms independent of AccessibilityService |
| UI (YAML Editor) | Yes | Pure Compose, no engine dependency |
| Execution Engine | No | Depends on C2, C3, C4 |
| Accessibility Service | No | Foundation for everything else |

---

## 10. Approval Verdict

### Verdict: APPROVED WITH CHANGES

### Changes Required Before Phase 1 Implementation

| # | Change | Priority | Owner | Deadline |
|---|--------|----------|-------|----------|
| 1 | **Create Android developer agent config** (or designate System Architect as implementation agent) | P0 — Blocker | System Architect / TDM | Before Day 1 |
| 2 | **Create Android Project Scaffold pattern** in pattern library | P0 — Blocker | BSA | Before Day 1 |
| 3 | **Create Accessibility Service pattern** in pattern library | P0 — Blocker | BSA | Before Day 1 |
| 4 | **Create Android Unit Testing pattern** in pattern library | P1 | BSA | Before Day 6 |
| 5 | **Truncate architecture** — defer OCR Fallback, ADB Helper, complex Event Bus to Phase 2 | P0 | System Architect | This review |
| 6 | **Select target SDKs** — API 26 min, API 30 primary test, API 34 target | P0 | System Architect | This review |
| 7 | **Prepare example workflows** (Calculator, Clock) before implementation | P1 | BSA | Before Day 13 |

### Architecture Decisions Documented

| Decision | Value | Rationale |
|----------|-------|-----------|
| Language | Kotlin | Conciseness, coroutines, modern standard |
| YAML Library | SnakeYAML 2.x | Stability, small footprint, sufficient for POC |
| UI Framework | Jetpack Compose | Fast POC development, less boilerplate |
| Build System | Gradle (Kotlin DSL) | Android standard |
| Min SDK | API 26 (Android 8.0) | Foreground service requirement |
| Target SDK | API 34 (Android 14) | Latest stable |
| Emulator | Google Play images (Pixel 6, API 30) | Proper Accessibility Service support |
| Distribution | Sideload (APK) | Avoids Play Store for POC |
| Scope trim | Defer OCR, ADB, complex Event Bus | 15-day POC timeline |

### Risk Acceptance

| Risk | Accepted? | Notes |
|------|-----------|-------|
| FLAG_SECURE limitation | Yes | POC avoids affected apps |
| Resource ID instability | Yes | Multi-strategy selectors mitigate |
| OEM battery optimization | Yes | Foreground service + active user |
| No Android dev patterns | Yes (with mitigation) | Patterns created before implementation |
| Emulator Accessibility gaps | Yes | Google Play images used |

### Next Steps

1. **TDM**: Update ticket to "Phase 1: Implementation — Ready"
2. **System Architect**: Create Android developer agent config (or confirm implementation role)
3. **BSA**: Create Android-specific patterns in pattern library
4. **System Architect**: Review and approve new patterns
5. **TDM**: Kick off Phase 1 implementation

---

## Appendix A: Architecture Decision Records Required

| ADR | Topic | Priority |
|-----|-------|----------|
| ADR-001 | Kotlin over Java for Android | P0 — Document now |
| ADR-002 | SnakeYAML over custom parser | P0 — Document now |
| ADR-003 | Native AccessibilityService over UI Automator | P0 — Document now |
| ADR-004 | Jetpack Compose over XML layouts | P1 — Document before UI dev |
| ADR-005 | POC scope trims (defer OCR, ADB, Event Bus) | P0 — Document now |

## Appendix B: Files to Create

| File | Purpose | Owner |
|------|---------|-------|
| `patterns_library/architectural/android-project-scaffold.md` | Android project pattern | BSA |
| `patterns_library/architectural/accessibility-service.md` | Service implementation pattern | BSA |
| `patterns_library/testing/android-unit-testing.md` | Testing pattern | BSA |
| `examples/calculator_add.yaml` | POC test workflow | BSA |
| `docs/adr/ADR-001-kotlin-language.md` | Language decision | System Architect |
| `docs/adr/ADR-002-snakeyaml-parser.md` | YAML library decision | System Architect |
| `docs/adr/ADR-003-native-accessibility-service.md` | Service approach decision | System Architect |

---

*Architecture Review completed by System Architect*  
*Date: 2026-07-06*  
*Ticket: PROJ-000-android-automation-app-poc*  
*Phase: 1 — Proof of Concept*
