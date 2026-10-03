# Feature Specification: Encrypted SMS (profile B)

**Feature Branch**: `feature/encrypted-sms` (not started)

**Created**: 2026-10-03 (from the SMS security document of 2026-09-30)

**Status**: Draft — depends on Spike 8 (a dumbphone that can send and receive binary SMS from our
program) and owner decision O8

**Input**: Plain SMS lets the carrier read every command and reply. Offer an optional channel
where content is encrypted end to end between a small client on the dumbphone and the agent.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send a confidential command (Priority: P1)

The owner opens the client on the dumbphone, types the PIN that unlocks the client key, and
sends `NU BALANCE`. The agent receives an encrypted binary SMS, decrypts and verifies it, runs
the command and replies encrypted, plus a plain "reply ready" SMS with no content.

**Acceptance Scenarios**:

1. **Given** a frame with a counter ≤ the highest seen, **Then** it is rejected (replay).
2. **Given** a tampered frame, **Then** authentication fails and nothing runs.
3. **Given** the client was not running when the reply arrived, **Then** `RESEND` fetches it
   again within a few minutes.

### User Story 2 - Provision the key in person (Priority: P1)

The key is created in the agent's Keystore and delivered once, physically (personalized build,
~26 base32 characters typed, or Bluetooth/card). It never travels over SMS.

## Requirements *(mandatory)*

- **FR-001**: Frame: `version (1 B) | counter (8 B) | ciphertext | tag (16 B)` ≤ 140 bytes per
  port-addressed data SMS; longer texts split into numbered frames.
- **FR-002**: Authenticated encryption: AES-GCM, or AES-CTR/CBC + HMAC-SHA256 truncated to 16
  bytes, depending on the dumbphone; established libraries only; published test vectors.
- **FR-003**: Highest counter per client stored; resynchronization only in admin mode.
- **FR-004**: Client key wrapped with a PIN on the dumbphone.
- **FR-005**: Adapter declares `STRONG_REMOTE` trust and confidential replies; the code sheet
  remains a second factor for risk 5.
- **FR-006**: Allowlist and rate limits still apply before decryption.

## Success Criteria *(mandatory)*

- **SC-001**: Spike 8 passes on a candidate phone: send, receive (also with the client closed via
  push registration), crypto speed, app loading without a store.
- **SC-002**: Test vectors pass on both the Android side and the client.

## Assumptions

- The carrier still sees sender, recipient, time and size.
- Java ME (MIDP 2.0/CLDC 1.1 with WMA) is the preferred client platform; KaiOS is ruled out
  (SMS permission needs certified apps; 3.x/4.x cannot sideload); a locked-down Android phone is
  the fallback. See [research.md](research.md).
- The protocol and a reference client are built first on a spare Android phone.
