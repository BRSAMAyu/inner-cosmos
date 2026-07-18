# INNO-INNER-009 — real GLM embeddings + pgvector semantic retrieval on live EKS

> Date: 2026-07-17 · Ledger: G5 `RETRIEVAL-QUALITY`, G6 `MATCH-MULTI`, G8 `LOCAL-COMPLETE`.
> Cluster: EKS MyEKS (prod stack, `deploy/k8s/overlays/eks-prod`). Complements the offline logic proof
> INNO-INNER-008 with a real embedding provider on the pgvector path.

## What was enabled
The prod `inner-cosmos-runtime` Secret was patched (out-of-band, key never committed) with:
```
MEMORY_EMBEDDING_ENABLED=true
MEMORY_EMBEDDING_BASE_URL=https://open.bigmodel.cn/api/paas/v4   # GLM (Zhipu), OpenAI-/embeddings-compatible
MEMORY_EMBEDDING_MODEL=embedding-3
MEMORY_EMBEDDING_DIMENSIONS=1536                                  # matches tb_memory_embedding vector(1536)
MEMORY_EMBEDDING_API_KEY=<glm key, env/secret only>
```
API + scheduler restarted. `MemoryEmbeddingConfig` then builds the real `OpenAiCompatibleMemoryEmbeddingClient`
(not the `DisabledMemoryEmbeddingClient` stub).

## Verified live on prod
- **App boots clean** with embeddings enabled (no startup error, prod guard still passes).
- **Rebuild job ran and used GLM** (scheduler log): `Memory embedding rebuild selected=2, indexed=2,
  failed=0, remaining=0` — 2 memory cards embedded via GLM `embedding-3`, zero failures (GLM egress from the
  pod works).
- **Real vectors stored in pgvector**: `tb_memory_embedding.embedding_vector` holds real 1536-dim vectors
  (e.g. `[-0.024311522,0.052931886,-0.06853286,…]`).
- **pgvector cosine retrieval works** (the exact `MemoryRetrievalService` mechanism, `<=>` operator):
  ```
  id | sim
   1 | 1.0000   (self)
   2 | 0.9556   (the two same-person memories are semantically close)
  ```
  So on Postgres the retrieval takes the `postgresScores()` pgvector-cosine path over real GLM embeddings;
  the deterministic lexical path remains as fallback (consent-scoped memories are never embedded/sent).

## Result
Semantic memory retrieval is no longer lexical-only or a fake embedder — it runs on a **real embedding
provider (GLM embedding-3) with pgvector** on the live prod deployment. Combined with INNO-INNER-008
(semantic beats lexical by +100pts recall on hard cases), the retrieval-quality path is proven both in
logic and with a real provider.

## Remaining
- A blind human-rated retrieval-quality eval on real user data (human gate).
- Reproducibility: the enablement is via the runtime Secret (key out-of-band); the non-secret vars can be
  promoted into the overlay when an embedding key is a standing input.
