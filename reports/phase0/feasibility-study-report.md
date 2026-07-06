# PROJ-000 Phase 0 Feasibility Study Report

**Date**: 2026-07-06  
**Status**: Analysis Complete  
**Recommendation**: GO (with caveats)  
**Session ID**: tdm-phase0-execution-20260706  

---

## Executive Summary

This report presents the findings of a comprehensive feasibility study for building a **local-first, declarative Android automation runtime** using Accessibility Services with YAML workflows. After critical analysis of Accessibility Services capabilities, comparison with existing tools, and thorough risk assessment, we conclude that:

> **The architecture is viable for a proof-of-concept, but significant reliability and fragmentation challenges must be addressed before it can be considered production-ready.**

**Recommendation: GO** — proceed to Phase 1 (Proof of Concept) with explicit acknowledgment of the technical risks identified in this report.

---

## 1. Problem Statement

### 1.1 Objective

Answer the core question:

> **Can we build a reliable, generic, declarative Android automation runtime capable of navigating and interacting with Android applications using Accessibility Services?**

### 1.2 Desired Architecture

```
Android App
    │
    ├── Workflow Editor (YAML input)
    │
    ├── YAML Parser
    │
    ├── Execution Engine
    │
    ├── State Manager
    │
    ├── Accessibility Service
    │
    └── Android Runtime
```

### 1.3 Non-Goals (Explicitly Out of Scope)

- NOT an AI assistant
- NOT an LLM orchestrator
- NOT SMS integration
- NOT a plugin marketplace

---

## 2. Accessibility Services Analysis

### 2.1 Capabilities

Android Accessibility Services provide the following automation-relevant capabilities via the `AccessibilityService` API:

| Capability | API | Reliability | Notes |
|-----------|-----|-------------|-------|
| **Element inspection** | `AccessibilityNodeInfo` | High | Full accessibility tree traversal |
| **Click** | `performAction(ACTION_CLICK)` | High | Works on most interactive elements |
| **Long press** | `performAction(ACTION_LONG_CLICK)` | High | Available since API 14 |
| **Text input** | `performAction(ACTION_SET_TEXT)` | Medium | Requires editable text fields |
| **Scroll** | `performAction(ACTION_SCROLL_FORWARD/BACKWARD)` | Medium | Depends on view implementation |
| **Focus** | `performAction(ACTION_FOCUS)` | High | For keyboard navigation |
| **Navigate back** | `onKeyDown(KEYCODE_BACK)` | High | Via `performGlobalAction()` |
| **Navigate home** | `performGlobalAction(GLOBAL_ACTION_HOME)` | High | System-level action |
| **Screenshot** | `takeScreenshot()` | Medium | API 29+, requires permission |
| **Notification access** | `AccessibilityServiceInfo` | High | Read notification content |
| **Gesture input** | `InjectInputEvent` | Low | Deprecated, unreliable |

### 2.2 Limitations and Showstoppers

#### 2.2.1 Encrypted/Protected Windows (HIGH IMPACT)

**Critical Limitation**: Apps with `FLAG_SECURE` set (banking apps, DRM content, secure folders) **cannot be inspected** by Accessibility Services. The accessibility tree returns empty or null nodes for protected windows.

- **Affected apps**: Banking apps, WhatsApp (media preview), some gaming apps
- **Impact**: Cannot automate any interaction within protected windows
- **Mitigation**: Cannot be bypassed; this is an OS-level security feature

#### 2.2.2 Hardware Keys (MEDIUM IMPACT)

- **Physical/virtual home button**: Accessibility Services **cannot** inject home key events reliably
- **Recent apps**: Cannot trigger recent apps via accessibility alone
- **Power button**: Cannot control power state
- **Volume keys**: Cannot control volume

#### 2.2.3 Encrypted Keyboards (MEDIUM IMPACT)

- Apps with `INPUT_METHOD_SECURE` keyboards (password managers, banking) prevent text injection via Accessibility Services
- `ACTION_SET_TEXT` will fail silently or throw exceptions

#### 2.2.4 Custom Views (VARIABLE IMPACT)

- Views without proper accessibility hints may not report meaningful info
- Custom drawing (Canvas-based games, charts) invisible to accessibility tree
- `ViewCompat.setAccessibilityDelegate()` can be overridden by apps

#### 2.2.5 Gesture-Based Navigation (MEDIUM IMPACT)

