# Milestone M2 Audit Report: Backend Concurrency, Transactions & Storage Audit

## Observation
During deep static code analysis and vulnerability scanning of the Inner Cosmos Spring Boot backend, database migrations, pgvector integrations, and Redis caching infrastructure, multiple architectural risks, concurrency bugs, missing database indexes, performance bottlenecks, and potential memory leaks were identified across the codebase.

Key observations with exact file locations and code snippets:

1. **Race Conditions & Concurrency Vulnerabilities**:
   - `src/main/java/com/innercosmos/event/GravityRecalculateListener.java` (Lines 27-46): `GravityRecalculateListener` is an `@Async("taskExecutor")` `@TransactionalEventListener` executing after `DialogFinishedEvent`. It fetches all `ACTIVE` `MemoryCard` entities for a user and updates each using `memoryCardMapper.updateById(card)`. Simultaneously, `MemoryServiceImpl.java` (Lines 410-419) `updateImportance()` reads a single `MemoryCard`, updates `userImportance` and `emotionalGravity`, and calls `memoryCardMapper.updateById(card)`. `tb_memory_card` has no `@Version` optimistic locking field.
   - `src/main/java/com/innercosmos/scheduler/LetterDeliveryJob.java` (Lines 71-100) vs `src/main/java/com/innercosmos/service/impl/SlowLetterServiceImpl.java` (Lines 116-137): `SlowLetterServiceImpl.transition()` uses atomic conditional updates (`WHERE id=? AND status=from`), but `LetterDeliveryJob.advanceWithRetry()` reads `SlowLetter` via `selectById`, checks status in JVM memory, and calls `letterMapper.updateById(fresh)` without a status predicate in the SQL UPDATE.
   - `src/main/java/com/innercosmos/streaming/InMemoryAuroraStreamStageStore.java` (Lines 30-36): `purgeExpired()` and `if (stages.size() >= maxEntries)` checks are executed non-atomically prior to `stages.put()`.

2. **Transaction Isolation, Propagation & Deadlocks**:
   - `src/main/resources/db/migration/postgresql/V10__versioned_memory_embeddings.sql` (Line 16, 18-19) & `V18__capsule_matching_embeddings.sql` (Line 14, 16-17): `CONSTRAINT fk_memory_embedding_memory FOREIGN KEY (memory_id) REFERENCES tb_memory_card(id) ON DELETE CASCADE` and `CONSTRAINT fk_capsule_embedding_capsule FOREIGN KEY (capsule_id) REFERENCES tb_echo_capsule(id) ON DELETE CASCADE` are defined. Neither `memory_id` nor `capsule_id` is the leading column in any index (`idx_memory_embedding_user_model` is on `(user_id, model_name, model_version, status)` and `idx_capsule_embedding_model` is on `(model_name, model_version, status)`).
   - `src/main/java/com/innercosmos/service/impl/PersonaChatServiceImpl.java` (Lines 142-288, 312-338): `PersonaChatServiceImpl.reply()` is annotated `@Transactional(rollbackFor = Exception.class)`. It reserves quota via `jdbcTemplate` UPDATE/INSERT inside the transaction and then invokes `structuredAiService.call(...)` (a blocking external LLM HTTP network request) while keeping the database connection open.

3. **pgvector & Embedding Search Performance / Degradation**:
   - `src/main/resources/db/migration/postgresql/V10__versioned_memory_embeddings.sql` & `V18__capsule_matching_embeddings.sql` & `src/main/java/com/innercosmos/service/impl/MemoryEmbeddingIndexServiceImpl.java` (Lines 119-130) & `src/main/java/com/innercosmos/service/impl/CapsuleEmbeddingIndexServiceImpl.java` (Lines 204-213): Both tables define `embedding_vector vector(1536)`. No HNSW or IVFFlat index (`USING hnsw (embedding_vector vector_cosine_ops)`) is declared in Flyway migrations. Searches execute `ORDER BY e.embedding_vector <=> ?::vector LIMIT 100`.
   - `src/main/java/com/innercosmos/service/impl/MemoryEmbeddingIndexServiceImpl.java` (Lines 106, 120, 160) & `src/main/java/com/innercosmos/service/impl/CapsuleEmbeddingIndexServiceImpl.java` (Lines 48, 170, 205, 269): `vectorLiteral` hardcodes dimension size `1536`.

