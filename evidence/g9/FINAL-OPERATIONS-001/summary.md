# FINAL-OPERATIONS-001 — competition operations contract foundation

> Reconciled: 2026-08-01 (Asia/Shanghai)
>
> Acceptance transition: `UNASSESSED` → `IN_PROGRESS`
>
> Evidence class: `STATIC_AND_TESTED_CONTRACT`; not an independent operational rehearsal

## What is now machine-checkable

The repository has one reviewer-facing operational entry point covering the six required scenarios:

1. signed release;
2. rollback;
3. privacy-safe incident response;
4. no-fallback AI Provider failure and recovery;
5. data export/correction/withdrawal/account deletion;
6. backup and disaster recovery.

`docs/operations/operations-contract.yml` links those scenarios to 15 current repository artifacts and
37 exact source assertions. `web/scripts/check-operations-contract.mjs` fails when a required scenario,
runbook heading, owner, prerequisite, success gate, stop condition, recovery action, evidence field,
artifact, source assertion, remaining-work statement, or evidence boundary drifts. It also rejects
secret-shaped contract values and false global `PASS` claims.

The same check and its negative-path tests are wired into `.github/workflows/java-baseline.yml` before
the web build. This makes the runbook a maintained repository contract rather than an unverified prose
page.

## Local verification

Executed from `web/` on 2026-08-01:

```text
npm.cmd run operations:test
6 tests, 6 pass, 0 fail

npm.cmd run operations:check
Operations contract PASS: 6 scenarios, 15 artifacts, 37 source assertions; status=IN_PROGRESS

npm.cmd run acceptance:check
Acceptance ledger integrity PASS: 10 gates, 69 acceptance items, 5 human gates,
199 repository evidence paths
```

## Existing runtime evidence reused, not overstated

- Argo Rollouts has live kind evidence for a successful canary, automatic abort and stable-revision
  availability during a bad revision.
- PostgreSQL dump/restore has an in-cluster exercise, while restore from an encrypted off-cluster copy
  remains open.
- Data-rights ownership and erasure have automated controller/service evidence, while the disposable
  account operator rehearsal remains open.
- Provider failover has unit/configuration controls, while a production-faithful no-fallback UI failure
  and recovery exercise remains open.

## Remaining before `FINAL-OPERATIONS = PASS`

An independent operator must execute the documented release and rollback flow, a privacy-safe incident
tabletop plus controlled technical incident, a production-faithful Provider failure/recovery drill, a
disposable-account data-rights rehearsal, and a restore from an encrypted off-cluster backup. Evidence
must use the sanitized templates in `docs/operations/README.md`. Until all six scenario records exist,
both the operations contract and `FINAL-OPERATIONS` remain `IN_PROGRESS`.