- Android 10+ gesture navigation replaces system navigation bar
- Back gesture, home gesture cannot be triggered via Accessibility Services alone
- Only `performGlobalAction()` works for system-level actions

### 2.3 Android Version Differences

| Feature | API 24 (7.0) | API 26 (8.0) | API 29 (10) | API 31 (12) | API 34 (14) |
|---------|-------------|-------------|-------------|-------------|-------------|
| Foreground service | No requirement | Required | Required | Required | Required |
| Permissions model | Runtime | Runtime | Runtime | Runtime | Runtime |
| Take screenshot | No | No | Yes (with permission) | Yes | Yes |
| Auto-grant permissions | N/A | Yes (for some) | Yes | No | No |
| Notification access | Manual | Manual | Manual | Manual | Manual |
| Accessibility events | Full | Full | Full | Full | Full |
| Background activity | Limited | Restricted | Restricted | Restricted | Restricted |

**Key change (API 26+)**: Foreground service is **mandatory** for Accessibility Services. The service must show a persistent notification.

**Key change (API 28+)**: `FLAG_WHISPER_MODE` — Accessibility Services can't read password fields.

**Key change (API 30+)**: Background activity recognition — apps must declare background activity usage.

**Key change (API 31+)**: `POST_NOTIFICATIONS` permission required for notifications.

### 2.4 Service Lifecycle and Reliability

| Scenario | Behavior | Impact |
|----------|----------|--------|
| App killed by OS | Service dies, must be restarted by user | HIGH — requires user intervention |
| System update | Service may be disabled, needs re-enabling | MEDIUM |
| Accessibility settings toggled off | Service disabled, needs manual re-enable | HIGH |
| Memory pressure | Foreground service protected (usually) | LOW |
| Doze mode | Background tasks limited | MEDIUM |
| OEM battery optimization | Service may be killed aggressively | HIGH (Samsung, Xiaomi) |

### 2.5 Selector Reliability Assessment

| Selector Type | Reliability | Notes |
|--------------|-------------|-------|
| `resource_id` | High | Stable within app version, changes on updates |
| `text` (label) | Medium | i18n breaks it, dynamic content changes it |
| `content_description` | Medium | Not always set, may be dynamic |
| `class_name` | Medium | Generic (Button, TextView) — not unique |
| `index` | Low | Position changes with layout modifications |
| `xpath` (accessibility) | Medium | Useful but fragile with dynamic content |

---

## 3. Existing Tool Comparison

### 3.1 Tool Comparison Matrix

| Tool | Approach | Reliability | Setup | Maintenance | Open Source | Local-First |
|------|----------|-------------|-------|-------------|-------------|-------------|
| **UI Automator** | Accessibility API | High (native) | Android Studio | Low | Yes | Yes |
| **Appium** | WebDriver + UI Automator | Medium-High | Complex | High | Yes | Yes |
| **Espresso** | View hierarchy | Very High (test-only) | Medium | Low | Yes | Yes (test) |
| **ADB + Shell** | ADB commands | Medium | Easy | Medium | N/A | Yes |
| **Scrcpy + ADB** | Screen + commands | Medium | Medium | Medium | Yes | Yes |
| **Accessibility Bot** | Direct API | Medium | Easy | Low | Yes | Yes |
| **Auto.js** | Accessibility + JS | Medium | Easy | Medium | Yes | Yes |
| **Tasker** | Automation + plugins | Medium-High | Medium | Low | No | Yes |
| **MacroDroid** | Automation + triggers | Medium | Easy | Low | No | Yes |
| **Selenium** | Web WebDriver | N/A | - | - | Yes | - |
| **Playwright** | Modern web | N/A | - | - | Yes | - |

### 3.2 Detailed Tool Analysis

#### 3.2.1 UI Automator (AndroidX)

**Architecture**: Uses `uiautomator` framework, directly accesses accessibility tree via `AccessibilityNodeInfo`

**Strengths**:
- Google-maintained, officially supported
- Direct access to accessibility tree
- No root required
- Stable API surface
- Good documentation

**Weaknesses**:
- Requires Android Studio / Gradle setup
- Tied to Android testing ecosystem
- Not designed for general-purpose automation
- Requires instrumentation test setup

**Relevance**: This is the **closest official tool** to our approach. The `UiDevice`, `UiSelector`, and `UiObject2` APIs map directly to our needs.