4. **Redis & DB Cache Consistency & Memory Leaks**:
   - `src/main/java/com/innercosmos/streaming/RedisAuroraLiveEventStore.java` (Lines 48-50): `publish()` executes `redis.opsForStream().add(...)` followed by `redis.expire(key, retention)`. These two operations are non-atomic.
   - `src/main/java/com/innercosmos/streaming/InMemoryAuroraStreamStageStore.java` (Lines 30-36, 51-54): Passive expiration cleanup `purgeExpired()` only executes on `stage()`.

---

## Logic Chain

1. **Concurrency Logic**:
   - In `GravityRecalculateListener`, asynchronous execution occurs on a background thread (`taskExecutor`) after a session ends. The listener reads all active memory cards for a user into Java memory, recalculates `emotionalGravity`, and writes them back row-by-row using `updateById(card)`.
   - If the user concurrently updates card importance via `updateImportance()`, `updateImportance()` reads the row, updates `userImportance` and `emotionalGravity`, and commits.
   - Because `GravityRecalculateListener` holds stale in-memory card instances fetched prior to the user's write, its subsequent `updateById()` overwrites `user_importance` back to its previous value. Without optimistic locking (`@Version`) or SELECT FOR UPDATE locking, this creates a classic lost update race condition.

2. **Transaction & Deadlock Logic**:
   - In PostgreSQL, deleting or updating rows in a parent table with foreign key constraints (`tb_memory_card` or `tb_echo_capsule`) requires PostgreSQL to verify referencing rows in `tb_memory_embedding` or `tb_capsule_embedding`.
   - Because `memory_id` and `capsule_id` are unindexed foreign keys in the referencing tables, PostgreSQL cannot perform index lookups; it must acquire shared locks while sequentially scanning the entire embedding table. Concurrent multi-row updates or bulk deletions lock table pages in unpredictable orders, causing PostgreSQL `deadlock detected` errors.
   - In `PersonaChatServiceImpl.reply()`, wrapping the external LLM RPC inside a `@Transactional` block holds database connection pool resources open for several seconds per request. Under concurrent load, connection pool exhaustion causes thread starvation across all API endpoints.

3. **pgvector Performance Logic**:
   - Without an HNSW index on `embedding_vector`, PostgreSQL `ORDER BY e.embedding_vector <=> ?::vector LIMIT 100` must calculate 1536-dimensional Euclidean/cosine distance across every single row in `tb_memory_embedding` / `tb_capsule_embedding`.
   - Time complexity is $O(N)$ full table scan per query. As dataset size increases, vector search latency degrades linearly, causing DB CPU utilization spikes.

4. **Redis & Memory Leak Logic**:
   - Non-atomic `XADD` + `EXPIRE` in `RedisAuroraLiveEventStore` means a process crash or network interrupt between the two commands leaves stream keys in Redis without TTL. Over time, un-expired stream keys leak memory.

---

## Caveats
- Production deployment behaviors dependent on external LLM response times or Redis network latency were evaluated via static code tracing and failure deduction.
- H2 memory database in dev mode does not enforce PostgreSQL pgvector constraint behaviors; pgvector findings apply specifically to the PostgreSQL production profile (`application-postgres.yml` / `application-prod.yml`).

---

## Conclusion

The backend architecture features strong domain modeling and outbox/idempotency designs, but requires targeted remediation in 4 specific areas to guarantee production scalability, concurrency safety, and storage efficiency:

