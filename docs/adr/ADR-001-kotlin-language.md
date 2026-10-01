# ADR-001: Kotlin as the Development Language for Android

| Field | Value |
|-------|-------|
| **ADR** | ADR-001 |
| **Status** | Accepted |
| **Date** | 2026-07-06 |
| **Context** | PROJ-000 Phase 1 POC — Android Automation Runtime |
| **Deciders** | System Architect, BSA |

## Context

Phase 0 feasibility confirmed that Android Accessibility Services can automate standard apps. Phase 1 requires building a minimal proof of concept (15-23 day timeline) that includes a YAML parser, execution engine, selector engine, and Accessibility Service bridge. The team must choose between Kotlin and Java for the Android implementation.

## Decision

**Kotlin is selected as the development language** for the entire Android codebase.

### Rationale

| Criterion | Kotlin | Java |
|-----------|--------|------|
| Android ecosystem standard | Yes — default in Android Studio | Legacy, declining adoption |
| Code conciseness | 30-40% less code than Java | Verbose boilerplate |
| Null safety | Built-in (`?`, `!!`, `?.`, `lateinit`) | Manual checks everywhere |
| Data classes | Native (`data class` with `equals`, `hashCode`, `copy`) | Lombok or manual overrides |
| Coroutines | Native (`suspend`, `launch`, `runTest`) | Callbacks or RxJava (heavy dependency) |
| POC development speed | Fast — minimal boilerplate | Slower — more lines to write and maintain |
| Interop with Java | Full bidirectional | N/A |

### Coroutines Advantage

The Accessibility Service is inherently async — events arrive on a background thread, and the execution engine needs structured concurrency. Kotlin coroutines provide:

- `suspend` functions for natural async flow (no callback hell)
- `Dispatchers.Main` for UI thread operations
- `runTest` for unit testing async code
- Structured concurrency via `CoroutineScope` tied to component lifecycles

## Consequences

### Positive

- Faster POC development (15-23 day timeline is achievable)
- Null safety eliminates a whole class of `NullPointerException` crashes
- Data classes simplify the workflow AST model
- Coroutines simplify async event handling
- Modern Android development best practices followed

### Negative

- Team must have Kotlin proficiency (Phase 0 reports indicate this is acceptable)
- Slightly larger compiled bytecode than equivalent Java (negligible for POC)

## References

- [Android documentation: Kotlin as primary language](https://developer.android.com/kotlin)
- Architecture Review, Section 3.1: Technology Recommendations — Language