**Key Insight**: UI Automator's `UiSelector` is essentially a declarative selector system. We can study its API design for our selector implementation.

#### 3.2.2 Appium

**Architecture**: WebDriver protocol → Appium Server → UI Automator (Android) or XCUITest (iOS)

**Strengths**:
- Cross-platform (iOS + Android)
- Large ecosystem, many bindings
- WebDriver standard
- Good community

**Weaknesses**:
- Heavy architecture (client → Appium server → device)
- Slow (network round-trips)
- Complex setup and maintenance
- Reliability issues with flaky tests
- Not local-first in spirit

**Key Insight**: Appium's `FindStrategy` and element locator syntax could inform our YAML selector design. Their retry/implicit-wait model is relevant.

#### 3.2.3 Auto.js / Auto.js Pro

**Architecture**: Accessibility Service + JavaScript execution engine

**Strengths**:
- Proves Accessibility Services can automate generic apps
- Rich element finding API
- Built-in selector system (id, text, desc, className)
- Open source

**Weaknesses**:
- JavaScript dependency injection required
- Not declarative (imperative scripting)
- Reliability varies by app
- Project largely abandoned

**Key Insight**: Auto.js's selector API (`$id()`, `$text()`, `$desc()`, `$className()`) is essentially what we want in YAML form. Study its selector fallback chain.

#### 3.2.4 Tasker + AutoInput Plugin

**Architecture**: Tasker profiles + AutoInput plugin (Accessibility Service)

**Strengths**:
- Proven production system
- Massive user base
- Rich action set
- Works without root

**Weaknesses**:
- Paid app ($4.99)
- Not open source
- Proprietary plugin format
- Not developer-friendly

**Key Insight**: Tasker's approach to automation profiles is similar to our YAML workflows. Study their action model and error handling.

#### 3.2.5 ADB + Scrcpy

**Architecture**: USB/WiFi ADB → shell commands → device interaction

**Strengths**:
- Works on any Android device
- No app installation needed on target
- Can interact with system UI
- Good for testing

**Weaknesses**:
- Requires USB debugging enabled
- Limited to what ADB can do
- No accessibility tree access
- Not suitable for end-user automation

**Key Insight**: ADB can complement Accessibility Services for system-level actions (install, permissions, screenshots).

### 3.3 Recommendation from Comparison

**Primary Reference**: **AndroidX UI Automator** — study its selector API and element model.

**Secondary Reference**: **Auto.js** — study its selector fallback chain and accessibility tree traversal.

**Alternative**: **ADB** — for system-level operations (install, screenshot, permissions).

**Avoid**: Building a WebDriver-like protocol layer (unnecessary complexity for local-first use case).

---

## 4. Architecture Critique and Assumption Challenge

### 4.1 Critical Analysis of Proposed Architecture

#### Assumption 1: "Accessibility Services can reliably automate ANY Android app"

**Verdict: FALSE**

Accessibility Services cannot interact with:
- Apps using `FLAG_SECURE` (banking, DRM)
- Apps using hardware-accelerated custom rendering (games)
- Apps using encrypted keyboards
- System overlays that block accessibility events

**Mitigation**: Build a fallback mechanism for supported apps only. Clearly document which apps are automation-compatible.

#### Assumption 2: "YAML is sufficient for workflow definition"

**Verdict: PARTIALLY TRUE**

YAML is adequate for:
- Linear step sequences ✓
- Simple conditionals (if/else) ✓
- Basic loops (repeat N times) ✓
- Parameterized workflows ✓

YAML is insufficient for:
- Complex state management (needs external state store)
- Dynamic selector computation (needs expressions engine)
- Error recovery (needs retry/timeout semantics)
- Parallel execution (needs concurrency model)

**Recommendation**: Use YAML as the **serialization format**, but implement a small expression language for dynamic values and conditions.

#### Assumption 3: "The execution engine can be deterministic"

**Verdict: FALSE (in practice)**

Android UI is inherently **non-deterministic**:
- Network-dependent content loading
- Animation timing varies
- Background processes interfere
- OEM customizations change behavior

**Mitigation**: Build probabilistic selectors with confidence scores and retry mechanisms. The engine must be **resilient** rather than purely deterministic.

#### Assumption 4: "Resource IDs are stable selectors"

**Verdict: FALSE (long-term)**

- Resource IDs change with every app update (R.id.* generation is non-deterministic)
- Minification tools rename resource IDs
- Different APK builds have different IDs
- Custom builds of same app may have different IDs

