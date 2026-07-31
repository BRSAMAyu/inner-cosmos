# Security policy

Inner Cosmos handles private conversations, memories, relationship data, and AI-derived profile
claims. Treat security and privacy reports as sensitive.

## Reporting a vulnerability

Do not open a public issue containing an exploit, credential, private user data, or reproduction
artifact with sensitive content. Contact the repository owner privately or use GitHub private
vulnerability reporting when it is enabled. Include the affected commit, surface, impact, minimal
reproduction, and whether any real data or credential may have been exposed.

Never send real provider keys, AWS credentials, signing material, session cookies, database dumps,
or user conversations as part of a report. Replace them with synthetic values.

## Current security boundaries

- Production startup fails closed without HTTPS identity endpoints, secure cookies, CSRF, OIDC,
  PostgreSQL TLS verification, Redis TLS/credentials, distributed rate limiting, idempotency,
  scheduler leases, outbox delivery, and a real AI provider.
- Public responses use explicit projections; raw P0 conversations and Capsule prompts are not
  public API fields.
- AI-derived claims remain candidates until user confirmation and retain provenance/correction
  paths.
- Secrets are injected at runtime and scanned in both the current tree and reachable history.
- Demo, local, kind, Academy, and commercial profiles have different evidence boundaries; a weaker
  profile must never be presented as production proof.

See [`docs/goal/complete-product-acceptance.yml`](docs/goal/complete-product-acceptance.yml) for
open security, legal, operational, and human release gates.
