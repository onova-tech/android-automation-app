# ADR-005: POC Scope Trims — Deferred Components for Phase 1

| Field | Value |
|-------|-------|
| **ADR** | ADR-005 |
| **Status** | Accepted |
| **Date** | 2026-07-06 |
| **Context** | PROJ-000 Phase 1 POC — Scope management for 15-day timeline |
| **Deciders** | System Architect, BSA |

## Context

The Phase 0 architecture defined 10 components for the automation runtime. However, the POC timeline (15-23 days) and the core question ("can we automate a complete workflow in a real app?") require trimming scope. The full architecture is the target design for Phase 2; Phase 1 needs only what answers the core question.

## Decision

**Five components are deferred to Phase 2.** Phase 1 implements only the 5 core components needed to parse and execute a linear YAML workflow against a real Android app.

### Deferred Components

| Component | Reason for Deferral | Phase 2 Plan |
|-----------|-------------------|--------------|
| **OCR Fallback** | Requires CameraX/ImageAnalysis setup, adds 3-5 days. POC avoids FLAG_SECURE apps entirely, so OCR is not needed. | Phase 2: Add CameraX dependency, implement OCR-based element detection for FLAG_SECURE apps. |
| **ADB Helper** | Requires `Runtime.exec()` or ADB library, adds complexity. POC runs everything in-process, no system-level operations needed. | Phase 2: Add ADB helper for system-level operations (package install, permission grant). |
| **State Manager** | POC uses a single snapshot per step. No persistent state or state history is needed for linear workflow execution. | Phase 2: Add AppState with history, diff detection, and state-based selectors. |
| **Event Bus (complex)** | POC is linear execution. Events handled inline without pub/sub infrastructure. | Phase 2: Add typed EventBus for async event handling (dialog detection, toast handling). |
| **Error Handler (advanced)** | POC uses simple `on_failure: ABORT`. No recovery workflows or complex retry chains needed. | Phase 2: Add full retry/timeout/circuit-breaker model with configurable policies. |

### POC Scope (Phase 1 — 5 Core Components)

```
┌─────────────────────────────────────────────────┐
│  C1: YAML Editor (Compose UI)                   │
│  - Paste/present YAML workflow                   │
│  - Run/Stop buttons                              │
│  - Execution log output                          │
├─────────────────────────────────────────────────┤
│  C2: YAML Parser                                │
│  - Parse workflow YAML → Workflow AST            │
│  - Validate structure (steps, actions)           │
│  - Extract action params                         │
├─────────────────────────────────────────────────┤
│  C3: Execution Engine                           │
│  - Step-by-step orchestrator                     │
│  - Action registry (launch_app, click, etc)      │
│  - Retry/Timeout logic (simple per-action)       │
│  - Error handling & reporting                    │
├─────────────────────────────────────────────────┤
│  C4: Selector Engine                            │
│  - Multi-strategy search (linear fallback)       │
│  - resource_id → text → content_description      │
│  - Node traversal utilities                      │
├─────────────────────────────────────────────────┤
│  C5: Accessibility Service                      │
│  - Native AccessibilityService subclass          │
│  - Element actions (click, type, scroll)         │
│  - Navigation (back, home)                       │
│  - Event forwarding to engine                    │
└─────────────────────────────────────────────────┘
```

## Rationale

The core POC question is: **"Can we automate a complete workflow in a real app?"** This requires:

1. A way to define workflows (YAML Editor)
2. A way to parse them (YAML Parser)
3. A way to execute them (Execution Engine)
4. A way to find UI elements (Selector Engine)
5. A way to interact with the OS (Accessibility Service)

Everything else (OCR, ADB, complex state, event bus, advanced error handling) addresses **production concerns** — app updates, FLAG_SECURE apps, complex recovery workflows, async event handling. These are Phase 2 problems.

## Consequences

### Positive

- 15-day POC timeline is achievable
- Focus on the core hypothesis: Accessibility Services work for generic automation
- Calculator demo proves the concept end-to-end
- Deferred components have clear Phase 2 implementations defined

### Negative

- POC cannot demonstrate FLAG_SECURE app automation (accepted — POC avoids these apps)
- No async event handling (dialogs, toasts appear during execution but are not auto-handled)
- No state-based selectors (only direct selectors: text, resource_id, content_description)
- Simpler error handling (ABORT/CONTINUE only, no circuit breaker)

## References

- Architecture Review, Section 2.3: Over-Engineering Risks
- Architecture Review, Section 4.1: POC Component Breakdown
- Architecture Review, Section 4.2: Deferred Components
