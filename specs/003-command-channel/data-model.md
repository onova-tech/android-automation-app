# Data model: command channel

| Entity | Fields | Notes |
|--------|--------|-------|
| **Envelope** | `channel`, `sender`, `body`, `receivedAt` | Built by the adapter; sender normalized to the allow-listed spelling |
| **ChannelCapabilities** | `maxReplyChars`, `confidential`, `senderAuthenticated`, `plainText`, `maxReplyParts` | `PLAIN_SMS` = 160 chars, not confidential; `LOCAL_UI` = 10 000, confidential |
| **TrustProfile** | `maxRisk` | `PHYSICAL`, `STRONG_REMOTE`, `WEAK_REMOTE`, `UNAUTHENTICATED` |
| **CodeSheet** | `sheetId`, `size` (default 100, max 999), MAC function | Codes are derived, never stored |
| **AuthState** | `used` indices, `consecutiveFailures`, `totalFailures`, `lockouts`, `lockedUntil`, hard lock | 5 consecutive → lock 15 min × 2^n; 20 total → hard lock |
| **PendingConfirmation** | command, confirmation word, expiry, channel | One at a time |
| **Reply** | status, text, pages | Last reply kept for `RESEND` |
| **AuditRecord** | time, channel, sender, command (no code), decision, result, `prevHash`, `hash` | Append-only |
| **Settings** (app) | allowed senders, sheet id, stopped flag, financial apps (007) | Changed only in admin mode |
