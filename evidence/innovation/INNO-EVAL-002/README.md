# INNO-EVAL-002 — real Provider interrupt pairwise

The executable harness is implemented, unit-tested and executed against GLM-5.2 and MiMo v2.5. On 2026-07-15 it was strengthened from four
prompt-only interrupt examples to eleven contract-aligned observations spanning interruption,
replanning, continuity, relationship repair, memory correction, temporal rescheduling, Aurora Self
boundaries and proactive fatigue, plus a cumulative three-day report-anxiety trajectory. Every output now includes deterministic constraint scores, blind
human-review columns, scenario/prompt contract hashes, latency/token metadata and an explicit
no-fallback assertion. Both 11-observation real-Provider runs completed with 33 calls each,
`provider_called=true` and `fallback_used=false`. The truthful status is now
`AWAITING_HUMAN_PAIRWISE`; no model-quality winner is claimed.

The harness runs eleven synthetic Living Aurora observations against one configured model,
holding model and sampling settings constant while comparing single-pass with the real
planner→speaker runtime. It emits `real-provider-runs.json` plus a source-blind review CSV.
It never stores the key or raw endpoint and cannot claim a winner before human
pairwise review is completed.

`real-provider-quality-analysis-2026-07-15.md` records aggregate latency/token cost, deterministic
constraint failures, the GLM dependency-language failure and the v3.1 boundary repair. The GLM
post-hoc rescore is explicitly calibration-only; MiMo v2.5 is the fresh v3.1 run. MiMo v2.5-pro was
not used.

The v3 review contract scores left and right independently on felt understanding, interruption
acceptance, continuity/boundary, proactive appropriateness and Self continuity. Two independent
reviewers must each cover every pair. After ratings are frozen, unblind with:

```powershell
python -m evals.cli.main real-pairwise-score `
  --ratings reviewer-01.csv reviewer-02.csv `
  --runs ../evidence/innovation/INNO-EVAL-002/real-provider-runs.json `
  --output ../evidence/innovation/INNO-EVAL-002/pairwise-score.json
```

Missing reviewers, pairs, scores, response mismatches, invalid preferences, longitudinal days or
quality thresholds fail closed. This makes the human gate executable; it does not satisfy it.

The production Prompt P1 was also closed at this checkpoint: Aurora identity, safety and
anti-injection boundaries now travel through `LlmRequest.systemPrompt` and every real Provider sends
them as `role=system`; dynamic profile, memory and user text remain in the user/context payload.
Focused Java tests passed `76/76`; full Java regression passed `777/777`; React passed `3/3` plus production build; Playwright passed `9/9`; AI Lab passed `39/39`; the full offline innovation evaluation
executed `192` runs across `48` scenarios and `80` metrics with all hard gates passing. This does not
claim real-model superiority.
