# API-CONTRACT-001 — Executable v1 core contract baseline

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16.

## Implemented

- Shipped an OpenAPI 3.1.0, version `1.0.0` core artifact for auth, Aurora message/staging,
  resumable turn SSE, capsule creation/boundaries, slow-letter drafts and persona messages.
- Added real `/api/v1` aliases for the documented core controllers while retaining legacy routes.
- Replaced MVC, Security and rate-limit error variants with `ApiErrorResponse` (`code`, semantic HTTP
  status, `traceId`, timestamp and safe details).
- Added request-fingerprint idempotency with response replay, in-flight exclusion and key/payload
  conflict rejection. Dev/test uses a concurrent in-memory store; production uses Redis Lua claims
  and fails startup when distributed idempotency is disabled. Replays retain ETag; Redis completion
  failure returns one clean 503 and never leaks the unprotected buffered 2xx.
- Added capsule-boundary optimistic concurrency (`version`, ETag, required v1 `If-Match`, SQL CAS),
  H2 convergence and PostgreSQL Flyway V17.
- Made Aurora stream staging owner-bound, one-use, bounded and lazily expiring. The React/PWA now
  sends private text in the staging POST and opens `/api/v1/aurora/stream?token=...`; private text no
  longer appears in the stream URL.
- React/PWA preserves idempotency keys through CSRF/OIDC retries and carries capsule boundary ETags.

Experience semantics: [`experience-contract.md`](../../../docs/campaigns/api-contract/experience-contract.md).

## Verification

- Red gates first proved the typed error/idempotency classes and OpenAPI artifact were absent.
- `ApiContractErrorTest`, `ApiIdempotencyFilterTest`, `ApiRateLimitFilterTest`,
  `OpenApiV1BaselineTest`, production guard and affected controller/security regression: PASS.
- Redis 7.4.2 Testcontainers: two independent store instances share atomic claim, in-progress,
  replay, different-payload conflict and TTL semantics: PASS.
- PostgreSQL 16 + pgvector: 17 Flyway migrations, exact table/index/FK reconciliation, application
  smoke and second-migrate-zero gate: PASS.
- Capsule boundary CAS integration: first writer advances version 1→2; stale version 1 writer receives
  `CONFLICT`: PASS.
- React/Vitest: 15 files, 60 tests, PASS. TypeScript and production Vite build: PASS.
- Full Java 21 gate: 135 suites, 819 tests, 0 failures, 0 errors, 0 skipped (`146.7s`).
- Current React source and built artifact contain the staged-token v1 stream URL and no legacy
  private-message stream query. YAML/OpenAPI parse, current-tree secret scan and diff checks are part
  of checkpoint reconciliation.

## Honest remaining work

- Only the external high-value slice is specified; many internal/admin and secondary product routes
  are not yet in v1 OpenAPI.
- A generated TypeScript/mobile client and breaking-change diff gate are not yet checked in.
- SSE durable replay exists, but live cross-Pod fan-out, heartbeat/load behavior and Redis Stream
  resume are not closed.
- Pagination/cursor consistency and optimistic concurrency across other mutable resources remain.
