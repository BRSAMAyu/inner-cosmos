# Contributing to Inner Cosmos

## Before changing the product

Read [`CLAUDE.md`](CLAUDE.md), [`goal-objective.md`](goal-objective.md), and
[`对齐文档/README.md`](对齐文档/README.md). Inspect the live branch and working tree before editing;
historical state documents are evidence, not current authority.

## Change discipline

- Preserve unrelated local work and keep each change tied to a visible product or acceptance gap.
- Do not reduce privacy, safety, provenance, correction, idempotency, or production fail-closed
  behavior to make a demo or test pass.
- Keep credentials and user data outside the repository, logs, screenshots, fixtures, and prompts.
- Add a failure-recovery path for user-facing async interactions.
- Label evidence honestly as verified, partial, static-only, blocked, synthetic-only, or human-gated.

## Verification

```powershell
.\mvnw.cmd test

Push-Location web
corepack enable
pnpm install --frozen-lockfile
pnpm run api:check
pnpm run api:diff:test
pnpm test
pnpm run build
Pop-Location

.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
.\scripts\academy\validate-schema-version.ps1
.\scripts\academy\validate-manifests.ps1
```

PostgreSQL/Redis integration and production image gates require Docker. Real-provider, physical
device, live cloud, legal, and independent experience checks remain explicit human/external gates.

Use focused tests while iterating, then run the risk-proportional full gates before asking for
review. A green static build is not a substitute for browser, device, provider, or recovery evidence.
