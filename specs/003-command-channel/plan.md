# Implementation Plan: Command channel

**Branch**: `feature/command-channel` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

A channel-neutral `CommandRouter` in `:core/channel` (allowlist → grammar → code → policy →
plugin runner → paged reply → audit), with the code sheet and audit in `:core/security`. The
app adds the SMS adapter, a Keystore-backed HMAC key, local storage, a single-job coordinator
and the Compose admin screen.

## Technical Context

**Language/Version**: Kotlin 2.4, JVM 17

**Primary Dependencies**: `javax.crypto` (HMAC-SHA256), Android Keystore, `SmsManager`, Compose

**Storage**: App-private JSON files (settings, auth state, plugins, trusted keys) and the audit
log

**Testing**: JUnit 5 (`ChannelTest`); device tests open

**Constraints**: No `INTERNET`; SMS replies ≤ 160 GSM-7 chars per part; one job at a time

## Constitution Check

| Gate | Result |
|------|--------|
| I | Pass — SMS only; no network |
| II | Pass — adapters are base-app code |
| III | Pass — policy and settings are local, admin-only |
| IV | Pass — allowlist + one-time codes + risk × trust + audit |
| V | Pass — no detail on failures; unknown outcomes reported, not retried |
| VI | N/A |
| VII | Pass — router, policy, codes and audit are pure JVM |

## Project Structure

```text
core/src/main/kotlin/com/proj/automation/
├── channel/   # Envelope, CommandParser (ArgsTemplate), Policy, Replies, CommandRouter, PhoneNumbers
└── security/  # CodeSheet, CodeVerifier, AuthState, AuditLog
app/src/main/java/com/proj/automation/
├── channel/sms/  # SmsReceiver, SmsSender
├── security/     # KeystoreKeys
├── agent/        # AgentStore, AgentCoordinator
└── admin/        # AdminScreen
```
