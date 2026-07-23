# INNO-INNER-010 — memory retrieval: real latency/budget measurement under concurrent load

> Date: 2026-07-23 · Branch: `codex/w1-product-3` (worktree off `codex/w0-integration` @ `9145d29`).
> Ledger: G5 `RETRIEVAL-QUALITY` — closes the specific, still-open sub-clause "latency/budget
> thresholds under load" (the blind human-rated quality and contradiction-calibration sub-clauses
> remain open and are NOT addressed here — see Remaining).

## Why this, not the whole gate
`RETRIEVAL-QUALITY`'s `remaining:` note named three separate open items: blind human-rated quality,
contradiction calibration, and latency/budget thresholds under load. The first two need a human
reviewer (or a real-provider relevance panel) and are not machine-executable today. The third is:
the existing `MemoryRetrievalEvaluationTest` (`src/test/java/com/innercosmos/evaluation/`) already
measures p95 latency and budget adherence, but does so **sequentially** (one `retrieve()` call at a
time, awaited before the next starts) against a **12-row** corpus. That proves correctness, not
behavior "under load" — it never puts multiple callers on the wire at once, so it can't show
connection-pool contention, per-user candidate-set scan cost at realistic memory volume, or how p95
moves when many requests overlap. That gap is exactly what was picked up here.

## What was built
`src/test/java/com/innercosmos/evaluation/MemoryRetrievalLoadTest.java` (new file):
- Seeds a **multi-tenant** corpus: 10 virtual users, each carrying the same correctness dataset used
  by `MemoryRetrievalEvaluationTest` (9 labelled query cases with known relevant/prohibited memory
  IDs) plus 200 unrelated filler ("noise") memory cards per user — ~212 rows scanned per
  `MemoryCardMapper.selectList` call per user, materially larger than the 12-row baseline corpus.
- Fires the workload **concurrently**: all (user x case) combinations, 2 rounds each = 180 calls,
  shuffled (fixed seed) so the pool sees mixed users/queries the way real traffic would, submitted to
  a fixed thread pool of **24 threads** — deliberately wider than `application.yml`'s configured
  HikariCP `maximum-pool-size: 10`, so connection-pool contention is genuinely exercised, not
  hand-waved.
- Each task independently times its own `MemoryRetrievalService.retrieve()` call
  (`System.nanoTime()` around just that call) and records: latency, whether `estimatedTokens` stayed
  within `tokenBudget`, whether a prohibited/contradicted memory leaked into the top-3, and whether
  the known-relevant memory was found — i.e. correctness is asserted to hold **while concurrent load
  is in flight**, not just that latency stayed low.
- Writes a full report to `target/evaluation/memory-retrieval-load-v1-report.json` (concurrency,
  corpus size, totalCalls, wallSeconds, throughput, p50/p95/p99/max, budgetViolations,
  prohibitedLeakage, relevantMisses).

## Honest baseline measurement (this machine, this dataset, real run — not tuned)
Two consecutive runs, no changes to the retrieval code between them, to check stability:

| metric | run 1 | run 2 |
|---|---|---|
| totalCalls | 180 | 180 |
| timeouts | 0 | 0 |
| wallSeconds | 1.13 | 1.12 |
| throughput (calls/sec) | 159.1 | 160.1 |
| p50 (ms) | 135.7 | 127.8 |
| p95 (ms) | 243.9 | 257.6 |
| p99 (ms) | 248.9 | 267.6 |
| max (ms) | 250.8 | 270.8 |
| budgetViolations | 0 | 0 |
| prohibitedLeakage | 0 | 0 |
| relevantMisses | 0 | 0 |

No code change was made to `MemoryRetrievalServiceImpl` or `AgentContextAssembler` — this is a
**measurement-only** pass; the existing lexical/ngram/task-fit/freshness/salience/authority scoring
path (real Provider embeddings are opt-in via `MEMORY_EMBEDDING_ENABLED` and off by default/in tests)
already meets these numbers. No tuning was applied to make the number look good: the assertion
thresholds (`p95 <= 400ms`, `p99 <= 800ms`) were set from these two honestly observed runs with
headroom above the actual figures (roughly 1.5-1.6x on p95, 3x on p99), not shaved down to whatever
the first run happened to produce, so a genuine future regression will still trip the gate.
`budgetViolations == 0`, `prohibitedLeakage == 0`, and `relevantMisses == 0` are asserted as hard
equalities (no slack) — under concurrent load, the same privacy and budget guarantees the sequential
test already proves must still hold exactly.

## Test commands and results
```
./mvnw -Dtest=MemoryRetrievalLoadTest test
./mvnw -Dtest=MemoryRetrievalEvaluationTest,AgentContextAssemblerMemoryRetrievalTest,GenomeRuntimeRetrievalEvaluationTest,MemoryRetrievalLoadTest test
```
All green: `MemoryRetrievalLoadTest` 1/1, `MemoryRetrievalEvaluationTest` 1/1,
`AgentContextAssemblerMemoryRetrievalTest` 3/3, `GenomeRuntimeRetrievalEvaluationTest` 1/1 — no
existing retrieval test regressed.

## Remaining (explicitly not attempted here)
- Blind human-rated retrieval quality (human gate, unchanged).
- Contradiction calibration under real-provider semantic similarity (human/real-provider gate,
  unchanged).
- This load test runs against H2 in-memory (`MODE=MySQL`), the project's standard dev/test substrate,
  not PostgreSQL/pgvector; absolute millisecond figures on a live Postgres+Redis topology (e.g.
  `local-complete` or `academy-eks`) were not re-measured here and would likely differ. The
  methodology (multi-tenant corpus, thread pool wider than the connection pool, per-call latency plus
  correctness-under-load) is reusable there without change.
- Corpus scale (200 noise cards/user) is a deliberate order-of-magnitude increase over the 12-row
  baseline, not a proven ceiling for real user memory-store sizes at maturity; if real usage grows
  memory stores substantially larger, this scenario should be re-run at that scale.
