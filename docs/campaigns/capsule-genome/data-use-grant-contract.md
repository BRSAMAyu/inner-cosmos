# Versioned DataUseGrant Contract

Status: builder-verified for capsule and simulator memory use.

## Owner promise

- Selecting a memory for a normal capsule creates separate `CAPSULE_RUNTIME` and
  `PROVIDER_EGRESS` grants. Simulator selection creates `CAPSULE_SIMULATOR` and
  `PROVIDER_EGRESS`; it never creates a real-visitor runtime grant.
- Each grant names the owner snapshot, resource type/id/version, purpose, consumer type/id,
  grant version, parent grant, source, state, grant time, and optional revocation facts.
- Grants are persisted before derived memory text can be sent to an LLM provider.
- Replacing an authorization set revokes the prior generation and creates parented new versions.
  A memory version change cannot silently reuse a grant bound to an older resource version.
- The owner can inspect grant history and revoke a specific purpose through owner-isolated capsule
  APIs. Revocation immediately delists the capsule, withdraws its reference, and requires review.

## Forgetting and account lifecycle

- FORGET revokes active grants before deleting content-bearing authorization references and
  compiled reachability. Grant tombstones remain without excerpts or original memory content.
- `owner_user_id` is an audit snapshot, deliberately not a foreign key: an account record may be
  removed while a minimal, content-free proof of prior grant and revocation remains.
- The active reference links to its primary runtime/simulator grant; Provider egress is a distinct
  required grant. PersonaChat fails closed unless exactly one current-version active grant exists
  for every required purpose.

## Migration promise

- Fresh H2/MySQL-compatible schema creates the v1 grant model and link.
- `SchemaM16Initializer` idempotently upgrades long-lived non-Flyway H2 data and backfills two
  purpose grants for every legacy authorization reference.
- PostgreSQL Flyway V16 performs the same backfill and preserves withdrawn legacy rows as revoked
  tombstones. Fresh V1→V16 and second-run idempotency are part of the executable gate.

## Non-claims

This contract provides purpose/version/revocation authority for capsule memory use. It is not a
global consent center for every product domain yet, and legal-policy review of retention windows and
provider subprocessors remains a human gate.