**Mitigation**: Support **multiple selector strategies** with fallback chains. Resource ID → text → content description → position.

#### Assumption 5: "Local-first eliminates server dependencies"

**Verdict: TRUE (with caveats)**

The runtime itself is local-first ✓
- YAML files stored locally
- Execution on device
- No cloud dependency

Caveats:
- Workflow sharing requires some transport (file, SMS, web)
- OTA updates to the runtime itself need delivery mechanism
- Multi-device coordination requires a transport layer

### 4.2 Missing Architecture Concerns

#### 4.2.1 State Management Model

The proposed architecture lacks a clear state model. Android UI state is:
- **Ephemeral**: Changes with every user interaction
- **Hierarchical**: Tree structure with parent-child relationships
- **Transient**: Toasts, dialogs, snackbar appear and disappear
- **Async**: Network calls, animations, loading states

**Recommendation**: Implement a **snapshot-based state model**:
```
AppState = {
  timestamp: long,
  rootNode: AccessibilityNodeInfo,
  activePackage: String,
  activeActivity: String,
  statusBar: StatusBarState,
  navigationBar: NavigationBarState
}
```

#### 4.2.2 Error Handling Model

Current proposal doesn't address:
- What happens when a click fails?
- What happens when an element isn't found?
- What happens when the app crashes mid-workflow?
- What happens when the Accessibility Service is killed?

**Recommendation**: Implement structured error handling:
```
ErrorPolicy = {
  onNotFound: RETRY | SKIP | ABORT,
  onTimeout: WAIT | SKIP | ABORT,
  onException: RETRY | SKIP | ABORT,
  maxRetries: int,
  retryDelay: long
}
```

#### 4.2.3 Event Listening

The execution engine needs to **listen** for UI events during execution:
- Dialogs appearing (terms of service, permissions)
- Toast messages
- Navigation changes
- App switching

**Recommendation**: Implement an event-driven architecture with an event bus between the Accessibility Service and the execution engine.

---

## 5. Risk Assessment

### 5.1 Risk Matrix

| # | Risk | Likelihood | Impact | Severity | Showstopper |
|---|------|-----------|--------|----------|-------------|
| R1 | FLAG_SECURE blocks automation | High | High | **Critical** | YES (for some apps) |
| R2 | Resource IDs change on app updates | High | Medium | **High** | NO (mitigable) |
| R3 | OEM battery optimization kills service | High | High | **High** | NO (mitigable) |
| R4 | Encrypted keyboards block text input | Medium | Medium | **Medium** | NO (known limitation) |
| R5 | Accessibility tree too deep/slow | Medium | Medium | **Medium** | NO (mitigable) |
| R6 | Custom views not in accessibility tree | High | Medium | **High** | NO (mitigable via OCR fallback) |
| R7 | Android fragmentation (10K+ devices) | High | Medium | **Medium** | NO (expected) |
| R8 | Play Store policy rejection | Medium | High | **High** | NO (sideload distribution) |
| R9 | User trust/permission fatigue | High | Medium | **Medium** | NO (education) |
| R10 | YAML expressiveness insufficient | Medium | Low | **Low** | NO (extendable) |

### 5.2 Critical Risk Deep Dive

#### R1: FLAG_SECURE / Encrypted Windows (SHOWSTOPPER for some use cases)

**Description**: Apps can flag their windows as secure, preventing any screen capture or accessibility inspection.

**Affected scenarios**:
- Banking apps (most problematic)
- WhatsApp media previews
- Netflix/Disney+ DRM content
- Secure folder apps

**Workaround**: None at OS level. This is intentional security design.

**Impact on our project**: Limits the set of "generic" apps we can automate. Banking apps are specifically excluded.

#### R3: OEM Battery Optimization

**Description**: OEMs (Samsung, Xiaomi, Huawei, Oppo) aggressively kill background services to save battery.

**Affected behaviors**:
- Accessibility Service killed after screen off
- Service killed after battery optimization threshold
- Requires user to disable "optimize battery usage" per app

**Impact**: Requires guided setup wizard to disable optimization. Not automatic.

### 5.3 Mitigation Strategies

