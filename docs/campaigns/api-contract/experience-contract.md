# API v1 Experience Contract

Status: builder-verified baseline; the complete `API-CONTRACT` acceptance item remains in progress.

## User and client promise

- New generated or mobile clients use the stable `/api/v1` core slice. Existing `/api` routes stay
  compatible while domains migrate; a controller alias test prevents documented v1 paths from
  becoming prose-only routes.
- Private Aurora text is POSTed to `/api/v1/aurora/stream-stage`. The subsequent SSE URL contains
  only an opaque one-use token bound to the authenticated owner; another user cannot consume it.
- Core v1 POSTs use `Idempotency-Key`. The first request owns the key; an identical replay returns
  the saved successful response, an in-flight duplicate is rejected with retry guidance, and reuse
  with a different body returns `IDEMPOTENCY_KEY_REUSED` without executing the controller.
- Production idempotency claims and responses live in Redis and are atomic across API Pods. Dev/test
  uses the same state machine in memory. Production startup fails closed if Redis idempotency is off.
- Capsule boundary reads return an ETag. v1 writes require `If-Match` plus `Idempotency-Key`; a stale
  writer receives typed `409 CONFLICT` and cannot silently overwrite a newer owner decision.
- Durable Aurora turn recovery accepts `Last-Event-ID: turnId:sequence` and replays owner-scoped
  timeline events without duplicating already consumed events.

## Error promise

MVC validation, domain exceptions, Spring Security, rate limiting, and idempotency boundaries emit
one JSON error shape:

```json
{
  "success": false,
  "code": "CONFLICT",
  "message": "stale version",
  "status": 409,
  "traceId": "opaque-support-correlation-id",
  "timestamp": "2026-07-16T00:00:00Z",
  "details": {}
}
```

Business codes map to semantic HTTP statuses (`401`, `403`, `404`, `409`) rather than returning every
domain failure as `400`. Stack traces, credentials and private content never enter the envelope.

## Executable source

- Shipped OpenAPI 3.1: `src/main/resources/static/openapi/inner-cosmos-v1.yml`
- Runtime URL: `/openapi/inner-cosmos-v1.yml`
- Drift gate: `OpenApiV1BaselineTest`
- Boundary gates: `ApiContractErrorTest`, `ApiIdempotencyFilterTest`,
  `RedisIdempotencyStoreIntegrationTest`, and the capsule boundary CAS scenario in
  `CapsuleGenomeServiceIntegrationTest`

## Migration boundary and non-claims

The repository currently has 48 REST controllers and 237 method-level mappings. This checkpoint
does not pretend the eight-path external core spec covers all of them. Admin, analytics, psychology,
memory lifecycle, notifications, social connections and several compatibility endpoints still need
v1 schema coverage, pagination and generated-client drift checks. The live token stream is recoverable
through its durable timeline but is not yet backed by a cross-Pod Redis Stream fan-out/heartbeat
transport. Therefore `API-CONTRACT` advances from `UNASSESSED` to `IN_PROGRESS`, not `PASS`.
