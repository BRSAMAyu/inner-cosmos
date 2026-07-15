# INNO-INNER-002 — Memory lifecycle, retrieval and Starfield V2 checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Product slice

- The relational authority now supports `ADD`, `UPDATE`, `MERGE`, `SPLIT`, `LINK`,
  `REINFORCE`, `DECAY`, `CONTRADICT`, `SUPERSEDE`, `ARCHIVE`, `FORGET` and `NO_OP`.
- Preview is read-only. Execution records before/after state, source and result versions,
  evidence, confidence, reason, actor, model/policy identity and the affected memory set.
- Merge and split preserve their source as superseded history and create typed memory links.
  Contradiction and supersession stop stale cards from acting as current facts.
- Forget explicitly scrubs content, provenance and derived fragments/todos/relations/authorized
  context. Its audit tombstone contains no original content.
- The legacy archive endpoint now enters the same operation authority instead of bypassing it.

## Retrieval and Inner Cosmos experience

- `POST /api/memory/retrieval` builds a task-aware Evidence Pack from owner-bound current
  memories. It combines lexical overlap, local character-ngram semantic similarity, task fit,
  salience, freshness and confidence, applies layer diversity and a hard token budget, and
  exposes score contributions.
- Forgotten, superseded and archived cards are excluded. Contradicted cards are excluded by
  default and can only be requested explicitly for conflict-review work.
- `GET /api/memory/starfield/v2` projects the same current-memory authority into deterministic
  time, theme and people views, includes typed links, version/confidence/provenance fields,
  semantic legends and an accessible list equivalent.
- The React Living Aurora surface now exposes all three views with responsive and reduced-motion
  behavior; the previous Aurora interrupt, WakeIntent, Self and correction journeys remain intact.

## Data and migration evidence

- PostgreSQL Flyway `V8__memory_lifecycle_operations.sql` adds lifecycle columns plus
  `tb_memory_operation` and `tb_memory_link`.
- PostgreSQL 16 + pgvector 0.8.1 Testcontainers: 8 migrations, 71 application tables,
  69 identity columns, source-schema table/index/foreign-key equality and application smoke PASS.
- The H2/MySQL-compatible source schema contains the equivalent authority tables and constraints.

## Verification

- Focused Java lifecycle/retrieval/starfield tests: 7/7 PASS.
- Full Java 21 / Spring Boot 3.5 regression: 745/745 PASS across 111 suites.
- React Vitest: 3/3 PASS; TypeScript and Vite production build PASS; bundled SPA regenerated.
- Packaged-JAR Playwright: 6/6 PASS, including the new three-view starfield and accessible-list
  journey in addition to interrupt/replan, durable recovery, WakeIntent, Self and correction.
- `git diff --check`: PASS (line-ending notices only).
- Current-tree secret scan and acceptance-ledger YAML parse: PASS.

## Honest remaining work

G5 is not complete. The retrieval implementation is a deterministic explainable hybrid baseline,
not yet a provider-embedding/pgvector quality claim. Calibrated offline relevance and contradiction
sets, latency thresholds, derived-index invalidation receipts, operation rollback execution,
provenance drill-down, mobile performance measurement, capsule withdrawal completion and
independent blind review remain required before PASS.