| Risk | Mitigation |
|------|-----------|
| R1 FLAG_SECURE | Document supported apps; build OCR fallback for visible content |
| R2 Resource IDs | Multi-strategy selectors with fallback chain |
| R3 Battery optimization | Setup wizard to disable optimization; request ignore optimizations permission |
| R4 Encrypted keyboards | Detect and warn; skip protected fields |
| R5 Deep trees | Depth limit (configurable); XPath-style optimization |
| R6 Custom views | OCR fallback using Android's built-in image analysis |
| R7 Fragmentation | Test on minimum 5 device profiles; document supported OEMs |
| R8 Play Store | Target sideload/distribution via GitHub or direct APK |
| R9 User trust | Transparent UI showing what automation is doing; minimal permissions |
| R10 YAML limits | Expression engine for dynamic values; structured YAML |

---

## 6. Technical Approach Recommendations

### 6.1 Recommended Architecture (Revised)

Based on findings, we recommend this revised architecture:

```
┌─────────────────────────────────────────────────┐
│                  Android App                     │
│  ┌───────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  YAML     │  │  YAML    │  │  Execution   │  │
│  │  Editor   │→ │  Parser  │→ │  Engine      │  │
│  └───────────┘  └──────────┘  └──────────────┘  │
│                                      │           │
│  ┌───────────┐  ┌──────────┐        ▼           │
│  │  State    │←─┤  Event   │  ┌──────────────┐  │
│  │  Manager  │  │  Bus     │  │  Selector    │  │
│  └───────────┘  └──────────┘  │  Engine      │  │
│                    ▲           └──────────────┘  │
│  ┌───────────┐  ┌─┴──────────┐  ┌────────────┐  │
│  │  Error    │  │  Retry /   │  │ Accessibility│ │
│  │  Handler  │  │  Timeout   │←─│  Service   │  │
│  └───────────┘  └────────────┘  └────────────┘  │
│                                      │           │
│  ┌───────────┐  ┌──────────┐        ▼           │
│  │  OCR      │  │  ADB     │  ┌──────────────┐  │
│  │  Fallback │  │  Helper  │  │  Android OS  │  │
│  └───────────┘  └──────────┘  └──────────────┘  │
└─────────────────────────────────────────────────┘
```

### 6.2 Key Changes from Original Proposal

1. **Added Event Bus**: For async UI event handling (dialogs, toasts, navigation)
2. **Added State Manager**: For snapshot-based UI state tracking
3. **Added Retry/Timeout Engine**: For reliability
4. **Added Error Handler**: For structured error recovery
5. **Added OCR Fallback**: For custom views and FLAG_SECURE content
6. **Added ADB Helper**: For system-level operations

### 6.3 Selector Strategy Recommendation

Implement a **weighted multi-strategy selector** system:

```yaml
selector:
  fallback:
    - strategy: resource_id
      weight: 10
    - strategy: text
      weight: 8
    - strategy: content_description
      weight: 7
    - strategy: class_name + index
      weight: 5
    - strategy: xpath
      weight: 3
```

### 6.4 Workflow Language Recommendation

Extend YAML with expression support:

```yaml
steps:
  - launch_app:
      package: com.whatsapp

  - wait_for:
      selector:
        text: "Search"
      timeout: 5000
      poll_interval: 500

  - click:
      selector:
        text: "Search"
      retries: 3
      retry_delay: 1000

  - type:
      value: "Hello"

  - click:
      selector:
        content_description: "Send"
      on_failure:
        action: screenshot
        then: abort

  - screenshot:
      path: /sdcard/automation/capture.png

  - if:
      condition: "${last_action.success}"
      then:
        - log: "Workflow completed successfully"
      else:
        - log: "Workflow failed"
        - screenshot:
            path: /sdcard/automation/error.png
```

### 6.5 Minimum Viable Actions (Phase 1)

Based on feasibility analysis, implement these first:

| Priority | Action | Reliability | Complexity |
|----------|--------|-------------|------------|
| P0 | `launch_app` | High | Low |
| P0 | `wait` / `wait_for` | High | Medium |
| P0 | `click` (with retries) | High | Medium |
| P0 | `type` | Medium | Medium |
| P1 | `back` | High | Low |
| P1 | `home` | High | Low |
| P1 | `scroll` | Medium | Medium |
| P1 | `screenshot` | Medium | High |
| P2 | `find_text` | Medium | High |
| P2 | `ocr` | Low | High |

---

## 7. Go/No-Go Decision

### 7.1 Decision

**GO** — Proceed to Phase 1 (Proof of Concept)

### 7.2 Justification

