# Tasks: Command channel

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/command-grammar.md](contracts/command-grammar.md)

## Phase 1: Core (JVM)

- [X] T001 `Envelope`, `ChannelCapabilities`, `TrustProfile` in `core/.../channel/Envelope.kt`
- [X] T002 Grammar and `ArgsTemplate` in `core/.../channel/CommandParser.kt`
- [X] T003 `CodeSheet`, `CodeVerifier` (burn on use, lockouts, budget) in `core/.../security/CodeSheet.kt`
- [X] T004 `Policy` risk × trust with two-step confirmation in `core/.../channel/Policy.kt`
- [X] T005 Paged plain-text replies, `MORE`/`RESEND` navigation in `core/.../channel/Replies.kt`
- [X] T006 Hash-chained `AuditLog` in `core/.../security/AuditLog.kt`
- [X] T007 `CommandRouter` with allowlist, `HELP`/`STATUS`/`STOP`/`CANCEL`/`OK`, `PhoneNumbers` matching and a low-codes notice (< 15) in replies
- [X] T008 Tests in `ChannelTest` (incl. fixes: navigation does not overwrite last reply; reused codes dropped silently; whitespace separators need ≥ 1 space)

## Phase 2: App (written, not run on a phone)

- [X] T009 `SmsReceiver` (multipart join) and `SmsSender` in `app/.../channel/sms/`
- [X] T010 Keystore-backed HMAC key in `app/.../security/KeystoreKeys.kt`
- [X] T011 `AgentStore` and single-job `AgentCoordinator` in `app/.../agent/`
- [X] T012 Admin screen: status, plugins, allowed senders, code sheet shown once, console, audit

## Phase 3: Device validation and gaps

- [ ] T013 Install on the agent phone; "Allow restricted settings"; SMS permission; end-to-end WhatsApp command (SC-005)
- [ ] T014 Spike 3: SMS reliability ≥ 72 h under Doze and One UI battery limits
- [ ] T015 Per-channel rate limits and a daily reply cap
- [ ] T016 Command expiry window (reject commands whose timestamp is too old)
- [ ] T017 Grammar and SMS-parser fuzzing
