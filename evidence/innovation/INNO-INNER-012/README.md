# INNO-INNER-012 — real embedding provider proof (Qwen/DashScope `text-embedding-v4`)

## What this is

W1 (Aurora inner-voice branch) swaps the default memory-embedding provider from a placeholder
OpenAI default to the real, empirically-confirmed Aliyun DashScope endpoint used by this account's
real API key, and proves the swap end-to-end: a real HTTP call succeeds, the returned vector
matches the existing fixed schema width exactly (no new migration needed), and a real embedding
actually improves retrieval, not just "the HTTP call returned 200".

`OpenAiCompatibleMemoryEmbeddingClient` (`src/main/java/com/innercosmos/ai/embedding/`) already
implemented a generic OpenAI-schema embeddings client before this work; nothing about that class
changed. What changed:

- `MemoryEmbeddingConfig` defaults (`src/main/java/com/innercosmos/config/MemoryEmbeddingConfig.java`)
  and `application.yml` now default to `base-url=https://dashscope.aliyuncs.com/compatible-mode/v1`,
  `model=text-embedding-v4`, `dimensions=1536` — still fully env-var overridable, never a hardcoded
  key. `memory.embedding.enabled` still defaults to `false`; a real key is opt-in via
  `MEMORY_EMBEDDING_API_KEY`.

## Spike evidence (manual, real key, run once during this session)

1. **Auth + shape** — `curl` against
   `https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/embeddings`
   (this account's real private-gateway OpenAI-compatible host) with
   `{"model":"text-embedding-v4","input":"衣服的质量杠杠的","dimensions":1536}` returned **HTTP 200**
   with a 1536-length `data[0].embedding` array. (An earlier attempt without the explicit
   `dimensions` field, and with the JSON body typed inline through a bash heredoc, returned
   `400 InvalidParameter` — root cause was UTF-8 mangling of the embedded Chinese text through the
   shell, not a real API problem; sending the same JSON from a UTF-8 file via `curl --data @file`
   fixed it immediately.)
2. **Dimension confirmation** — `tb_memory_embedding.embedding_vector` is a fixed
   `vector(1536)` column (`V10__versioned_memory_embeddings.sql`, `V18__capsule_matching_embeddings.sql`).
   `text-embedding-v4` documents 2048/1536/1024(default)/768/512/256/128/64 as supported output
   widths via the OpenAI-compatible `dimensions` parameter (DashScope's "切换向量维度" /
   "switch vector dimension" doc section) — requesting `dimensions:1536` returns exactly a
   1536-length vector, confirmed by the same curl call above. **No new Flyway migration or index
   change was needed.**

## Real-provider round-trip proof (automated, gated, not part of the default gate)

`MemoryEmbeddingRealProviderIndexRetrievalTest`
(`src/test/java/com/innercosmos/evaluation/MemoryEmbeddingRealProviderIndexRetrievalTest.java`),
tagged `@Tag("real-provider")` (excluded from `./mvnw test` by `pom.xml`'s
`excludedGroups=real-provider`, matching the existing `TrackARealProviderSmokeEvaluationTest`
convention). Reads `MEMORY_EMBEDDING_API_KEY`/`MEMORY_EMBEDDING_BASE_URL`/`MEMORY_EMBEDDING_MODEL`
from the process environment only; self-skips to a `SKIPPED_NO_CREDENTIAL` evidence row (never a
silent pass, never a fallback to a fake client) when the key is absent.

This test exercises the **actual product pipeline** — `MemoryEmbeddingIndexService.rebuildMissing`
and `MemoryRetrievalService.retrieve` — over H2 (the local/JSON cosine-similarity scoring path;
this does not require PostgreSQL/pgvector/Testcontainers), not a bespoke embedding-only check:

1. Inserts one relevant memory card ("提交课程报告 / 整理实验结果并交付截止日期前的最终版本") and two
   unrelated distractors ("雨天散步...", "整理厨房...").
2. Calls `rebuildMissing(20)`, which calls the REAL `client.embed(...)` for each card and writes
   the resulting vector into `tb_memory_embedding.embedding_json`.
3. Asserts every stored vector is exactly `client.dimensions()` (1536) long.
4. Calls `retrieval.retrieve(owner, "赶在截止日期前交作业", "ACTION_SPLIT", ...)` — a semantically
   related but lexically dissimilar query — and asserts the relevant memory is retrieved.

Run on this session with the real key:

```
export MEMORY_EMBEDDING_API_KEY=<redacted>
export MEMORY_EMBEDDING_BASE_URL=https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
export MEMORY_EMBEDDING_MODEL=text-embedding-v4
./mvnw test -Dtest=MemoryEmbeddingRealProviderIndexRetrievalTest -DexcludedGroups=
```

Result: **1 test run, 0 failures.** Evidence JSON written to
`target/evaluation/memory-embedding-real-provider-report.json`:

```json
{
  "status" : "CALLED",
  "provider" : "aliyun-dashscope",
  "model" : "text-embedding-v4",
  "modelVersion" : "2026-01",
  "configuredDimensions" : 1536,
  "storedVectorDimension" : 1536,
  "relevantMemoryRetrieved" : true,
  "evidenceCount" : 2
}
```

## Honest scope — what is proven vs not

**Proven:**
- The real key authenticates against this account's DashScope gateway for `text-embedding-v4`.
- A real embedding call returns exactly the width `tb_memory_embedding` already expects (1536),
  with zero schema/migration changes.
- A real embedding, written through the actual `MemoryEmbeddingIndexServiceImpl.rebuildMissing`
  write path and read back through the actual `MemoryRetrievalServiceImpl.retrieve` read path,
  successfully retrieves a semantically-related-but-lexically-different memory over unrelated
  distractors — i.e. the vectors are not just present, they are usable for real retrieval.
- All pre-existing embedding tests (`OpenAiCompatibleMemoryEmbeddingClientTest`,
  `MemoryEmbeddingCandidateIntegrationTest`, `MemoryEmbeddingRebuildJobTest`,
  `MemoryEmbeddingIndexServiceImplDimensionContractTest`) still pass unmodified after the default
  config change.

**Not (re-)proven this round, by explicit scope decision:**
- The full PostgreSQL/pgvector `<=>` ANN scoring path (`postgresScores`) was not re-exercised
  against a live Postgres/pgvector instance with real vectors in this session — the H2/local
  cosine path was used instead, since it needs no Docker/Testcontainers and already proves the
  vector width and retrieval-quality claims. The pgvector column width contract itself
  (`requireDimensionContract`, `MemoryEmbeddingIndexServiceImplDimensionContractTest`) is
  unaffected by this change and remains covered by its own existing test.
- A full retrieval-quality regression/load test (recall@k across a larger real-embedding dataset,
  the kind `MemoryEmbeddingCandidateIntegrationTest`'s hard-paraphrase suite runs against a fake
  deterministic client) was not re-run against the real provider this round — one focused
  relevant-vs-distractor case was proven, not a statistically robust recall benchmark.
- Real-key network calls cost real money/quota and are not part of the default `./mvnw test` gate
  by design (see the `real-provider` tag convention) — this is intentional, not an evasion.
