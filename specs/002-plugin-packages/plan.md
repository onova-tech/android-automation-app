# Implementation Plan: `.agp` plugin packages

**Branch**: `feature/engine-foundation` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

A safe in-memory unpacker, a lock file, a manifest and a loader in `:core/plugin` produce an
immutable `Plugin` model; a capability guard wraps the device at run time; the `agp` CLI builds
reproducible packages from source folders.

## Technical Context

**Language/Version**: Kotlin 1.9.22, JVM 17

**Primary Dependencies**: SnakeYAML 2.2; `java.util.zip`; `java.security`

**Storage**: App-private files (`plugins/`, `plugins.json`)

**Testing**: JUnit 5 (`PluginPackageTest`), replay (004)

**Project Type**: JVM library + CLI (`tools/agp`) + Android app

**Constraints**: No code in packages; reproducible builds (sorted entries, fixed timestamps)

## Constitution Check

| Gate | Result |
|------|--------|
| I | Pass — install from file only |
| II | Pass — packages hold data; file types allow-listed |
| III | Pass — risk floor and guard in the base app |
| IV | Pass — install is an admin operation (local only) |
| V | Pass — any doubt at load rejects the whole package |
| VI | N/A |
| VII | Pass — all in `:core`; malicious-zip tests |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/plugin/
├── PackageReader.kt     # safe unpacker, path and type rules
├── PackageZip.kt        # reproducible zip writer
├── PackageLock.kt       # lock format and verification
├── Manifest.kt          # plugin.yaml
├── PluginLoader.kt      # merge, references, risk floor, install summary
├── PackageBuilder.kt    # vendoring + build
└── CapabilityGuard.kt   # runtime blindness and E_CAPABILITY
tools/agp/src/main/kotlin/…/Main.kt
plugins/whatsapp/                    # example plugin
plugins/libraries/android-common/    # first library
```
