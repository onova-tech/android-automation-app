# Implementation Plan: Interrupt rules and replay tests

**Branch**: `feature/interrupts-replay` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

Interrupt rules are parsed into the AST (`InterruptRule`) and checked by the interpreter before
screen actions. Replay is a `ScreenDevice` (a `DevicePort` over fixtures with transitions) plus a
runner used by `ReplayTest` and `agp test`, with an injected clock and sleep for virtual time.

## Technical Context

**Language/Version**: Kotlin 1.9.22, JVM 17 · **Testing**: JUnit 5 · **Project Type**: JVM library + CLI

## Constitution Check

| Gate | Result |
|------|--------|
| II | Pass — rules are data with a restricted step set |
| III | Pass — rules run under the plugin's capabilities; banned in financial plugins |
| V | Pass — one retry only; failing rules never mask the skill's own failure |
| VII | Pass — replay makes plugin behavior provable on the JVM |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/
├── dsl/Interpreter.kt, dsl/Ast.kt   # InterruptRule, checks before screen actions
└── replay/ScreenDevice.kt, replay/Replay.kt
tools/agp/…/Main.kt                  # agp test
plugins/whatsapp/{interrupts.yaml,tests/,fixtures/}
```

YAML note: transitions use `after:` because YAML 1.1 reads a bare `on:` key as `true`.
