# Research: Plugin classification

Former ADR-009 §9 (amendment, 2026-10-01).

## Decision: Two separate rules, decided by the base app

- **Rule 1 — financial apps list.** Financial if declared **or** operating/reading a listed app.
  A plugin caught by the list gets risk 5, because its read-only claim cannot be trusted.
- **Rule 2 — secrets.** Secrets or device PIN ⇒ trusted signer, nothing else changes.
- **Rationale**: The category is a plugin claim; what the plugin operates is enforced by the
  capability guard, so it is a fact. Separating the rules avoids treating every
  password-holding plugin as a bank (which would, for instance, ban its interrupt rules).

## Alternatives considered

| Alternative | Why not |
|-------------|---------|
| Trust the declared category | An author could lie to bypass the financial rules |
| Detect finance apps from Android attributes | `ApplicationInfo.category` has no finance value; store categories need the Internet |
| Heuristics on screen texts (Pix, saldo…) | Unreliable and bypassable; better as defense in depth inside financial skills (009) |
| Make every secret-holding plugin financial | Over-restrictive; signer requirement is the part that matters |
