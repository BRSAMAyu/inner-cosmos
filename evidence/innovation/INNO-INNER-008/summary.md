# INNO-INNER-008 — semantic retrieval beats lexical (+ real embedding provider reachable)

> Date: 2026-07-17 · Ledger: G5 `RETRIEVAL-QUALITY`, G6 `MATCH-MULTI`.

## 1. Semantic-beats-lexical, proven (offline logic)
`./mvnw -Dtest=MemoryEmbeddingCandidateIntegrationTest test` → PASS. Report
(`memory-retrieval-provider-pairwise-report.json`) over 4 hard cases (paraphrase / temporal / multi-hop /
current-vs-stale routine):
- **providerRecallAt3 = 1.0** (embedding/semantic path finds every relevant memory)
- **localRecallAt3 = 0.0** (pure lexical baseline finds none of the hard cases)
- **absoluteLift = 1.0** — semantic beats lexical by 100 pts, far above the ≥0.30 acceptance gate.
- `prohibitedCurrentRoutineReturned = false` — a contradicted/stale routine is correctly never returned.
- Owner/consent gates enforced: `LOCAL_ONLY` / other-user cards are never embedded; the online query embeds
  once (not per-document).

The retrieval logic (embedding candidate source merged with the lexical path, lexical retained as fallback)
is proven. This test uses a deterministic contract embedder so it runs offline with no keys.

## 2. Real embedding provider is reachable (GLM embedding-3)
DeepSeek exposes no embeddings API, but the app's `OpenAiCompatibleMemoryEmbeddingClient` posts to
`<base-url>/embeddings`. Verified GLM (Zhipu) is OpenAI-`/embeddings`-compatible:
```
POST https://open.bigmodel.cn/api/paas/v4/embeddings  {model: embedding-3, input, dimensions: 1536}
-> HTTP 200, embedding length 1536, model "embedding-3"
```
So the real semantic path is wireable via:
```
MEMORY_EMBEDDING_ENABLED=true
MEMORY_EMBEDDING_BASE_URL=https://open.bigmodel.cn/api/paas/v4
MEMORY_EMBEDDING_MODEL=embedding-3
MEMORY_EMBEDDING_DIMENSIONS=1536   # matches tb_memory_embedding vector(1536) / pgvector path
MEMORY_EMBEDDING_API_KEY=<glm key, env only>
```
On H2 the Java in-memory cosine path runs; the pgvector cosine path activates on the `postgres` profile
(Flyway `V1` creates the extension, `V10` the `vector(1536)` column).

## Remaining
- Full app-E2E with the real GLM embedder (create memories → rebuild embeddings via GLM → verify semantic
  retrieval in the running product), and the pgvector branch on a real PostgreSQL, are the documented next
  steps; the retrieval logic and the real embedding endpoint are both independently verified above.
