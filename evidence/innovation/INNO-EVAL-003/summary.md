# INNO-EVAL-003 — real-provider dual-kernel vs single-pass pairwise (DeepSeek)

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Ledger: G4 `AURORA-DUAL-KERNEL`, `AURORA-QUALITY`.
> Harness: `ai-lab` `real-pairwise` (`evals/real_provider_pairwise.py`). Provider key injected via
> `~/.config/inner-cosmos/providers.local.json` profile `deepseek` (base_url api.deepseek.com, model
> deepseek-chat) — key never stored in the artifact (only `endpoint_origin_hash` = sha256(base_url)).

## What ran
`python -m evals.cli.main real-pairwise --profile deepseek --seed 7` over **11 synthetic trajectories**
(interrupt / replan / continuity / repair / memory / temporal / self / proactive + a 3-day longitudinal
trio). For each: System **A** = single-pass baseline (1 LLM call); System **B** = **dual-kernel**
(Planner emits a compact JSON plan → Speaker composes from it, 2 LLM calls). Same model both sides.

## Result (honest)
- `provider_called: true`, `fallback_used: false` — **the dual-kernel runtime executed end-to-end against a
  real LLM** (22 real LLM calls across 11 records; no Mock). This is the first real-provider exercise of the
  planner→speaker path.
- Deterministic lexical rubric (`lexical-constraints.v3.1`, keyword presence only):
  **A single-pass = 7/11 (0.64), B dual-kernel = 6/11 (0.55)**. On this crude keyword check, dual-kernel does
  **NOT** beat single-pass. This matches a prior observation and is recorded honestly, not hidden.
- Status: `AWAITING_HUMAN_PAIRWISE`. The real quality verdict (does the planner→speaker reply read as more
  attuned, boundary-respecting, continuous?) is **not** decidable by keyword matching — it requires the blind
  human pairwise scoring in `blind-human-pairwise.csv` (≥2 independent reviewers; `real-pairwise-score`
  enforces `dual_kernel_preference_rate >= 0.60` + no per-dimension regression). That human step is the
  standing gate `REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW`.

## Qualitative note (illustrative, not a verdict)
On the memory/continuity trajectory the dual-kernel B response first emitted a structured planner JSON
(user_intent / emotional_need / relationship_move / constraints / uncertainty) and then a speaker reply that
explicitly honored the user's "接住 before 拆解" preference, declined to pre-remind, and offered a
"撤回/换一种" retract path — behaviors the single-pass A reply covered less completely. Whether that
translates to a human-preferred answer is exactly what the blind scoring measures.

## Files
- `real-provider-runs.json` — full blinded A/B records, planner outputs, token/latency meta, contract hashes.
- `blind-human-pairwise.csv` — order-shuffled (seeded) rows ready for ≥2 human reviewers across 5 dimensions.

## Remaining (gate)
Blind human pairwise scoring (human gate). A fresh-context non-author agent review MAY be run as an interim
automated signal per the working norms, but does not substitute for the human blind panel.
