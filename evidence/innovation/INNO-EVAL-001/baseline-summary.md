# INNO-EVAL-001 baseline summary

## Result

The isolated evaluation harness is implemented. Its first reproducible comparison executes four systems across 48 synthetic scenarios (192 runs), seven scenario families, four protected splits and 20 deterministic metrics per system (80 results), plus schema validation, blind human-review exchange, optional Judge metadata, and current-production source contracts.

This result does **not** establish model quality or system superiority. All sample responses are deterministic contract fixtures with zero Provider calls.

## Current-production scope

The contract adapter checks that the current tree still exposes:

- `PromptBuilder` segments and the Aurora multi-message policy;
- `multiMessageAllowed` context control;
- proactive push/wait/schedule decisions and `PrivateTimer`;
- Aurora Constitution, Reflection, Self Model and user-triggered emergence path;
- the current User Portrait entity;
- the current CapsuleAgent path.

Baseline capture types are deliberately distinct:

| Capture | Status | Meaning |
|---|---|---|
| current-production contract fixture | RUN | Source contracts plus deterministic synthetic events; no Spring runtime or LLM |
| current-production Mock runtime | REGISTERED_NOT_RUN | No runtime capture was added to production in this package |
| historical fixture | REGISTERED_NOT_RUN | No approved redacted historical fixture was available |
| real Provider | BLOCKED_BY_CREDENTIAL_GATE | External credential revocation/rotation is not signed |

The single-prompt, long-persona-prompt and structured-context offline baselines are runnable as synthetic structural comparators. Structured Genome, Planner/Speaker, Critics and full Compiler systems are registered as `UNAVAILABLE`; no results are fabricated.

## Sample result interpretation

All hard gates pass for the synthetic contract fixture: no stale bubble, unauthorized memory reference, privacy leakage, held-out leakage or duplicate wake; evidence traceability is 1.0. These values prove deterministic instrumentation behavior only. They must not be reported as production model-quality measurements.
