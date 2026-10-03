# Implementation Plan: Plugin package signing

**Branch**: `feature/package-signing` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

`PackageSignature` signs and verifies; `PluginLoader` exposes `SignatureStatus` (Unsigned /
Valid(fingerprint)); `InstallPolicy` turns signature, trusted keys and the previous signer into
Allowed(warnings) or Blocked(reason). The app stores the signer per installed plugin and the
trusted keys, and shows the state in the install dialog.

## Technical Context

**Language/Version**: Kotlin 1.9.22 · **Primary Dependencies**: `java.security`, `javax.crypto` ·
**Storage**: `plugins.json` (signer), `trusted_keys.json` · **Testing**: JUnit 5 (`SigningTest`)

## Constitution Check

| Gate | Result |
|------|--------|
| I | Pass — offline verification, no CA |
| III | Pass — trust decisions are the owner's, on the phone |
| IV | Pass — key management is an admin operation |
| V | Pass — malformed or invalid signatures reject; never downgrade to unsigned |
| VII | Pass — all in `:core`; CLI shares the code |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/plugin/{PackageSignature.kt, InstallPolicy.kt}
agp/src/main/kotlin/…/{Main.kt, KeyFiles.kt}             # plugins repository
app/src/main/java/com/proj/automation/agent/{AgentStore.kt, AgentCoordinator.kt}
app/src/main/java/com/proj/automation/admin/AdminScreen.kt   # trusted developers, install dialog
```