**Reasons for GO:**
1. Accessibility Services API is mature and stable (available since Android 4.0, API 14)
2. AndroidX UI Automator proves the approach works (used by Google for their own testing)
3. No root required — accessible to end users
4. Local-first architecture aligns with privacy requirements
5. Open-source precedents exist (Auto.js, Accessibility Bot) proving viability
6. The core question is answerable with a POC: "Can we automate Calculator, Clock, or basic WhatsApp search?"

**Reasons for CAUTION:**
1. FLAG_SECURE blocks automation of sensitive apps (banking, DRM)
2. OEM fragmentation creates reliability challenges
3. Selector stability is a long-term maintenance burden
4. User setup complexity (enabling Accessibility Services, disabling battery optimization)
5. Not suitable for production-critical automations

### 7.3 Phase 1 Success Criteria

The POC will be considered successful if it can:

1. ✅ Load a YAML workflow from a textbox
2. ✅ Execute workflow steps using Accessibility Services
3. ✅ Launch an application (e.g., Calculator)
4. ✅ Wait for a UI element to appear
5. ✅ Click an element by resource_id or text
6. ✅ Type text into an editable field
7. ✅ Navigate back to previous screen
8. ✅ Detect and report failures
9. ✅ Execute a complete workflow in Calculator or Clock app
10. ✅ Handle basic error scenarios (element not found, timeout)

### 7.4 Phase 1 Estimated Effort

| Component | Estimate |
|-----------|----------|
| Accessibility Service implementation | 2-3 days |
| YAML parser | 1-2 days |
| Selector engine (multi-strategy) | 3-4 days |
| Execution engine | 2-3 days |
| State manager | 1-2 days |
| Event bus | 1 day |
| Error handling / retry | 1-2 days |
| UI (YAML editor) | 2-3 days |
| Testing on real devices | 2-3 days |
| **Total** | **15-23 days** |

---

## 8. Questions to Investigate

### 8.1 Architecture (UNRESOLVED)

| Question | Current Understanding | Action Needed |
|----------|----------------------|---------------|
| Can Accessibility Services run in headless mode? | No, requires UI interaction | Accept — not a blocker for POC |
| How to handle multi-window on tablets? | Not prioritized for POC | Phase 2 |
| How to handle split-screen? | Limited support | Phase 2 |
| Can we listen for accessibility events in real-time? | Yes, via `onAccessibilityEvent()` | Implement in Phase 1 |

### 8.2 Accessibility (UNRESOLVED)

| Question | Current Understanding | Action Needed |
|----------|----------------------|---------------|
| What's the max tree depth we should traverse? | Unknown — need empirical testing | Test during POC |
| How fast is tree traversal on complex screens? | Unknown — need profiling | Test during POC |
| Can we register for specific event types? | Yes, via `AccessibilityServiceInfo` | Implement in Phase 1 |

### 8.3 Execution Engine (UNRESOLVED)

| Question | Current Understanding | Action Needed |
|----------|----------------------|---------------|
| How to model workflow state? | Snapshot-based approach proposed | Design in Phase 1 |
| How to handle async operations? | Event bus + polling | Design in Phase 1 |
| How to serialize/deserialize workflow state? | Not designed yet | Design in Phase 1 |

### 8.4 Selectors (UNRESOLVED)

| Question | Current Understanding | Action Needed |
|----------|----------------------|---------------|
| What's the most reliable selector for our use case? | Multi-strategy fallback | Test during POC |
| How to handle dynamic content (ads, toasts)? | Filter by stability / timeout | Design in Phase 1 |
| Should selectors support regex? | Useful but adds complexity | Phase 2 |

### 8.5 Security (PARTIALLY RESOLVED)

| Question | Current Understanding | Action Needed |
|----------|----------------------|---------------|
| What permissions does the app need? | Accessibility Services + foreground service | Document for Phase 1 |
| Can the app be published on Play Store? | Difficult — Accessibility Services scrutiny | Sideloading for Phase 1 |
| What data does the service access? | Accessibility tree (visible text, labels, structure) | Privacy policy needed |

---

## 9. Deliverables

### 9.1 Phase 0 Deliverables (This Report)

- [x] Accessibility Services capability analysis
- [x] Existing tool comparison (11 tools evaluated)
- [x] Risk assessment (10 risks identified, 2 critical)
- [x] Architecture critique (5 assumptions challenged)
- [x] Go/No-Go recommendation: **GO**
- [x] Phase 1 success criteria defined
- [x] Revised architecture proposal

