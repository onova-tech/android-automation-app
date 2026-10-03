# Implementation Plan: Plugin classification

**Branch**: `feature/plugin-classification` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

`PluginClassifier.classify(plugin, financialApps)` returns financial and secret reasons;
`effective()` raises command risks for financial plugins; `InstallPolicy.evaluate` checks, in
order: signer continuity → financial with interrupt rules (blocked) → needs trusted signer
without one (blocked) → allowed with warnings and the classification. The coordinator
reclassifies on every reload.

## Technical Context

**Language/Version**: Kotlin 1.9.22 · **Storage**: `financialApps` in settings ·
**Testing**: JUnit 5 (`ClassificationTest`)

## Constitution Check

| Gate | Result |
|------|--------|
| III | Pass — this feature is principle III applied |
| IV | Pass — list edited only in admin mode |
| V | Pass — non-qualifying plugins stop loading |
| VII | Pass — pure JVM |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/plugin/{PluginClassifier.kt, InstallPolicy.kt}
app/src/main/java/com/proj/automation/agent/{AgentStore.kt, AgentCoordinator.kt}
app/src/main/java/com/proj/automation/admin/AdminScreen.kt   # "Financial apps" section
```
