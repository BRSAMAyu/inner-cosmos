# INNO-INNER-004 — Provenance experience and retrieval builder gate

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Progressive provenance experience

- Every accessible Starfield V2 list item now opens a plain-language “why this star” panel.
- The panel shows current version, confidence, memory layer, gravity explanation, owner-bound
  operation history and downstream projection receipts without exposing another user's data.
- Audit detail remains progressively disclosed: the default surface stays an emotional starfield;
  database-oriented evidence only appears after the user asks to inspect a star.
- The same flow is operable at a 390x844 viewport with no horizontal overflow. Reduced-motion
  behavior and the non-visual accessible list remain intact.

## Reproducible retrieval gate

- `memory-retrieval-v1.json` is a versioned synthetic annotation set with 12 current/stale/
  contradicted/forgotten memories and 10 task-conditioned cases.
- `MemoryRetrievalEvaluationTest` exercises the actual Spring service and relational filters,
  not a second implementation. It writes a machine-readable report under `target/evaluation/`.
- Current builder result: macro Recall@3 `1.00`, micro Recall@3 `1.00`, MRR `1.00`, prohibited
  leakage `0`, cross-user leakage `0`, token-budget violations `0`, P95 `12.82ms`.
- Published builder thresholds are Recall@3 >= `0.90`, MRR >= `0.85`, prohibited leakage `0`,
  budget violations `0` and local P95 <= `150ms`.

## Verification

- Memory lifecycle/provenance focused test: 10/10 PASS.
- Retrieval evaluation: 1/1 PASS with the metrics above.
- React Vitest: 3/3 PASS; TypeScript/Vite build PASS. Production bundle gzip remains about 70KB.
- Packaged-JAR Playwright: desktop provenance, rollback, Starfield views and 390px mobile journey PASS.

## Honest remaining work

This dataset is a builder-owned synthetic gate, not independent or longitudinal evidence and not
a provider-embedding result. Hard paraphrase, temporal ambiguity and multi-hop relation cases,
OpenAI-compatible embedding/pgvector candidates, real-provider pairwise comparison, production
latency/load measurement and independent blind review remain required. `RETRIEVAL-QUALITY` and
`STARFIELD-V2` therefore remain `IN_PROGRESS`.
