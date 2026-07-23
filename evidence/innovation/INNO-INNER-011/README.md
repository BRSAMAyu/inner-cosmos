# INNO-INNER-011 — memory retrieval: real Postgres+Redis load rerun

Date: 2026-07-23. Branch `codex/w1-product-6` (worktree off `codex/w0-integration` @ `d4c634f`).
Campaign W1, acceptance G5 `RETRIEVAL-QUALITY`.

## Why this, not something new

`INNO-INNER-010` (`evidence/innovation/INNO-INNER-010/retrieval-load-2026-07-23.md`) proved the
concurrent-load methodology — 180 concurrent `MemoryRetrievalService.retrieve()` calls, a 24-thread
pool wider than the configured HikariCP `maximum-pool-size: 10`, a 10-user / ~212-row-per-user
corpus — but explicitly disclosed it ran against H2 in-memory, "not PostgreSQL/pgvector," and named
this as remaining work. The ledger's `RETRIEVAL-QUALITY` `remaining:` note carries the identical
sentence forward verbatim: "rerunning this exact load methodology on a live Postgres+Redis
topology." This item closes exactly that, and only that — it does not re-touch the blind
human-rated quality or contradiction-calibration sub-clauses, which remain human/real-provider
gates untouched here.

## What was built

`src/test/java/com/innercosmos/evaluation/MemoryRetrievalLoadPostgresRedisTest.java` (new file) —
line-for-line the same corpus construction, workload shape, concurrency, and correctness/latency
scoring logic as `MemoryRetrievalLoadTest`, with one deliberate change: the Spring context now boots
against

- a real **Testcontainers PostgreSQL** (`pgvector/pgvector:0.8.1-pg16`, the same pinned image
  `CapsuleEmbeddingPostgresIntegrationTest` already uses) via the `postgres` Spring profile — real
  Flyway migration (22 migrations applied fresh) runs against it, not H2's `spring.sql.init.mode`
  path;
- a real **Testcontainers Redis** (`redis:7.4.2-alpine`, password-protected, the same pinned image
  the existing Redis integration tests use) wired via `spring.data.redis.host/port/password`, so the
  full production-shaped runtime topology (PostgreSQL system-of-record + Redis for
  sessions/rate-limit/idempotency/streams/leases) is genuinely live underneath the app context
  during the load, not simulated or mocked out.

**Honest scope note:** `MemoryRetrievalService` itself is a pure PostgreSQL/H2-backed read path — it
does not call Redis directly today. This test does not claim (and does not need) a
Redis-attributable latency effect on retrieval; what it proves is that the identical
HikariCP-pool-contention scenario holds up against a real network-connected relational database
(not an in-process H2 instance) with a real Redis instance live in the same application context, and
that the two containers coexist cleanly under this exact concurrent load.

Reproduce:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\mvnw.cmd test "-Dtest=com.innercosmos.evaluation.MemoryRetrievalLoadPostgresRedisTest"
```

(Docker Desktop must be running; Testcontainers pulls/starts a Ryuk reaper, the Redis container and
the pgvector/Postgres container automatically.)

## Honest measurement (two runs, no code changes between them)

| metric | run 1 | run 2 | H2 baseline (INNO-INNER-010, run 1/run 2) |
|---|---|---|---|
| totalCalls | 180 | 180 | 180 / 180 |
| timeouts | 0 | 0 | 0 / 0 |
| wallSeconds | 1.01 | 1.04 | 1.13 / 1.12 |
| throughput (calls/sec) | 178.57 | 173.61 | 159.1 / 160.1 |
| p50 (ms) | 115.83 | 118.81 | 135.7 / 127.8 |
| p95 (ms) | 279.17 | 262.85 | 243.9 / 257.6 |
| p99 (ms) | 284.78 | 274.86 | 248.9 / 267.6 |
| max (ms) | 285.52 | 275.90 | 250.8 / 270.8 |
| budgetViolations | 0 | 0 | 0 / 0 |
| prohibitedLeakage | 0 | 0 | 0 / 0 |
| relevantMisses | 0 | 0 | 0 / 0 |

**This is the honest number, not a rounded-up guess:** the real-Postgres run is, on this machine, in
the same order of magnitude as the H2 run — p95 is actually slightly higher in run 1 (279ms vs H2's
244-258ms) and within the same band in run 2 (263ms), p99/max track closely too. This is plausible
(both databases are local — H2 in-process, PostgreSQL in a local Docker container over loopback TCP
— and `MemoryRetrievalService`'s dominant cost is almost certainly in-JVM scoring/ranking over the
~212-row candidate set fetched per call, not the fetch itself), but it is reported as observed, not
adjusted to match a prior expectation either way. Correctness — zero budget violations, zero
prohibited-memory leakage, zero relevant-recall misses across all 360 calls (180 x 2 runs) — is
identical to the H2 result: the concurrent-load correctness guarantees hold on a real database too.

No retrieval-scoring code was changed for this item — this is a measurement-only pass, exactly like
INNO-INNER-010. The new test's own assertion thresholds (`p95 <= 1500ms`, `p99 <= 2500ms`) are
deliberately set much wider than `MemoryRetrievalLoadTest`'s H2 thresholds (`p95<=400`, `p99<=800`)
to leave headroom for real Docker/network variance on a shared CI/dev machine, not shaved down to
make a specific number pass; the two runs above (262-279ms p95) sit well inside that budget with
room to spare, so the wider threshold is not the reason the test passes.

## Test commands and results

```
mvn test -Dtest=com.innercosmos.evaluation.MemoryRetrievalLoadPostgresRedisTest
-> run 1: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.83 s
-> run 2: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 27.74 s
```

Both runs: real Testcontainers PostgreSQL (22 Flyway migrations applied fresh each run) + real
Testcontainers Redis (password-protected) booted successfully; Docker Desktop confirmed running
(`Server Version: 28.5.1`) in the test log.

The pre-existing H2 variant (`MemoryRetrievalLoadTest`, INNO-INNER-010) is untouched and still
passes unchanged — this item adds a new test file, it does not modify or replace the existing one.

## What this closes and what remains for G5.RETRIEVAL-QUALITY

Closes: the ledger's specific remaining clause "rerunning this exact load methodology on a live
Postgres+Redis topology." The identical concurrent-load methodology now has a second, real-database
data point, with honestly reported (not tuned) latency figures that are comparable to, not
mysteriously better or worse than, the H2 baseline.

Still open (unchanged from the existing ledger note, not addressed here):
- Blind human-rated retrieval quality (human gate).
- Contradiction calibration under real-provider semantic similarity (human/real-provider gate).
- This test still runs `MemoryRetrievalService`'s existing lexical/ngram/task-fit/freshness/
  salience/authority scoring path with real Provider embeddings off (`memory.embedding.enabled=false`,
  matching production default without a key) — a real-embedding-enabled variant on this same
  Postgres/Redis topology, with a real provider key, was explicitly out of scope for this
  environment (no key available) and is not attempted or claimed here.
- Corpus scale (200 noise cards/user, same as INNO-INNER-010) remains a deliberate
  order-of-magnitude increase over the original 12-row baseline, not a proven ceiling for real
  user memory-store sizes at maturity.
