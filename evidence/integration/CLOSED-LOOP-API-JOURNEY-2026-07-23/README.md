# CLOSED-LOOP-API-JOURNEY-2026-07-23

> Branch: `codex/w1-product-4` (worktree off `codex/w0-integration` @ `dc70f7a`).
> Ledger: G5 `PROFILE-PROPAGATION`, G6 `SOCIAL-CLOSED-LOOP`.

## What this closes

The W1/W2/W4 handoff (`docs/goal/claude-w2-w4-handoff-2026-07-23.md` §3.3) asks for the Aurora →
memory/profile → capsule → matching/slow-social provenance/authorization/correction/withdrawal chain
to be "proven end-to-end via a REAL API journey (not just unit tests)". Existing evidence for this
chain was either:

- **service-layer-direct** (e.g. `CapsuleGenomeServiceIntegrationTest`, `UserCorrectionServiceImpl`'s
  own tests) — calls the Java method directly, never goes through HTTP/a controller, or
- **browser-driven** (Playwright, `evidence/integration/FINAL-STABILIZATION-2026-07-20/`) — real, but
  requires Node/a browser and is the W2 UI agent's domain, out of this agent's scope
  (`web/**`/`deploy/**` are off-limits here).

There was no fast, CI-native Spring Boot integration test that drives **only the real REST endpoints**
across the whole chain and asserts on their real HTTP responses. That is the concrete gap closed here.

## New test

`src/test/java/com/innercosmos/controller/MemoryCorrectionCapsuleClosedLoopApiJourneyTest.java`
(`@SpringBootTest` + `@AutoConfigureMockMvc`, real H2, mock LLM — same pattern as the existing
`DataRightsControllerTest`). One real HTTP journey, two real registered accounts (owner + a
genuinely different visitor), real cross-request sessions (no seeded session objects):

1. `POST /api/auth/register` x2 — owner and visitor, real accounts, real sessions.
2. (precondition, JDBC seed, matching the existing `CapsuleSyncEndToEndIT`/
   `CapsuleP1P2PrivacyBoundaryTest` convention) — one ACTIVE `MemoryCard` for the owner. Aurora's own
   memory-extraction pipeline is a separately-covered slice; this test starts from its output.
3. `POST /api/capsule/create-from-memory` — the owner compiles a real public capsule from that
   memory over HTTP. Verified via the response body: `visibilityStatus=PUBLIC`, `isPublic=true`.
4. `POST /api/persona-chat/session/create` + `POST /api/persona-chat/message` — the **visitor**
   (a different account) discovers and chats with the capsule over real HTTP; the reply is a real,
   non-empty capsule message (mock LLM).
5. `POST /api/aurora/corrections/confirm` — the owner authoritatively corrects the SAME memory the
   capsule was authorized against. This is the real `UserCorrectionServiceImpl#confirm` path:
   supersedes the memory, flags the `AuthorizedMemoryRef` `NEEDS_REVIEW`, de-lists the capsule,
   retires its matching-index vector, and records a data-retraction receipt.
6. `GET /api/capsule/{id}` (owner) — real HTTP proof the correction propagated:
   `visibilityStatus=NEEDS_REVIEW`, `isPublic=false`.
7. `POST /api/persona-chat/session/create` (the SAME visitor session that chatted successfully in
   step 4) — real HTTP proof the withdrawal is enforced live at the API boundary: a genuine
   **403 Forbidden** (via `GlobalExceptionHandler`'s `BusinessException` → `HttpStatus` mapping), not
   just a DB column nobody re-checks.
8. `GET /api/me/data-rights/receipts` (owner) — real HTTP proof of the auditable receipt trail:
   both a `CAPSULE_MATCH_INDEX` and a `MEMORY_EMBEDDING` derivative-erasure receipt are present.
   This exercises `G5.PROFILE-PROPAGATION`'s "complete receipts" remaining note through the actual
   correction flow, not by seeding `retractionReceiptService.record()` directly the way
   `DataRightsControllerTest` does.
9. `GET /api/aurora/corrections` + `DELETE /api/aurora/corrections/{id}` + re-`GET` — the owner's own
   correction history and retire action, over real HTTP, closing the loop.

## Result

```text
mvn test -Dtest=com.innercosmos.controller.MemoryCorrectionCapsuleClosedLoopApiJourneyTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Passed on the first real run (no fixture massaging needed) — the production propagation/authorization
chain genuinely works end to end through the real HTTP surface, with two distinct real accounts and
no seeded session shortcuts.

## Honest scope

- This is one linear happy-path journey (create → chat → correct → verify de-listed → verify
  visitor blocked → verify receipt → retire), not an exhaustive matrix of every correction/withdrawal
  variant — those variants already have dedicated unit/service-level coverage elsewhere
  (`UserCorrectionServiceImpl`'s own tests, `CapsuleGenomeServiceIntegrationTest`, Playwright).
- Memory *extraction* (Aurora conversation → memory card) is not re-proven here; it starts from a
  seeded `MemoryCard`, consistent with how `CapsuleSyncEndToEndIT` and
  `CapsuleP1P2PrivacyBoundaryTest` already scope their own preconditions.
- Slow-letter delivery and the connection-request path are not included in this particular journey
  (`G6.SOCIAL-CLOSED-LOOP`'s discovery→letter→inbox→connection sub-chain already has its own
  evidence in `evidence/integration/FINAL-STABILIZATION-2026-07-20/` and Playwright specs) — this
  journey's scope is deliberately the correction/authorization/withdrawal/receipt half, which is
  what `G5.PROFILE-PROPAGATION`'s own remaining note names.
- Still open per both ledger items' remaining notes: non-author review, production topology,
  notification/device delivery, and moderation operations — all human/production gated, unchanged
  by this test.