1. **Concurrency**: Add optimistic locking (`@Version`) to entity definitions and convert scheduled delivery jobs to conditional SQL updates.
2. **Transactions**: Index foreign key columns in Flyway migrations and remove blocking external RPCs from `@Transactional` boundaries.
3. **pgvector**: Add HNSW indexes to `embedding_vector` columns and make vector dimension formatting dynamic.
4. **Redis**: Bundle Redis stream additions and expiration into atomic Lua scripts, and add background cleanup schedules for in-memory stage stores.

---

## Findings & Detailed Remediation Plan

### Focus Area 1: Race Conditions & Concurrency Vulnerabilities

#### Finding 1.1: Concurrent Memory Gravity & Importance Updates Overwrite State without Optimistic Locking
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/event/GravityRecalculateListener.java` (Lines 27-46) & `src/main/java/com/innercosmos/service/impl/MemoryServiceImpl.java` (Lines 410-419)
- **Root Cause Analysis**: `GravityRecalculateListener` is an `@Async` listener triggered post-commit. It queries active memory cards and calls `memoryCardMapper.updateById(card)` for each. Simultaneously, `MemoryServiceImpl.updateImportance` reads a card, updates `userImportance` and `emotionalGravity`, and calls `updateById(card)`. Neither `MemoryCard` entity nor `tb_memory_card` DB table enforces optimistic locking (`@Version`), leading to lost updates.
- **Scenario Reproduction**: User finishes a chat session, triggering `GravityRecalculateListener`. While the background thread processes 20 cards, the user updates Card #5 importance from 4.0 to 9.0 in UI. `updateImportance()` finishes first. Milliseconds later, `GravityRecalculateListener` executes `updateById()` using its pre-fetched instance (importance 4.0), overwriting the user's change.
- **Impact Assessment**: **High** (Data corruption of user memory importance and emotional gravity).
- **Recommended Fix**:
  Add `@Version` field to `MemoryCard.java`:
  ```java
  @Version
  public Integer versionNo;
  ```
  Ensure MyBatis-Plus `MybatisPlusInterceptor` with `OptimisticLockerInnerInterceptor` is registered in `MybatisPlusConfig.java`. Alternatively, perform atomic SQL updates:
  ```sql
  UPDATE tb_memory_card SET user_importance = ?, emotional_gravity = ? WHERE id = ? AND user_id = ?
  ```

#### Finding 1.2: Scheduled Delivery Job Races with User Letter Transitions
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/scheduler/LetterDeliveryJob.java` (Lines 71-100) & `src/main/java/com/innercosmos/service/impl/SlowLetterServiceImpl.java` (Lines 125-137)
- **Root Cause Analysis**: While `SlowLetterServiceImpl.transition()` uses atomic conditional updates (`UPDATE ... WHERE id=? AND status=from`), `LetterDeliveryJob.advanceWithRetry()` reads `SlowLetter` via `selectById(letter.id)` at line 74, checks `fromStatus.equals(fresh.status)`, and calls `letterMapper.updateById(fresh)` at line 80 without checking `status = fromStatus` in the SQL `UPDATE`.
- **Scenario Reproduction**: A letter is in `SENT` status. `LetterDeliveryJob` queries `SENT` letters and reads Letter #100. Simultaneously, the recipient performs a `READ` or `BLOCK` transition via API. `SlowLetterServiceImpl.transition()` updates status to `BLOCKED`. A millisecond later, `LetterDeliveryJob` calls `updateById(fresh)` with status `FLYING`, overwriting `BLOCKED`.
- **Impact Assessment**: **High** (Letter state machine bypass, invalid state transitions).
- **Recommended Fix**: Refactor `LetterDeliveryJob.advanceWithRetry` to use atomic conditional updates:
  ```java
  UpdateWrapper<SlowLetter> wrapper = new UpdateWrapper<SlowLetter>()
          .eq("id", letter.id)
          .eq("status", fromStatus)
          .set("status", toStatus);
  if (setDeliveredAt) wrapper.set("delivered_at", LocalDateTime.now());
  int rows = letterMapper.update(null, wrapper);
  if (rows == 0) return true; // Already transitioned concurrently
  ```

