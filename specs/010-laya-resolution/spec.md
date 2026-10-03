# Feature Specification: Self-healing element resolution with Laya

**Feature Branch**: `feature/laya-resolution` (not started)

**Created**: 2026-10-03 (from ADR-006 and the Laya resolution document of 2026-09-30)

**Status**: Draft — enters only if Spikes 1 and 2 pass

**Input**: Keep plugins working when apps change ids, wording or language, without editing the
plugin and without any external service, while never clicking an element the system is unsure
about.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A renamed button is still found (Priority: P1)

After an app update renames the send button's id and label, the resolver still picks it: cache
miss → hints fail → ranker shortlists ≤ 8 candidates → Laya answers "does this element match
'the button that sends the message'?" per candidate → the top candidate with enough confidence
and margin is clicked → the post-condition confirms → the cache learns the new fingerprint.

**Independent Test**: Golden set of real screens per app/version; compare against the heuristic
ranker (001).

### User Story 2 - Doubt means stop (Priority: P1)

Medium confidence: try the next candidate for reads, abort and report for sends. Financial
actions: Laya never decides alone (deterministic anchor required).

### User Story 3 - Know which screen we are on (Priority: P2)

A screen classifier (Choice over ≤ 20 known screens) feeds `wait_for_screen` and interruption
detection.

## Requirements *(mandatory)*

- **FR-001**: Pipeline: fingerprint cache → exact hints → candidates + heuristic ranking (top
  K ≤ 8) → Laya Boolean per candidate (batched) → decision by calibrated thresholds and margin →
  act + verify → update cache only after verification.
- **FR-002**: The document sent to the model contains UI attributes only (role, description, id
  tail, region, neighbors) — never message bodies.
- **FR-003**: Thresholds by risk; financial actions require a deterministic anchor.
- **FR-004**: Model files are signed, installed by hand, loaded on demand and unloaded when idle.
- **FR-005**: Every resolver decision (stage, confidence) is traced locally.
- **FR-006**: The ranker's `rerank` hook (already in `TargetResolver`) is the integration point.

## Success Criteria *(mandatory)*

- **SC-001**: Spike 1: < 1.5 s per resolution on the 6 GB baseline phone, acceptable RAM and
  battery.
- **SC-002**: Spike 2: measurable gain in correct-resolution rate over the ranker, including on
  other app versions and languages.
- **SC-003**: Zero wrong clicks at high risk on the golden set.

## Assumptions

- If either spike fails, this spec is dropped and the ranker stays (constitution VI).
- Icon-only elements are out of reach (OCR does not read drawings); a UI-grounding model is a
  separate decision (O7).
