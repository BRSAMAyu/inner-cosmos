# INNO-INNER-005 — Versioned provider embeddings and pgvector candidate path

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Architecture and privacy contract

- Memory embeddings are optional, rebuildable derivatives. PostgreSQL memory/version rows remain
  authoritative and the lexical/structured retrieval path remains available when the provider fails.
- Explicit enablement without a credential fails application startup. The default is disabled and
  performs no network call. Provider failures never log raw memory text or credentials.
- `LOCAL_ONLY` and `NO_EXTERNAL_PROCESSING` cards never cause query or document embedding calls.
  Owner, current-status, contradiction and layer filters run before provider candidate generation.
- Every row records model name, model version, source memory version, task scope and dimensions.
  Retrieval joins the current memory version and never mixes vectors from different models/versions.
- UPDATE/merge/split/rollback and related lifecycle changes mark old vectors `STALE`; `FORGET`
  physically deletes all vectors for that memory in the same transaction.

## Real execution path

- The client implements the OpenAI-compatible `POST /embeddings` contract with bearer auth,
  requested model/dimensions and strict response-dimension validation.
- V10 adds `tb_memory_embedding`; PostgreSQL stores a fixed 1536-dimensional pgvector alongside
  portable JSON and executes cosine ranking through the real `<=>` operator.
- Provider similarity is an explainable score contribution and widens semantic candidates; it does
  not bypass the existing privacy, contradiction, diversity or token-budget gates.

## Verification

- Provider HTTP contract and fail-closed configuration: 2/2 PASS.
- Candidate integration: hard English/Chinese paraphrase ranks through a fake contract provider,
  while foreign, contradicted and `LOCAL_ONLY` memories are not embedded or returned.
- Lifecycle focused gate proves FORGET removes embedding derivatives.
- PostgreSQL 16 + pgvector 0.8.1: 10 Flyway migrations, 73 tables, 71 identities, exact source
  table/index/foreign-key baseline, application smoke and real vector `<=>` equality query PASS.
- Full Java 21 / Spring Boot 3.5 regression: 753/753 PASS across 114 suites.

## Honest remaining work

The HTTP contract provider is local and deterministic; no external credential was available or
required for this checkpoint. A real approved provider run, hard-paraphrase/multi-hop dataset,
provider-vs-local pairwise quality/cost/latency comparison, async bulk rebuild and independent review
remain required. `RETRIEVAL-QUALITY` stays `IN_PROGRESS`.