### 9.2 Phase 1 Deliverables (Next)

- [ ] Android project scaffold
- [ ] Accessibility Service implementation
- [ ] YAML parser
- [ ] Selector engine
- [ ] Execution engine
- [ ] Basic UI (YAML editor)
- [ ] POC workflow execution (Calculator)

---

## 10. TDM Coordination Evidence

### Session Information

- **Session ID**: tdm-phase0-execution-20260706
- **Ticket**: PROJ-000-android-automation-app-poc
- **Phase**: Phase 0 — Feasibility Study (Execution)
- **Date**: 2026-07-06

### Gate Verification

| Gate | Status | Evidence |
|------|--------|----------|
| Spec Validation "BSA - Spec fix complete" | ✅ PASS | Completed in Phase 0 pre-gate |
| Architecture Review "System Architect - Pattern approved" | ✅ PASS | Completed in Phase 0 pre-gate |
| Ready for Execution "TDM - Orchestration ready" | ✅ PASS | Phase 0 execution initiated |

### Blocker Status

| Blocker | Status | Resolution |
|---------|--------|------------|
| No active blockers | ✅ Clear | — |

### Evidence Package

| Evidence | Location |
|----------|----------|
| Feasibility Study Report | `reports/phase0/feasibility-study-report.md` |
| Tool Comparison Analysis | `reports/phase0/tool-comparison-analysis.md` |
| Risk Assessment | `reports/phase0/risk-assessment.md` |
| Go/No-Go Recommendation | This document (Section 7) |

### Pattern Library Compliance

All 4 required patterns were followed:
- ✅ `patterns_library/architectural/feasibility-study.md` — 7 sections covered
- ✅ `patterns_library/research/technical-analysis.md` — 5 sections covered
- ✅ `patterns_library/evaluation/tool-comparison.md` — 5 sections covered
- ✅ `docs/android/security/accessibility-permissions.md` — Security considerations addressed

---

## Appendix A: Accessibility Services API Reference

### Key Classes

| Class | Purpose |
|-------|---------|
| `AccessibilityService` | Base class for accessibility services |
| `AccessibilityNodeInfo` | Represents a node in the accessibility tree |
| `AccessibilityEvent` | Event from the accessibility system |
| `AccessibilityServiceInfo` | Configuration for the service |
| `UiDevice` (AndroidX) | Device-level automation (UI Automator) |
| `UiSelector` (AndroidX) | Element selector (UI Automator) |
| `UiObject2` (AndroidX) | Element wrapper (UI Automator) |

### Key Methods

| Method | Purpose |
|--------|---------|
| `getRootInActiveWindow()` | Get root of current window's accessibility tree |
| `findAccessibilityNodeInfosByText(String)` | Find nodes by text |
| `findAccessibilityNodeInfosByViewId(int)` | Find nodes by resource ID |
| `performAction(int)` | Perform action on node (click, scroll, etc.) |
| `performGlobalAction(int)` | Perform global action (back, home, etc.) |
| `onAccessibilityEvent(AccessibilityEvent)` | Event callback |
| `onServiceConnected()` | Service lifecycle callback |

### Action Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `ACTION_CLICK` | 1 | Click the element |
| `ACTION_LONG_CLICK` | 2 | Long press the element |
| `ACTION_FOCUS` | 4 | Focus the element |
| `ACTION_CLEAR_FOCUS` | 8 | Clear focus |
| `ACTION_COPY` | 16 | Copy selected text |
| `ACTION_CUT` | 32 | Cut selected text |
| `ACTION_PASTE` | 64 | Paste text |
| `ACTION_SCROLL_FORWARD` | 128 | Scroll forward |
| `ACTION_SCROLL_BACKWARD` | 256 | Scroll backward |
| `ACTION_SET_TEXT` | 8192 | Set text (editable fields) |
| `GLOBAL_ACTION_BACK` | 1 | Navigate back |
| `GLOBAL_ACTION_HOME` | 2 | Go to home |
| `GLOBAL_ACTION_RECENTS` | 3 | Show recent apps |
| `GLOBAL_ACTION_NOTIFICATIONS` | 4 | Show notifications |

---

*Report generated as part of PROJ-000 Phase 0 Feasibility Study*  
*Next phase: Phase 1 — Proof of Concept Implementation*
