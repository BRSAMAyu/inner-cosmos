# INNO-CAP-009 — Versioned and revocable capsule DataUseGrant

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16.

## Implemented contract

- Added `tb_data_use_grant`, `DataUseGrant`, mapper and service with owner/resource version,
  purpose, consumer, grant version/parent, consent source, status and revocation facts.
- Normal and simulator capsules create distinct primary purposes plus a separate Provider-egress
  grant. Grants are written before `generateUserPersona`, closing the previous audit-order gap where
  derived memory text could reach a Provider before a durable purpose record existed.
- `AuthorizedMemoryRef.dataUseGrantId` links the content-bearing reference to its primary grant.
  Genome authorization snapshots upgraded to `capsule-authorization.v2` and include both purposes.
- Recompile revokes v1 and creates parented v2 grants; archive, owner revoke, memory mutation and
  FORGET revoke active grants. PersonaChat and publication fail closed on missing, duplicated,
  stale-resource-version or revoked purposes.
- Owner-isolated history and revoke APIs are available at
  `GET /api/capsule/{id}/data-use-grants` and
  `POST /api/capsule/{id}/data-use-grants/{grantId}/revoke`.
- FORGET deletes excerpts/references but keeps content-free revoked grant tombstones. Owner ids are
  intentionally snapshots rather than foreign keys so account deletion cannot erase the audit fact.

Experience semantics: [`data-use-grant-contract.md`](../../../docs/campaigns/capsule-genome/data-use-grant-contract.md).

## Verification

- Red gate: new integration test initially failed compilation because no grant model/service existed.
- Compatibility red gate then found that a strict owner FK broke the existing forgotten-subject
  scenario. The schema was corrected to preserve a minimal audit snapshot without retaining content.
- `DataUseGrantServiceIntegrationTest`: 4/4 normal/simulator purpose separation, owner revocation,
  parented recompile versions and FORGET tombstones.
- `SchemaM16InitializerTest`: legacy H2 backfill is idempotent and links the primary grant.
- Capsule Genome, PersonaChat, matching, quota and forgetting focused regression: PASS.
- PostgreSQL 16 + pgvector: application smoke and exact schema/index/FK contract PASS; 16 Flyway
  migrations applied, V16 reached, second migrate applied 0.
- Campaign full Java 21 gate: 131 suites, 810 tests, 0 failures, 0 errors, 0 skipped (`140.4s`).
- YAML parse, current-tree secret scan and `git diff --check`: PASS.

## Honest remaining work

The same generalized grant authority is not yet wired to every product domain (psychology skills,
exports, analytics, mobile push, or administrative processing). Legal retention/subprocessor review
and real-provider consent copy remain human gates. G6 and governance therefore stay `IN_PROGRESS`.
