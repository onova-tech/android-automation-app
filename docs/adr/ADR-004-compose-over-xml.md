# ADR-004: Use Jetpack Compose Over XML Layouts

- **Status**: Accepted
- **Date**: 2026-07-06
- **Authors**: System Architect (PROJ-000)
- **Decisions**:
  - Use Jetpack Compose for all UI development
  - Target API 26+ (Android 8.0 Oreo)
  - Target SDK 34 (Android 14)

## Context

The Android Automation POC requires a YAML editor UI with:

- Real-time YAML content editing
- Live validation feedback
- Simple, focused single-screen editor interface
- Accessibility Service integration for automation

The POC phase demands rapid iteration and minimal boilerplate to validate the core automation workflow before committing to long-term architecture.

## Decision

We will use **Jetpack Compose** as the primary UI toolkit for all Android development in this project.

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| UI Toolkit | Jetpack Compose | 1.6+ |
| Min SDK | Android | 26 (Oreo) |
| Target SDK | Android | 34 (Android 14) |
| Build System | AGP | 8.x |
| Language | Kotlin | 2.0+ |
| Dependency Injection | Hilt | Latest stable |
| State Management | StateFlow + ViewModel | Jetpack lifecycle |
| Navigation | Navigation Compose | 2.7+ |
| YAML Library | SnakeYAML | 2.x |

### Implementation Pattern

UI screens will follow this pattern:

```kotlin
// 1. Define state model
data class EditorState(val content: String, val errors: List<String>)

// 2. ViewModel with StateFlow
class EditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
}

// 3. Composable screen
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // UI here
    }
}
```

## Rationale

1. **Reduced boilerplate**: Compose eliminates XML layout files, View bindings, and findViewById calls. The YAML editor screen is straightforward — Compose expresses it concisely.

2. **Faster POC development**: Declarative UI paradigm means less wiring code. Changes to state immediately reflect in the UI. This accelerates iteration during the POC phase.

3. **Modern Android standard**: Jetpack Compose is Google's recommended UI toolkit. It is the future of Android development and aligns with industry direction.

4. **Kotlin-first**: Compose is a Kotlin library (not Java + XML), which fits our Kotlin-only codebase. This avoids mixing paradigms and reduces cognitive overhead.

5. **Single responsibility**: The POC editor is a single focused screen. Compose's declarative model maps naturally to this — state drives the UI, no imperative view management needed.

## Consequences

### Positive

- **Development speed**: ~40-50% less code for equivalent UI vs XML + ViewBinding
- **Testability**: Composable functions are unit-testable without Android framework dependencies
- **Maintainability**: State-driven UI is easier to reason about than imperative view updates
- **Hot reload**: Compose's dev experience is faster for iterative POC work

### Negative

- **Learning curve**: Team must be comfortable with Compose (declarative paradigm shift)
- **Debugging**: Compose stack traces can be verbose; requires familiarity with Compose debugging tools
- **Ecosystem maturity**: Compose is newer than XML — fewer Stack Overflow answers for edge cases
- **AGP version**: Requires AGP 8.x which may have build system differences from XML-based projects

### Mitigations

- Use pattern library (`patterns_library/android/compose-screen.md`) to standardize approach
- Keep POC scope small — single screen reduces exposure to edge cases
- AGP 8.x build issues handled by BSA during pattern creation

## Alternatives Considered

### XML Layouts with ViewBinding

**Rejected** because:

- Requires separate XML layout files for each UI element
- ViewBinding adds boilerplate (generated classes, type-safe but verbose access)
- Imperative UI updates (`findViewById`, `setText`, `setVisibility`)
- More code for a simple editor screen
- Not aligned with modern Android development direction

**Example overhead comparison** for a simple text editor with button:

```xml
<!-- XML approach: layout file + ViewBinding + activity/fragment -->
<!-- ~50-80 lines across 3 files -->

<!-- Compose approach: single composable function -->
<!-- ~15-25 lines in 1 file -->
```

### No decision (defer)

**Rejected** because:

- POC requires immediate UI development
- Deferred decisions compound technical debt
- XML is legacy in the Android ecosystem

## Related Decisions

- **ADR-001**: Project architecture and module structure (app/ module with editor/, parser/, engine/, selector/, service/ packages)
- **ADR-002**: YAML parsing with SnakeYAML 2.x
- **ADR-003**: Accessibility Service for automation

## References

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [AGP 8.x Migration Guide](https://developer.android.com/build/releases/gradle-plugin)
- [Android Compatibility Matrix](https://developer.android.com/about/dashboards)
