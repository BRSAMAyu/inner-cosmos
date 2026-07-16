# API-CONTRACT-002 — Generated client truth and compatibility gate

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16.

## Implemented

- Added a checked-in `openapi-typescript` declaration generated from the shipped OpenAPI 3.1 file.
- Isolated the generator in `web/tools/openapi` with pinned TypeScript 5.9.3 so the application can
  remain on TypeScript 7 without peer/runtime ambiguity; pnpm 11.9.0 and Node 22.20.0 are explicit.
- Replaced handwritten core request shapes for login, Aurora stream staging, capsule creation and
  boundary updates, persona messages and slow-letter drafts with generated schema types. The capsule
  boundary response type is generated as well.
- Added a generation drift gate and an OpenAPI 3.1 semantic compatibility gate. The latter compares
  the accepted Git ref with the candidate and rejects removed paths/operations/success responses,
  new required parameters or bodies, removed request media types, removed properties/schemas,
  required-set changes, enum narrowing/new restrictions and type narrowing.
- Added the same gates, the PWA build and component/protocol tests to GitHub Actions using frozen
  pnpm installation with dependency scripts disabled.

## Verification

- Initial RED: `npm run api:check` failed because no generation/check command existed.
- Generator drift gate: PASS against `web/src/generated/inner-cosmos-v1.d.ts`.
- Compatibility-rule tests: 6/6 PASS, including removed operation, new required property, enum/type
  narrowing and unrestricted-to-enum failures; optional fields and nullable widening remain compatible.
- Current OpenAPI compared with accepted `HEAD`: PASS, no breaking changes.
- Frontend: TypeScript 7 + production Vite build PASS; Vitest 15 files / 60 tests PASS.
- Server focused regression: `OpenApiV1BaselineTest`, `ApiContractErrorTest` and
  `ApiIdempotencyFilterTest`, 7 tests PASS on Java 21.
- Both npm and pnpm lockfiles resolve with zero npm audit findings; CI uses the frozen pnpm workspace.

## Honest boundary

This closes generated-client drift and unreviewed breaking changes for the documented external core
slice. It does not claim full-controller OpenAPI coverage, cursor pagination consistency, a published
mobile SDK, or live cross-Pod SSE fan-out/heartbeat behavior. `API-CONTRACT` remains `IN_PROGRESS`.
