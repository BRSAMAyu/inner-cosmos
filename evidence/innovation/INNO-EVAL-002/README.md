# INNO-EVAL-002 — real Provider interrupt pairwise

The executable harness is implemented and unit-tested. On 2026-07-15 it was strengthened from four
prompt-only interrupt examples to eight contract-aligned trajectories spanning interruption,
replanning, continuity, relationship repair, memory correction, temporal rescheduling, Aurora Self
boundaries and proactive fatigue. Every output now includes deterministic constraint scores, blind
human-review columns, scenario/prompt contract hashes, latency/token metadata and an explicit
no-fallback assertion. The live run is truthfully
`BLOCKED_BY_CREDENTIAL_GATE`: no approved real Provider endpoint/key/model pair is
present in this process, so no model was called and no Mock result was substituted.

When the three ephemeral environment values in `gate-status.json` are supplied, the
harness runs eight synthetic Living Aurora trajectories against one configured model,
holding model and sampling settings constant while comparing single-pass with the real
planner→speaker runtime. It emits `real-provider-runs.json` plus a source-blind review CSV.
It never stores the key or raw endpoint and it cannot claim a winner before human
pairwise review is completed.

The production Prompt P1 was also closed at this checkpoint: Aurora identity, safety and
anti-injection boundaries now travel through `LlmRequest.systemPrompt` and every real Provider sends
them as `role=system`; dynamic profile, memory and user text remain in the user/context payload.
Focused Java tests passed `76/76`; full Java regression passed `777/777`; React passed `3/3` plus production build; Playwright passed `9/9`; AI Lab passed `39/39`; the full offline innovation evaluation
executed `192` runs across `48` scenarios and `80` metrics with all hard gates passing. This does not
claim real-model superiority.