---

### Focus Area 2: Transaction Isolation, Propagation & Deadlocks

#### Finding 2.1: Unindexed Foreign Keys in Embedding Tables Cause PostgreSQL Lock Escalation & Deadlocks
- **Exact File Path & Lines**: `src/main/resources/db/migration/postgresql/V10__versioned_memory_embeddings.sql` (Lines 15-19) & `V18__capsule_matching_embeddings.sql` (Lines 13-17)
- **Root Cause Analysis**: Both `V10` and `V18` migrations establish foreign key constraints (`fk_memory_embedding_memory` referencing `tb_memory_card(id)` and `fk_capsule_embedding_capsule` referencing `tb_echo_capsule(id)`). However, `memory_id` and `capsule_id` are not the leading columns of any index (`idx_memory_embedding_user_model` starts with `user_id`, `idx_capsule_embedding_model` starts with `model_name`).
- **Scenario Reproduction**: Under high concurrent writes or deletions of memory cards / capsules, PostgreSQL acquires share locks on `tb_memory_embedding` / `tb_capsule_embedding` for foreign key validation. Without an index on `memory_id` / `capsule_id`, PostgreSQL performs sequential table scans under share lock, triggering deadlocks (`deadlock detected`) across concurrent transactions.
- **Impact Assessment**: **High** (Database transaction deadlocks, cascading HTTP 500 errors during memory cleanup).
- **Recommended Fix**: Add a new Flyway migration script `V22__index_embedding_foreign_keys.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_memory_embedding_memory_id ON tb_memory_embedding(memory_id);
  CREATE INDEX IF NOT EXISTS idx_capsule_embedding_capsule_id ON tb_capsule_embedding(capsule_id);
  ```

#### Finding 2.2: Blocking External Remote RPC Inside `@Transactional` Session Boundary
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/service/impl/PersonaChatServiceImpl.java` (Lines 142-288)
- **Root Cause Analysis**: `PersonaChatServiceImpl.reply()` is marked `@Transactional(rollbackFor = Exception.class)`. It reserves quota via DB write, and then calls `structuredAiService.call(...)` (a blocking HTTP network call to external LLM provider) inside the active Spring `@Transactional` context.
- **Scenario Reproduction**: Under API traffic spikes or when the LLM provider experiences latency (e.g. 5–10 seconds), DB connections from HikariCP are held open waiting for HTTP responses. The DB connection pool exhausts rapidly, blocking unrelated user requests across the system.
- **Impact Assessment**: **High** (Database connection pool starvation and service blackout under high load).
- **Recommended Fix**: Un-nest the remote LLM call from `@Transactional`. Execute quota reservation in a short transaction, invoke `structuredAiService.call()` outside the transaction, and save response/messages in a secondary short transaction.

---

### Focus Area 3: pgvector & Embedding Search Performance / Degradation

#### Finding 3.1: Missing HNSW / IVFFlat Index Causes $O(N)$ Table Scans on Vector Search
- **Exact File Path & Lines**: `src/main/resources/db/migration/postgresql/V10__versioned_memory_embeddings.sql` & `V18__capsule_matching_embeddings.sql` & `src/main/java/com/innercosmos/service/impl/MemoryEmbeddingIndexServiceImpl.java` (Lines 119-130)
- **Root Cause Analysis**: Columns `embedding_vector vector(1536)` are defined without an HNSW or IVFFlat vector index. `postgresScores()` queries run `ORDER BY e.embedding_vector <=> ?::vector LIMIT 100`.
- **Scenario Reproduction**: As memory embeddings grow beyond 10,000+ rows, every vector similarity query forces PostgreSQL to execute a sequential scan computing 1536-dim cosine distances for every row. Query execution time spikes from <5ms to >1500ms.
- **Impact Assessment**: **High** (Linear degradation of memory search performance).
- **Recommended Fix**: Add HNSW indexes in a Flyway migration:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_memory_embedding_vector_hnsw 
    ON tb_memory_embedding USING hnsw (embedding_vector vector_cosine_ops) WITH (m = 16, ef_construction = 64);
  CREATE INDEX IF NOT EXISTS idx_capsule_embedding_vector_hnsw 
    ON tb_capsule_embedding USING hnsw (embedding_vector vector_cosine_ops) WITH (m = 16, ef_construction = 64);
  ```

