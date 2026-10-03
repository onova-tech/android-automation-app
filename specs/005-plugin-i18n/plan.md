# Implementation Plan: Per-language plugin texts

**Branch**: `feature/i18n` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

`PluginLoader.loadStrings` reads and cross-checks `i18n/*.yaml`; the interpreter exposes the
selected language's map as the reserved `t` scope; the app passes the device locale.

## Technical Context

**Language/Version**: Kotlin 2.4 · **Testing**: JUnit 5 (`I18nTest`), replay

## Constitution Check

| Gate | Result |
|------|--------|
| II | Pass — texts are data |
| V | Pass — inconsistencies rejected at install, never at run time |
| VII | Pass — JVM tests and replays per language |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/plugin/{Manifest.kt (default_language), PluginLoader.kt (strings)}
core/src/main/kotlin/com/proj/automation/dsl/{Ast.kt (Program.strings), Interpreter.kt (t scope)}
core/src/test/resources/whatsapp/i18n/{pt.yaml,en.yaml}   # and the plugins repository
```
