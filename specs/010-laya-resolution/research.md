# Research: Laya resolution

Former ADR-006 (accepted 2026-10-01, reversible by Spikes 1–2) and `docs/vision/laya-resolution.md`.

## What Laya is (secondary sources, 2026-09-30; model card not yet checked)

| Property | Reported value |
|----------|----------------|
| Type | Non-autoregressive **decision** model (encoder); does not generate text |
| Input / output | Text + typed question → Choice, Score or Boolean probabilities |
| Variants | `laya` (ModernBERT-large, 421M, 512 ctx, English); `laya-multilingual` (mmBERT-base, 322M, 1024 ctx, 100+ languages) |
| License / release | Apache 2.0 / 2026-09-18 |
| Android port (third-party) | ONNX Runtime CPU, int8, ~649 MB, ~260 ms per decision on a 2-core runner |
| Zero-shot | ~0.36 accuracy — fine-tuning required (a few thousand examples) |
| Calibration | ECE 0.466 raw; 0.081 after temperature refit |
| Limits | Degrades beyond ~20 options; weak on non-Latin scripts |

Consequences: it reads **text** (the accessibility tree), not pixels; screenshots reach it only
as OCR text (never for Nubank); one Boolean per candidate keeps it far from the 20-option limit;
use the multilingual variant for Portuguese; output is always an option we enumerate.

## Decision: Laya as stage 4, behind a baseline (former ADR-006)

Constraints: tree (or region-filtered OCR) text only; ≤ 8 candidates; multilingual with
fine-tuning and calibration; runs only after cache and hints fail; never authorizes an
irreversible action alone; UI attributes only; heuristic baseline first; training off-device;
one generic question with plugin-supplied intent text (generalization to be measured).

| Alternative | Why not (for now) |
|-------------|-------------------|
| Exact selectors + cache only | The base of the plan; does not cover larger changes |
| Local generative LLM | Open output, injection risk, memory and latency |
| Local vision / UI-grounding model | Needed for icon-only buttons; heavier; separate decision |
| Classic ranker | The baseline; may be enough |
| Cloud decision service (Jev) | Violates self-containment |

## Other uses (by value)

Screen classification (Choice); interruption detection; post-condition verification (Boolean,
with exact comparison at high risk); typo tolerance in commands (only after the grammar fails,
never for risk 4–5); contact disambiguation. **Exact values never go through a model.**

## Data and training

Recorder snapshots (004 T010) → labels (intent, candidate, true/false) → augmentation (pt/en,
dropped descriptions, obfuscated ids, OCR noise) → off-device fine-tuning → int8 ONNX →
per-question-type calibration → golden set per app/version with regression on each change.

## Metrics

Correct-resolution rate (and under drift), false positives on sensitive actions (target 0),
calibration ECE, on-device latency p50/p95, cache hit rate, tree vs. OCR gap.

## Risks

Adversarial on-screen text (UI attributes only, enumerated output, region-filtered OCR); high
confidence but wrong (calibration, margin, post-condition, anchors); third-party port
(baseline, own export); memory and battery (on-demand load); unseen intent wording (keep hints
primary).

## Sources

- [Flowtivity — Laya technical overview](https://flowtivity.ai/blog/laya-open-source-jev-alternative/)
- [DEV Community — Jev vs Laya](https://dev.to/jamilxt/jev-vs-laya-the-same-ai-idea-one-closed-and-one-open-3c6e)
- [laya-android](https://github.com/edwardmonteiro/laya-android)