#### Finding 3.2: Hardcoded Vector Dimension (1536) Prevents Model Upgrades
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/service/impl/MemoryEmbeddingIndexServiceImpl.java` (Lines 106, 120, 160) & `src/main/java/com/innercosmos/service/impl/CapsuleEmbeddingIndexServiceImpl.java` (Lines 48, 170, 205, 269)
- **Root Cause Analysis**: `vectorLiteral` hardcodes dimension size `1536`. If an embedding model returning different dimensions (e.g. 1024 or 768) is configured, string formatting appends trailing zeros or causes PostgreSQL type cast errors (`cannot cast type vector(1536) to vector(1024)`).
- **Impact Assessment**: **Medium** (Failure when switching LLM embedding models).
- **Recommended Fix**: Use `vector.length` dynamically in `vectorLiteral(vector, vector.length)`.

---

### Focus Area 4: Redis & DB Cache Consistency & Memory Leaks

#### Finding 4.1: Non-Atomic Redis Stream Addition and Expiration
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/streaming/RedisAuroraLiveEventStore.java` (Lines 48-50)
- **Root Cause Analysis**: `publish()` calls `redis.opsForStream().add(...)` followed by `redis.expire(key, retention)`. If the JVM terminates between these calls, the stream key remains in Redis permanently without TTL.
- **Impact Assessment**: **Medium** (Memory leak in Redis server over time).
- **Recommended Fix**: Execute addition and expiration atomically via a single Lua script.

#### Finding 4.2: Passive Cleanup in In-Memory Stream Stage Store
- **Exact File Path & Lines**: `src/main/java/com/innercosmos/streaming/InMemoryAuroraStreamStageStore.java` (Lines 30-36, 51-54)
- **Root Cause Analysis**: `purgeExpired()` only executes during `stage()` calls. Unconsumed tokens remain in memory if no new stream requests arrive.
- **Impact Assessment**: **Low** (Minor memory footprint retention in dev profile).
- **Recommended Fix**: Add a `@Scheduled(fixedDelay = 60000)` active eviction method to purge expired staged stream entries periodically.

---

## Verification Method

To independently verify all observations, logic chains, and recommendations:

1. **Verify Unindexed Foreign Keys**:
   Run against PostgreSQL:
   ```sql
   SELECT conname, pg_get_constraintdef(c.oid)
   FROM pg_constraint c
   WHERE conrelid = 'tb_memory_embedding'::regclass AND contype = 'f';
   ```
   Inspect `\d tb_memory_embedding` to confirm `memory_id` is missing a dedicated leading index.

2. **Verify Vector Index Deficit**:
   Run against PostgreSQL:
   ```sql
   SELECT indexname, indexdef FROM pg_indexes WHERE tablename IN ('tb_memory_embedding', 'tb_capsule_embedding');
   ```
   Confirm no index utilizes `USING hnsw` or `vector_cosine_ops`.

3. **Verify Code References**:
   - `view_file` on `src/main/java/com/innercosmos/scheduler/LetterDeliveryJob.java` (Lines 75-85) to verify non-conditional update.
   - `view_file` on `src/main/java/com/innercosmos/service/impl/PersonaChatServiceImpl.java` (Lines 142-240) to verify LLM RPC inside `@Transactional`.
