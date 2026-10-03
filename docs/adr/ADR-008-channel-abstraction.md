# ADR-008: Channel Abstraction — SMS as the First of Several Contact Channels

| Field | Value |
|-------|-------|
| **ADR** | ADR-008 |
| **Status** | **Accepted** (2026-10-01) |
| **Date** | 2026-10-01 |
| **Context** | Target vision in `docs/vision/` — the owner wants SMS to be one way of reaching the agent, with others added later |
| **Deciders** | Owner |

## Context

The vision documents were written around SMS as the only control channel. The owner clarified that SMS is one of several contact mechanisms. If SMS assumptions (160 characters, spoofable sender, plain text, code sheet) leak into the command language, policy or plugins, every new channel becomes a refactor.

## Decision

1. Introduce a **channel gateway** and **channel adapters** as layer L1. Adapters convert their transport into a **channel-neutral command envelope** and render **structured replies** back.
2. Each adapter declares **capabilities** (reply size, confidentiality, sender authentication, interactivity, network need) and a **trust profile**.
3. The **policy engine** decides with both the command's risk level and the channel's trust profile. Admin operations stay **on-device only** on every channel; `STOP` works on every channel.
4. **Masking happens in the core** based on the channel's confidentiality, before the adapter sees the reply.
5. Adapters are **base-app code**, not YAML plugins (consistent with ADR-007).
6. Channels that need the Internet live in a **separate companion app** connected by local authenticated IPC, so the base app keeps no `INTERNET` permission.
7. v1 ships two adapters: **SMS profile A** and the **on-device admin UI**.

## Alternatives Considered

| Alternative | Why not |
|-------------|---------|
| Keep SMS hard-wired, refactor later | Cheapest now, but SMS assumptions spread into grammar, policy and replies, making the second channel expensive |
| Channels as YAML plugins | Channels are on the security boundary; a data-defined channel could weaken authentication |
| Put network channels in the base app | Breaks the verifiable "no Internet permission" property |

## Consequences

**Positive:** new channels are additive; policy reasons about channel trust explicitly; replies adapt to each channel; the audit shows which channel did what.

**Negative / costs:** a small extra layer in Phase 3; trust profiles must be designed carefully so that a weak channel cannot escalate (for example, by starting a confirmation on one channel and finishing it on another).

See `docs/vision/channels.md`.
