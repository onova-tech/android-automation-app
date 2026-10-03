# ADR-006: Laya as a Local Decision Layer for Element Resolution

| Field | Value |
|-------|-------|
| **ADR** | ADR-006 |
| **Status** | **Accepted** (2026-10-01). Becomes Rejected if Spike 1 or 2 fails; see Validation |
| **Date** | 2026-09-30 |
| **Context** | Target vision in `docs/vision/` — resilient element resolution, 100% local |
| **Deciders** | Owner |

## Context

The POC resolves elements with exact selectors (`resource_id`, `text`, `content_description`, `class_name`). They break when an app changes ids, wording or language. The goal is to keep working with no external service, while validating that the element found is the one intended.

Laya (Convai Innovations, Apache 2.0, released 2026-09-18) is a non-autoregressive **decision** model: it takes **text** and a typed question (Choice, Score, Boolean) and returns probabilities. The multilingual variant is mmBERT-base, 322M parameters, 1024-token context. A third-party Android port exists (ONNX Runtime, int8, ~649 MB). **It is not a vision model.** Screenshots can reach it only as OCR text.

The baseline device is Android 13 with 6 GB of RAM, running 24x7.

## Decision

Adopt Laya, **if the spikes confirm it**, as stage 4 of a `ResolverPipeline` **on the device**, with these constraints:

1. It works on **text derived from the accessibility tree** (or from OCR when there is no usable tree), with one Boolean question per candidate in a batch and at most ~8 candidates pre-filtered by a heuristic ranker.
2. It uses the **multilingual** variant (Portuguese), with mandatory **fine-tuning and calibration** on real screens.
3. It runs only after the fingerprint cache and exact hints fail.
4. It **never authorizes an irreversible action alone.** Financial actions require a deterministic anchor and exact comparison of amount and recipient.
5. The document sent to the model contains **UI attributes only**, never the body of the user's messages; OCR input is region-filtered.
6. A **heuristic baseline** is built first; Laya stays only if it beats the baseline on the golden set.
7. Training happens **off-device**; the device receives signed artifacts. No external service at runtime.
8. It answers one generic question ("does this element match this description?"), with the description supplied by plugin `intent` text, so plugins can add intents without retraining. Generalization is to be measured.

## Alternatives Considered

| Alternative | Why not (for now) |
|-------------|-------------------|
| Exact selectors + cache only | The base of the plan, but does not cover larger text/id changes |
| Local generative language model | Open output (risk of unintended actions, injection); much higher memory/latency cost |
| Local vision / UI-grounding model | Needed for icon-only buttons but heavier; **a separate decision** |
| Classic ranker (features + decision trees) | Becomes the **baseline**; may be enough |
| Cloud decision service (e.g. Jev) | Violates the self-contained requirement |

## Consequences

**Positive:** output limited to enumerated options (predictable, less prone to injection); calibratable probabilities that feed risk thresholds; runs on CPU; 6 GB of RAM leaves room when the model is loaded on demand.

**Negative / costs:** ~650 MB of storage and significant memory; weak zero-shot (~0.36 reported), so a dataset and a training pipeline are required; ~20-option limit; does not cover icon-only elements even with OCR; very new project with a third-party port (maintenance risk).

**Residual risks and mitigation:** see `docs/vision/laya-resolution.md` section 8.

## Validation

- **Spike 1:** performance on the target handset.
- **Spike 2:** gain over the baseline on the golden set, generalization to unseen intents, tree vs. OCR.
- If either spike fails, this ADR becomes **Rejected** and the `ResolverPipeline` proceeds without stage 4.

## Sources

- [Flowtivity — Laya technical overview](https://flowtivity.ai/blog/laya-open-source-jev-alternative/)
- [DEV Community — Jev vs Laya](https://dev.to/jamilxt/jev-vs-laya-the-same-ai-idea-one-closed-and-one-open-3c6e)
- [laya-android](https://github.com/edwardmonteiro/laya-android)
