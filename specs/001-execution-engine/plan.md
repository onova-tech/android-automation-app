# Implementation Plan: Execution engine and workflow language

**Branch**: `feature/engine-foundation` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

## Summary

A bounded, verifiable workflow language interpreted by an engine in the pure-JVM `:core`
module. Actions reach the phone through `DevicePort`; targets are resolved by exact hints then a
heuristic ranker that refuses ambiguous matches.

## Technical Context

**Language/Version**: Kotlin 2.4, JVM 17

**Primary Dependencies**: SnakeYAML 2.7, kotlinx-coroutines 1.11

**Storage**: N/A

**Testing**: JUnit 5, MockK; `ScreenDevice` + `UiXml` fixtures

**Target Platform**: Android 8+ (minSdk 26, target 34); baseline device Android 13, 6 GB RAM

**Project Type**: Mobile app + JVM library + CLI

**Performance Goals**: A simple command from SMS to reply in < 30 s (to validate on a phone)

**Constraints**: No network; bounded runs; no arbitrary evaluation

## Constitution Check

| Gate | Result |
|------|--------|
| I. Self-contained | Pass — no network, no external service |
| II. Plugins are data | Pass — fixed action set, templates only read variables |
| III. Base app owns trust | Pass — limits are engine constants, not plugin data |
| IV. Channels untrusted | N/A (003) |
| V. Fail closed | Pass — `expect`, `assert`, `E_LOW_CONFIDENCE` |
| VI. Deterministic first | Pass — hints, then ranker (the baseline for 010) |
| VII. JVM-testable | Pass — `:core` behind `DevicePort` |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/
├── engine/      # DevicePort, ActionContext, ExecutionEngine, ErrorHandler, ErrorCode, actions/
├── dsl/         # Ast, DslParser, Interpreter, Templates, RunResult (RunLimits)
├── parser/      # YamlParser (safe loading, step parsing), Step
├── resolve/     # Target, TargetResolver (hints + ranker, rerank hook for 010)
└── ui/          # UiNode, UiXml (uiautomator dumps)
core/src/test/kotlin/com/proj/automation/{engine,dsl,resolve}/

app/src/main/java/com/proj/automation/
├── accessibility/  # AutomationService, AutomationBridge, AndroidDevicePort
└── ui/             # UiSnapshots (live tree → UiNode)
```

**Structure Decision**: Engine and language in `:core`; only Android adapters in `:app`.
