# ADR-003: Native AccessibilityService Over UI Automator for Runtime Automation

| Field | Value |
|-------|-------|
| **ADR** | ADR-003 |
| **Status** | Accepted |
| **Date** | 2026-07-06 |
| **Context** | PROJ-000 Phase 1 POC — Element interaction approach for Android Automation Runtime |
| **Deciders** | System Architect, BSA |

## Context

The POC must interact with Android UI elements (click, type, scroll, navigate). Two approaches were evaluated:

1. **Native `AccessibilityService` subclass** — Direct use of `AccessibilityNodeInfo` API
2. **AndroidX UI Automator (`UiDevice`, `UiSelector`)** — Instrumentation test framework

The core question: should the runtime use the same framework designed for instrumentation tests, or use the Accessibility API directly?

## Decision

**Native `AccessibilityService` subclass is selected** for all runtime element interactions.

### Rationale

| Criterion | Native AccessibilityService | UI Automator |
|-----------|----------------------------|--------------|
| Runtime use in app | Designed for this | Designed for tests |
| Setup complexity | Simple (service declaration) | Complex (instrumentation APK) |
| APK footprint | Minimal | Requires test infrastructure |
| Element access | Full via `AccessibilityNodeInfo` | Via `UiSelector` (limited API) |
| Gesture support | `performAction(ACTION_CLICK)`, scroll, setText | `click()`, `swipe()`, `pressKey()` |
| Tree traversal | Recursive traversal of `AccessibilityNodeInfo` | Flat `findObject()` / `findObjects()` |
| Maintenance | Stable API (Android 4.0+) | Test framework (may change) |

### Why Not UI Automator

UI Automator is an **instrumentation test framework**, not a runtime automation library:

- Requires a separate test APK with instrumentation setup
- Designed for CI/CD test pipelines, not runtime use inside an app
- Limited API surface (no tree traversal, no custom selectors)
- `UiDevice` and `UiSelector` classes are in the test runtime, not the app runtime
- Adding test infrastructure to a POC adds 3-5 days of setup with no runtime benefit

### Why Native AccessibilityService

The Accessibility API is purpose-built for this use case:

- `rootInActiveWindow` provides the full accessibility tree
- `findAccessibilityNodeInfosByText()` provides built-in search
- `performAction(ACTION_CLICK)` performs reliable element interaction
- `performAction(ACTION_SET_TEXT, Bundle)` handles text injection
- `performGlobalAction(GLOBAL_ACTION_BACK/HOME)` handles navigation
- API has been stable since Android 4.0 (API 14)
- `canPerformGestures` flag enables full interaction capability

## Consequences

### Positive

- No test infrastructure needed — the app IS the automation runtime
- Full control over element interaction via `AccessibilityNodeInfo` API
- Can traverse the entire accessibility tree for selector matching
- Minimal setup — declare service in manifest, configure in XML
- Stable API surface across Android versions

### Negative

- Accessibility Service requires user to manually enable in Settings (no programmatic enable)
- `FLAG_SECURE` apps block tree access (documented limitation)
- `ACTION_SET_TEXT` can be unreliable on some custom EditText views

## References

- Architecture Review, Section 3.3: Accessibility Service Implementation
- Android docs: [Creating an Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service)
- `AccessibilityNodeInfo` API reference
- `AccessibilityService` API reference
