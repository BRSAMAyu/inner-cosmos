# INNO-INNER-006 — Asynchronous embedding rebuild and hard retrieval pairwise

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Runtime behavior

- Interactive retrieval no longer embeds every missing memory. It embeds the query once and reads
  only already-built rows for the configured model/version; lexical and structured retrieval still
  work while the derivative index catches up or the Provider is unavailable.
- A bounded scheduler job builds missing current memory versions outside the request path. The
  existing Redis-backed ShedLock contract prevents duplicate scheduled batches in production and
  the database uniqueness constraint remains the final race guard.
- The missing-work query excludes `LOCAL_ONLY`, `NO_EXTERNAL_PROCESSING`, non-current and foreign
  memory rows before any document content reaches the embedding client.
- Model/version/source-version are part of the completeness query. A model rotation or memory
  version change is therefore rebuilt without mixing old vectors. Failed PostgreSQL vector writes
  remove the incomplete derivative so a later batch can retry it.
- Batch size and cadence are environment-configurable. Default Provider behavior remains disabled;
  explicit enablement still requires an externally injected key.

## Quality and cost/latency probe

`MemoryEmbeddingCandidateIntegrationTest` now includes four deliberately hard contract cases:
Chinese/English paraphrase, current-vs-contradicted temporal preference, and a two-memory relation
question. Against the same relational rows, the deterministic contract Provider achieved Recall@3
`1.00` versus local lexical Recall@3 `0.00`; prohibited stale-memory leakage remained `false`.

The checked snapshot records 18 background document calls for the seeded test batch and five online
query calls for five retrievals. This proves document cost moved off the user request; it also makes
the next optimization visible: the current client sends one document per HTTP request rather than a
Provider-supported batch. Local timings are contract-test timings, not production latency.

## Verification

- `MemoryEmbeddingCandidateIntegrationTest`: 2/2 PASS.
- `MemoryEmbeddingRebuildJobTest`: 1/1 PASS.
- PostgreSQL application smoke: missing-index query, current-model completion and real pgvector
  cosine operator PASS.
- `PostgresFlywayBaselineTest`: 3/3 PASS with ten migrations.
- Reproducible report: `hard-v2-builder-report.json`; the test regenerates the live report at
  `target/evaluation/memory-retrieval-provider-pairwise-report.json`.

## Honest remaining work

This is a deterministic local contract Provider, not an external model quality claim. Pricing is
therefore deliberately `not measured`. A real approved Provider run, batched embedding requests,
larger independently labelled longitudinal data, calibrated thresholds, production load/latency and
independent review remain required. `RETRIEVAL-QUALITY` stays `IN_PROGRESS`.
